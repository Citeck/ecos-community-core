package ru.citeck.ecos.role;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.policy.ClassPolicyDelegate;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.TransactionalResourceHelper;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.repository.*;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.AuthorityType;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.namespace.RegexQNamePattern;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.extensions.surf.util.ParameterCheck;
import ru.citeck.ecos.role.CaseRolePolicies.OnRoleAssigneesChangedPolicy;
import ru.citeck.ecos.role.CaseRolePolicies.OnCaseRolesAssigneesChangedPolicy;
import ru.citeck.ecos.model.ICaseRoleModel;
import ru.citeck.ecos.role.dao.RoleDAO;
import ru.citeck.ecos.utils.DictionaryUtils;
import ru.citeck.ecos.utils.RepoUtils;

import java.util.*;

/**
 * @author Maxim Strizhov
 * @author Pavel Simonov
 */
public class CaseRoleServiceImpl implements CaseRoleService {

    private static final String ROLE_TO_NOT_FIRE_TXN_KEY = CaseRoleServiceImpl.class.getName() + ".roleToNotFire";

    private static final int ASSIGNEE_DELEGATION_DEPTH_LIMIT = 100;

    private static final Logger logger = LoggerFactory.getLogger(CaseRoleServiceImpl.class);

    private NodeService nodeService;
    private PolicyComponent policyComponent;
    private AuthorityService authorityService;
    private DictionaryService dictionaryService;

    private Map<QName, RoleDAO> rolesDAOByType = new HashMap<>();

    private ClassPolicyDelegate<OnRoleAssigneesChangedPolicy> onRoleAssigneesChangedDelegate;
    private ClassPolicyDelegate<OnCaseRolesAssigneesChangedPolicy> onCaseRolesAssigneesChangedDelegate;

    public void init() {
        onRoleAssigneesChangedDelegate = policyComponent.registerClassPolicy(OnRoleAssigneesChangedPolicy.class);
        onCaseRolesAssigneesChangedDelegate = policyComponent.registerClassPolicy(OnCaseRolesAssigneesChangedPolicy.class);
    }

