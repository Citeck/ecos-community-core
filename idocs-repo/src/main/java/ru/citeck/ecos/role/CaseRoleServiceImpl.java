package ru.citeck.ecos.role;

import ecos.com.google.common.cache.CacheBuilder;
import ecos.com.google.common.cache.CacheLoader;
import ecos.com.google.common.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.policy.ClassPolicyDelegate;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.TransactionalResourceHelper;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.repository.*;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.AuthorityType;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.namespace.RegexQNamePattern;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.extensions.surf.util.I18NUtil;
import org.springframework.extensions.surf.util.ParameterCheck;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.model.lib.role.dto.RoleDef;
import ru.citeck.ecos.model.lib.role.service.RoleService;
import ru.citeck.ecos.model.lib.type.service.TypeDefService;
import ru.citeck.ecos.node.EcosTypeService;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.role.CaseRolePolicies.OnRoleAssigneesChangedPolicy;
import ru.citeck.ecos.role.CaseRolePolicies.OnCaseRolesAssigneesChangedPolicy;
import ru.citeck.ecos.model.ICaseRoleModel;
import ru.citeck.ecos.role.dao.RoleDAO;
import ru.citeck.ecos.utils.AuthorityUtils;
import ru.citeck.ecos.utils.DictionaryUtils;
import ru.citeck.ecos.utils.RepoUtils;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author Maxim Strizhov
 * @author Pavel Simonov
 */
@Slf4j
public class CaseRoleServiceImpl implements CaseRoleService {

    private static final String ROLE_TO_NOT_FIRE_TXN_KEY = CaseRoleServiceImpl.class.getName() + ".roleToNotFire";

    private static final int ASSIGNEE_DELEGATION_DEPTH_LIMIT = 100;

    private NodeService nodeService;

    private TypeDefService typeDefService;
    private EcosTypeService ecosTypeService;

    private PolicyComponent policyComponent;
    private AuthorityService authorityService;
    private DictionaryService dictionaryService;
    private RoleService roleService;
    private AuthorityUtils authorityUtils;

    private final Map<QName, RoleDAO> rolesDaoByType = new HashMap<>();

    private ClassPolicyDelegate<OnRoleAssigneesChangedPolicy> onRoleAssigneesChangedDelegate;
    private ClassPolicyDelegate<OnCaseRolesAssigneesChangedPolicy> onCaseRolesAssigneesChangedDelegate;

    private LoadingCache<NodeRef, NodeRef> normalizeRoleCache;

    public void init() {
        onRoleAssigneesChangedDelegate = policyComponent.registerClassPolicy(OnRoleAssigneesChangedPolicy.class);
        onCaseRolesAssigneesChangedDelegate = policyComponent.registerClassPolicy(OnCaseRolesAssigneesChangedPolicy.class);

        normalizeRoleCache = CacheBuilder.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .build(CacheLoader.from(this::normalizeRoleRefImpl));
    }

    private NodeRef ecosRoleToNodeRef(NodeRef parentRef, String roleId) {
        return new NodeRef(CaseRoleService.ROLE_REF_PROTOCOL, parentRef.getId(), roleId);
    }

    @Override
    public boolean isAlfRole(NodeRef nodeRef) {
        return nodeRef == null || StoreRef.STORE_REF_WORKSPACE_SPACESSTORE.equals(nodeRef.getStoreRef());
    }

    @Override
    public List<RoleDef> getRolesDef(NodeRef caseRef) {
        return getRoles(caseRef)
            .stream()
            .map(this::getRoleDef)
            .collect(Collectors.toList());
    }

    @Override
    public List<NodeRef> getRoles(NodeRef caseRef) {

        if (caseRef == null || !nodeService.exists(caseRef)) {
            return Collections.emptyList();
        }

        List<ChildAssociationRef> assocs = nodeService.getChildAssocs(caseRef, ICaseRoleModel.ASSOC_ROLES,
                                                                               RegexQNamePattern.MATCH_ALL);
        Map<String, NodeRef> result = new HashMap<>();
        for (ChildAssociationRef assoc : assocs) {
            result.put(getRoleId(assoc.getChildRef()), assoc.getChildRef());
        }
        result.putAll(getEcosTypeRolesForCase(caseRef));

        return Collections.unmodifiableList(new ArrayList<>(result.values()));
    }

    private Map<String, NodeRef> getEcosTypeRolesForCase(NodeRef caseRef) {

        Map<String, NodeRef> result = new HashMap<>();

        RecordRef ecosType = ecosTypeService.getEcosType(caseRef);
        typeDefService.forEachAsc(ecosType, typeDef -> {
            typeDef.getModel()
                .getRoles()
                .forEach(role -> result.put(role.getId(), ecosRoleToNodeRef(caseRef, role.getId())));
            return false;
        });

        return result;
    }

    @Nullable
    @Override
    public NodeRef getRole(NodeRef caseRef, String name) {

        ParameterCheck.mandatoryString("name", name);

        List<NodeRef> roles = getRoles(caseRef);
        for (NodeRef roleRef : roles) {
            if (name.equals(getRoleId(roleRef))) {
                return roleRef;
            }
        }

        return null;
    }

    @NotNull
    @Override
    public String getRoleId(NodeRef roleRef) {
        String varName;
        if (isAlfRole(roleRef)) {
            varName = (String) nodeService.getProperty(roleRef, ICaseRoleModel.PROP_VARNAME);
        } else {
            varName = roleRef.getId();
        }
        return varName == null ? "" : varName;
    }

    @NotNull
    @Override
    public String getRoleDispName(NodeRef roleRef) {

        String res;
        if (!isAlfRole(roleRef)) {
            RoleDef roleDef = getRoleDef(roleRef);
            return MLText.getClosestValue(roleDef.getName(), I18NUtil.getLocale());
        }

        if (!nodeService.exists(roleRef)) {
            return "";
        }

        Object objRes = nodeService.getProperty(roleRef, ContentModel.PROP_TITLE);
        if (objRes instanceof org.alfresco.service.cmr.repository.MLText) {
            objRes = ((org.alfresco.service.cmr.repository.MLText) objRes).getClosestValue(I18NUtil.getLocale());
        }
        res = objRes != null ? objRes.toString() : null;
        if (StringUtils.isBlank(res)) {
            res = (String) nodeService.getProperty(roleRef, ContentModel.PROP_NAME);
        }
        if (StringUtils.isBlank(res)) {
            res = getRoleId(roleRef);
        }
        if (StringUtils.isBlank(res)) {
            res = roleRef.toString();
        }
        return res;
    }

    @Override
    public RoleDef getRoleDef(NodeRef roleRef) {

        if (isAlfRole(roleRef)) {
            Map<QName, Serializable> props = nodeService.getProperties(roleRef);
            return RoleDef.create()
                .withId((String) props.get(ICaseRoleModel.PROP_VARNAME))
                .withName(new MLText((String) props.get(ContentModel.PROP_TITLE)))
                .build();
        }

        NodeRef caseRef = new NodeRef("workspace://SpacesStore/" + roleRef.getStoreRef().getIdentifier());
        RecordRef ecosType = ecosTypeService.getEcosType(caseRef);

        return roleService.getRoleDef(ecosType, roleRef.getId());
    }

    @NotNull
    @Override
    public NodeRef getRoleCaseRef(NodeRef roleRef) {
        if (isAlfRole(roleRef)) {
            return nodeService.getPrimaryParent(roleRef).getParentRef();
        }
        return new NodeRef("workspace://SpacesStore/" + roleRef.getStoreRef().getIdentifier());
    }