    @Override
    public List<NodeRef> getRoles(NodeRef caseRef) {
        if (caseRef == null || !nodeService.exists(caseRef)) {
            return Collections.emptyList();
        }
        List<ChildAssociationRef> assocs = nodeService.getChildAssocs(caseRef, ICaseRoleModel.ASSOC_ROLES,
                                                                               RegexQNamePattern.MATCH_ALL);
        List<NodeRef> result = new ArrayList<>(assocs.size());
        for (ChildAssociationRef assoc : assocs) {
            result.add(assoc.getChildRef());
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public NodeRef getRole(NodeRef caseRef, String name) {
        ParameterCheck.mandatoryString("name", name);

        List<NodeRef> roles = getRoles(caseRef);
        for (NodeRef roleRef : roles) {
            String varName = (String) nodeService.getProperty(roleRef, ICaseRoleModel.PROP_VARNAME);
            if (name.equals(varName)) {
                return roleRef;
            }
        }
        return null;
    }

    @Override
    public List<String> getUserRoles(NodeRef caseRef, String userName) {
        ParameterCheck.mandatoryString("userName", userName);

        List<String> userRoleRefs = new ArrayList<>();
        List<NodeRef> roles = getRoles(caseRef);
        for (NodeRef roleRef : roles) {
            List<NodeRef> assigneesRefs = RepoUtils.getTargetNodeRefs(roleRef, ICaseRoleModel.ASSOC_ASSIGNEES,
                    nodeService);
            if (assigneesRefs != null) {
                for (NodeRef assigneesRef : assigneesRefs) {
                    String authorityName = RepoUtils.getAuthorityName(assigneesRef, nodeService, dictionaryService);
                    QName type = nodeService.getType(assigneesRef);
                    if (dictionaryService.isSubClass(type, ContentModel.TYPE_AUTHORITY_CONTAINER)) {
                        Set<String> groupAuthorities = authorityService.getContainedAuthorities(
                                AuthorityType.USER, authorityName, false);
                        for (String groupAuthority : groupAuthorities) {
                            if (userName.equals(groupAuthority)) {
                                String userRoleId = (String) nodeService.getProperty(roleRef, ICaseRoleModel.PROP_VARNAME);
                                userRoleRefs.add(userRoleId);
                            }
                        }
                    } else if (authorityName.equals(userName)) {
                        String userRoleId = (String) nodeService.getProperty(roleRef, ICaseRoleModel.PROP_VARNAME);
                        userRoleRefs.add(userRoleId);
                    }
                }
            }
        }
        if (logger.isDebugEnabled()) {
            logger.debug("User roles: " + userRoleRefs);
        }
        return userRoleRefs;
    }

    @Override
    public void setAssignees(NodeRef caseRef, String roleName, Collection<NodeRef> assignees) {
        setAssignees(needRole(caseRef, roleName), assignees);
    }

    @Override
    public void setAssignees(NodeRef roleRef, Collection<NodeRef> assignees) {
        if (assignees == null || assignees.isEmpty()) {
            removeAssignees(roleRef);
            return;
        }
        Set<NodeRef> existing = getAssignees(roleRef);
        Set<NodeRef> added = subtract(assignees, existing);
        Set<NodeRef> removed = subtract(existing, assignees);

        addToTransactionMap(roleRef, added);
        for (NodeRef assignee : added) {
            nodeService.createAssociation(roleRef, assignee, ICaseRoleModel.ASSOC_ASSIGNEES);
        }
        clearTransactionMap();

        addToTransactionMap(roleRef, removed);
        for (NodeRef assignee : removed) {
            nodeService.removeAssociation(roleRef, assignee, ICaseRoleModel.ASSOC_ASSIGNEES);
        }
        clearTransactionMap();

        fireAssigneesChangedEvent(roleRef, added, removed);
    }

    @Override
    public void addAssignees(NodeRef caseRef, String roleName, NodeRef... assignees) {
        addAssignees(needRole(caseRef, roleName), assignees);
    }

    @Override
    public void addAssignees(NodeRef roleRef, NodeRef... assignees) {
        addAssignees(roleRef, Arrays.asList(assignees));
    }

    @Override
    public void addAssignees(NodeRef caseRef, String roleName, Collection<NodeRef> assignees) {
        addAssignees(needRole(caseRef, roleName), assignees);
    }

    @Override
    public void addAssignees(NodeRef roleRef, Collection<NodeRef> assignees) {
        if (assignees == null || assignees.isEmpty()) {
            return;
        }
        Set<NodeRef> existing = getAssignees(roleRef);
        Set<NodeRef> added = subtract(assignees, existing);
        addToTransactionMap(roleRef, added);
        for (NodeRef assignee : added) {
            nodeService.createAssociation(roleRef, assignee, ICaseRoleModel.ASSOC_ASSIGNEES);
        }
        clearTransactionMap();
        fireAssigneesChangedEvent(roleRef, added, null);
    }

    @Override
    public Set<NodeRef> getAssignees(NodeRef caseRef, String roleName) {
        return getAssignees(needRole(caseRef, roleName));
    }

    @Override
    public Set<NodeRef> getAssignees(NodeRef roleRef) {
        if (roleRef != null) {
            return getTargets(roleRef, ICaseRoleModel.ASSOC_ASSIGNEES);
        }
        return new HashSet<>();
    }

    @Override
    public void removeAssignees(NodeRef caseRef, String roleName) {
        removeAssignees(needRole(caseRef, roleName));
    }

    @Override
    public void removeAssignees(NodeRef roleRef) {
        if (roleRef != null) {
            Set<NodeRef> assignees = getTargets(roleRef, ICaseRoleModel.ASSOC_ASSIGNEES);
            addToTransactionMap(roleRef, assignees);
            for (NodeRef ref : assignees) {
                nodeService.removeAssociation(roleRef, ref, ICaseRoleModel.ASSOC_ASSIGNEES);
            }
            clearTransactionMap();
            fireAssigneesChangedEvent(roleRef, null, assignees);
        }
    }

    @Override
    public boolean isRoleMember(NodeRef caseRef, String roleName, NodeRef authorityRef) {
        return isRoleMember(needRole(caseRef, roleName), authorityRef);
    }

    @Override
    public boolean isRoleMember(NodeRef caseRef, String roleName, NodeRef authorityRef, boolean immediate) {
        return isRoleMember(needRole(caseRef, roleName), authorityRef, immediate);
    }

    @Override
    public boolean isRoleMember(NodeRef roleRef, NodeRef authorityRef) {
        return isRoleMember(roleRef, authorityRef, false);
    }

    @Override
    public boolean isRoleMember(NodeRef roleRef, NodeRef authorityRef, boolean immediate) {

        Set<NodeRef> assignees = getAssignees(roleRef);

        if (assignees.contains(authorityRef)) {
            return true;
        } else if (immediate) {
            return false;
        }

        String authorityName = RepoUtils.getAuthorityName(authorityRef, nodeService, dictionaryService);
        AuthorityType authorityType = AuthorityType.getAuthorityType(authorityName);

        for (NodeRef assigneeRef : assignees) {
            String assigneeName = RepoUtils.getAuthorityName(assigneeRef, nodeService, dictionaryService);
            Set<String> authorities = authorityService.getContainedAuthorities(authorityType, assigneeName, false);
            if (authorities.contains(authorityName)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void updateRoles(final NodeRef caseRef) {
        AuthenticationUtil.runAsSystem(() -> {
            Collection<NodeRef> roles = getRoles(caseRef);
            for (NodeRef roleRef : roles) {
                updateRoleImpl(caseRef, roleRef);
            }
            return null;
        });
    }

    @Override
    public void updateRole(NodeRef caseRef, String roleName) {
        updateRole(needRole(caseRef, roleName));
    }

    @Override
    public void updateRole(final NodeRef roleRef) {
        AuthenticationUtil.runAsSystem(() -> {
            NodeRef caseRef = nodeService.getPrimaryParent(roleRef).getParentRef();
            updateRoleImpl(caseRef, roleRef);
            return null;
        });
    }

    @Override
    public void roleChanged(NodeRef roleRef, NodeRef added, NodeRef removed) {
        AuthenticationUtil.runAsSystem(() -> {

            if (added != null && transactionMapContains(roleRef, added)) {
                return null;
            }

            if (removed != null && transactionMapContains(roleRef, removed)) {
                return null;
            }

            Set<NodeRef> addedSet = added != null ? new HashSet<>(Arrays.asList(added)) : null;
            Set<NodeRef> removedSet = removed != null ? new HashSet<>(Arrays.asList(removed)) : null;
            fireAssigneesChangedEvent(roleRef, addedSet, removedSet);
            return null;
        });
    }

    @Autowired
    public void register(List<RoleDAO> rolesDAO) {
        for (RoleDAO dao : rolesDAO) {
            rolesDAOByType.put(dao.getRoleType(), dao);
        }
    }

    @Override
    public void setDelegate(NodeRef roleRef, NodeRef assignee, NodeRef delegate) {
        setDelegates(roleRef, Collections.singletonMap(assignee, delegate));
    }

    @Override
    public void setDelegates(NodeRef roleRef, Map<NodeRef, NodeRef> delegates) {

        Map<NodeRef, NodeRef> actualDelegates = new HashMap<>(getDelegates(roleRef));
        boolean wasChanged = false;

        for (Map.Entry<NodeRef, NodeRef> entry : delegates.entrySet()) {
            NodeRef assignee = entry.getKey();
            NodeRef delegate = entry.getValue();

            if (Objects.equals(assignee, delegate)) {
                continue;
            }
            NodeRef actualDelegate = actualDelegates.get(assignee);
            if (Objects.equals(delegate, actualDelegate)) {
                continue;
            }

            actualDelegates.remove(assignee);
            NodeRef delegateIter = delegate;
            while (delegateIter != null && !delegateIter.equals(assignee)) {
                delegateIter = actualDelegates.get(delegateIter);
            }
            if (delegateIter != null) {
                delegateIter = delegate;
                while (delegateIter != null) {
                    delegateIter = actualDelegates.remove(delegateIter);
                }
            } else {
                actualDelegates.put(assignee, delegate);
            }

            wasChanged = true;
        }

        if (wasChanged) {
            persistDelegates(roleRef, actualDelegates);
        }
    }

    @Override
    public void removeDelegate(NodeRef roleRef, NodeRef assignee) {
        Map<NodeRef, NodeRef> delegates = getDelegates(roleRef);
        delegates.remove(assignee);
        persistDelegates(roleRef, delegates);
        updateRole(roleRef);
    }

    @Override
    public void removeDelegates(NodeRef roleRef) {
        persistDelegates(roleRef, Collections.<NodeRef, NodeRef>emptyMap());
        updateRole(roleRef);
    }

    @Override
    public Map<NodeRef, NodeRef> getDelegates(NodeRef roleRef) {
        String delegatesStr = (String) nodeService.getProperty(roleRef, ICaseRoleModel.PROP_DELEGATES);
        Map<NodeRef, NodeRef> delegates = new HashMap<>();
        if (delegatesStr != null) {
            boolean dirtyProperty = false;
            try {
                JSONObject jsonObject = new JSONObject(delegatesStr);
                Iterator it = jsonObject.keys();
                while (it.hasNext()) {
                    String key = (String) it.next();
                    String value = (String) jsonObject.get(key);
                    try {
                        delegates.put(new NodeRef(key), new NodeRef(value));
                    } catch (MalformedNodeRefException e) {
                        dirtyProperty = true;
                    }
                }
            } catch (Exception e) {
                dirtyProperty = true;
            }
            if (dirtyProperty) {
                persistDelegates(roleRef, delegates);
            }
        }
        return delegates;
    }

    private void persistDelegates(NodeRef roleRef, Map<NodeRef, NodeRef> delegates) {
        JSONObject jsonObject = new JSONObject();

        for (Map.Entry<NodeRef, NodeRef> entry : delegates.entrySet()) {
            if (nodeService.exists(entry.getKey()) && nodeService.exists(entry.getValue())) {
                try {
                    jsonObject.putOpt(entry.getKey().toString(), entry.getValue().toString());
                } catch (JSONException e) {
                    //do nothing
                }
            }
        }
        nodeService.setProperty(roleRef, ICaseRoleModel.PROP_DELEGATES, jsonObject.toString());
    }

    private void updateRoleImpl(NodeRef caseRef, NodeRef roleRef) {
        QName type = nodeService.getType(roleRef);
        RoleDAO dao = rolesDAOByType.get(type);
        if (dao != null) {
            Set<NodeRef> assignees = dao.getAssignees(caseRef, roleRef);
            if (assignees != null) {
                setAssignees(roleRef, getDelegates(roleRef, assignees));
            }
        }
    }

    private Set<NodeRef> getDelegates(NodeRef roleRef, Set<NodeRef> assignees) {
        Map<NodeRef, NodeRef> delegation = getDelegates(roleRef);
        if (delegation.isEmpty()) {
            return assignees;
        }
        Set<NodeRef> delegates = new HashSet<>();
        for (NodeRef assigneeRef : assignees) {
            NodeRef delegateRef = assigneeRef;
            NodeRef next = delegation.get(assigneeRef);
            int idx = 0;
            for (; idx < ASSIGNEE_DELEGATION_DEPTH_LIMIT; idx++) {
                if (next == null) break;
                delegateRef = next;
                next = delegation.get(next);
            }
            if (idx == ASSIGNEE_DELEGATION_DEPTH_LIMIT) {
                logger.error("ROLE ASSIGNEE DELEGATION ERROR! " +
                             "Role assignees delegates is looped. " +
                             "RoleRef: " + roleRef + " AssigneeRef: " + assigneeRef);
            }
            if (nodeService.exists(delegateRef)) {
                delegates.add(delegateRef);
            } else {
                delegates.add(assigneeRef);
            }
        }
        return delegates;
    }

    private void fireAssigneesChangedEvent(NodeRef roleRef, Set<NodeRef> added, Set<NodeRef> removed) {
        if (added == null) {
            added = Collections.emptySet();
        }
        if (removed == null) {
            removed = Collections.emptySet();
        }
        if (added.isEmpty() && removed.isEmpty()) {
            return;
        }
        Set<QName> classes;

        classes = new HashSet<>(DictionaryUtils.getNodeClassNames(roleRef, nodeService));
        OnRoleAssigneesChangedPolicy changedPolicy = onRoleAssigneesChangedDelegate.get(roleRef, classes);
        changedPolicy.onRoleAssigneesChanged(roleRef, added, removed);

        NodeRef caseRef = nodeService.getPrimaryParent(roleRef).getParentRef();
        classes = new HashSet<>(DictionaryUtils.getNodeClassNames(caseRef, nodeService));
        OnCaseRolesAssigneesChangedPolicy rolesChangedPolicy = onCaseRolesAssigneesChangedDelegate.get(caseRef, classes);
        rolesChangedPolicy.onCaseRolesAssigneesChanged(caseRef);
    }

    private Set<NodeRef> getTargets(NodeRef nodeRef, QName assocType) {
        List<AssociationRef> assocs = nodeService.getTargetAssocs(nodeRef, assocType);
        Set<NodeRef> result = new HashSet<>();
        for (AssociationRef ref : assocs) {
            result.add(ref.getTargetRef());
        }
        return result;
    }

    private Set<NodeRef> subtract(Collection<NodeRef> from, Collection<NodeRef> values) {
        if (from == null || from.isEmpty()) {
            return Collections.emptySet();
        }
        Set<NodeRef> result = new HashSet<>(from);
        if (values != null) {
            result.removeAll(values);
        }
        return Collections.unmodifiableSet(result);
    }

    private NodeRef needRole(NodeRef caseRef, String name) {
        NodeRef roleRef = getRole(caseRef, name);
        if (roleRef == null) {
            throw new IllegalArgumentException("Role with name '" + name + "' not found in case " + caseRef);
        }
        return roleRef;
    }

    private boolean transactionMapContains(NodeRef roleRef, NodeRef authorityRef) {
        Map<NodeRef, Collection<NodeRef>> transactionMap = TransactionalResourceHelper.
                <NodeRef, Collection<NodeRef>>getMap(ROLE_TO_NOT_FIRE_TXN_KEY);
        Collection<NodeRef> authorities = transactionMap.get(roleRef);
        if (authorities == null) {
            return false;
        }
        return authorities.contains(authorityRef);
    }

    private void addToTransactionMap(NodeRef roleRef, NodeRef authority) {
        addToTransactionMap(roleRef, Collections.singleton(authority));
    }

    private void addToTransactionMap(NodeRef roleRef, Collection<NodeRef> newAuthorities) {
        Map<NodeRef, Collection<NodeRef>> transactionMap = TransactionalResourceHelper.getMap(ROLE_TO_NOT_FIRE_TXN_KEY);
        Collection<NodeRef> authorities = transactionMap.get(roleRef);
        if (authorities != null) {
            authorities.addAll(newAuthorities);
        } else {
            transactionMap.put(roleRef, new HashSet<>(newAuthorities));
        }
    }

    private void clearTransactionMap() {
        Map<NodeRef, Collection<NodeRef>> transactionMap = TransactionalResourceHelper.getMap(ROLE_TO_NOT_FIRE_TXN_KEY);
        transactionMap.clear();
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setPolicyComponent(PolicyComponent policyComponent) {
        this.policyComponent = policyComponent;
    }

    public void setAuthorityService(AuthorityService authorityService) {
        this.authorityService = authorityService;
    }

    public void setDictionaryService(DictionaryService dictionaryService) {
        this.dictionaryService = dictionaryService;
    }
}