    @Override
    public List<String> getUserRoles(NodeRef caseRef, String userName) {

        ParameterCheck.mandatoryString("userName", userName);

        List<String> userRoleIds = new ArrayList<>();
        List<NodeRef> roles = getRoles(caseRef);

        Set<NodeRef> userAuthorities = authorityUtils.getUserAuthoritiesRefs();

        for (NodeRef roleRef : roles) {
            Set<NodeRef> roleAssignees = getAssignees(roleRef);
            if (userAuthorities.stream().anyMatch(roleAssignees::contains)) {
                String roleId = getRoleId(roleRef);
                if (roleId.isEmpty()) {
                    userRoleIds.add(roleId);
                }
                break;
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("User roles: " + userRoleIds);
        }
        return userRoleIds;
    }

    @Override
    public void setAssignees(NodeRef caseRef, String roleName, Collection<NodeRef> assignees) {
        setAssignees(needRole(caseRef, roleName), assignees);
    }

    @Override
    public void setAssignees(NodeRef roleRef, Collection<NodeRef> assignees) {

        roleRef = normalizeRoleRef(roleRef);

        if (!isAlfRole(roleRef)) {
            return;
        }

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

        roleRef = normalizeRoleRef(roleRef);

        if (!isAlfRole(roleRef)) {
            return;
        }
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

    private NodeRef normalizeRoleRef(NodeRef roleRef) {
        if (roleRef == null || !isAlfRole(roleRef)) {
            return roleRef;
        }
        return normalizeRoleCache.getUnchecked(roleRef);
    }

    private NodeRef normalizeRoleRefImpl(NodeRef roleRef) {

        String roleId = getRoleId(roleRef);
        NodeRef caseRef = getRoleCaseRef(roleRef);
        Map<String, NodeRef> etypeRoles = getEcosTypeRolesForCase(caseRef);

        return etypeRoles.getOrDefault(roleId, roleRef);
    }

    @Override
    public Set<NodeRef> getAssignees(NodeRef roleRef) {

        roleRef = normalizeRoleRef(roleRef);

        if (roleRef == null) {
            return new HashSet<>();
        }

        if (isAlfRole(roleRef)) {
            return getTargets(roleRef, ICaseRoleModel.ASSOC_ASSIGNEES);
        }

        RecordRef caseRef = RecordRef.valueOf(String.valueOf(getRoleCaseRef(roleRef)));

        return roleService.getAssignees(caseRef, roleRef.getId())
            .stream()
            .map(authorityUtils::getNodeRef)
            .collect(Collectors.toSet());
    }

    @Override
    public void removeAssignees(NodeRef caseRef, String roleName) {
        removeAssignees(needRole(caseRef, roleName));
    }

    @Override
    public void removeAssignees(NodeRef roleRef) {

        roleRef = normalizeRoleRef(roleRef);

        if (!isAlfRole(roleRef)) {
            return;
        }
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
        if (!isAlfRole(roleRef)) {
            return;
        }
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

            Set<NodeRef> addedSet = added != null ? new HashSet<>(Collections.singletonList(added)) : null;
            Set<NodeRef> removedSet = removed != null ? new HashSet<>(Collections.singletonList(removed)) : null;
            fireAssigneesChangedEvent(roleRef, addedSet, removedSet);

            return null;
        });
    }

    @Autowired
    public void register(List<RoleDAO> rolesDAO) {
        for (RoleDAO dao : rolesDAO) {
            rolesDaoByType.put(dao.getRoleType(), dao);
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
        persistDelegates(roleRef, Collections.emptyMap());
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
                Iterator<?> it = jsonObject.keys();
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

        roleRef = normalizeRoleRef(roleRef);

        if (!isAlfRole(roleRef)) {
            return;
        }

        QName type = nodeService.getType(roleRef);
        RoleDAO dao = rolesDaoByType.get(type);
        if (dao != null) {
            Set<NodeRef> assignees = dao.getAssignees(caseRef, roleRef);
            if (assignees != null) {
                setAssignees(roleRef, getDelegates(roleRef, assignees));
            }
        }
    }

    private Set<NodeRef> getDelegates(NodeRef roleRef, Set<NodeRef> assignees) {
        if (!isAlfRole(roleRef)) {
            // todo
            return assignees;
        }
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
                log.error("ROLE ASSIGNEE DELEGATION ERROR! " +
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
        if (!isAlfRole(nodeRef)) {
            return new HashSet<>();
        }
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

    @Autowired
    public void setTypeDefService(TypeDefService typeDefService) {
        this.typeDefService = typeDefService;
    }

    @Autowired
    public void setEcosTypeService(EcosTypeService ecosTypeService) {
        this.ecosTypeService = ecosTypeService;
    }

    @Autowired
    public void setRoleService(RoleService roleService) {
        this.roleService = roleService;
    }

    @Autowired
    public void setAuthorityUtils(AuthorityUtils authorityUtils) {
        this.authorityUtils = authorityUtils;
    }
}
