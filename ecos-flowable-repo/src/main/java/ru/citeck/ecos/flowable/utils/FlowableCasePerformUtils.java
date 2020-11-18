package ru.citeck.ecos.flowable.utils;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.model.Repository;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.TransactionalResourceHelper;
import org.alfresco.repo.workflow.WorkflowModel;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.repository.AssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.AuthorityType;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.flowable.variable.api.delegate.VariableScope;
import org.springframework.beans.factory.annotation.Autowired;
import ru.citeck.ecos.flowable.activiti.delegates.FlowableVariableScopeDelegate;
import ru.citeck.ecos.model.CasePerformModel;
import ru.citeck.ecos.model.ICaseTaskModel;
import ru.citeck.ecos.role.CaseRoleAssocsDao;
import ru.citeck.ecos.role.CaseRoleService;
import ru.citeck.ecos.utils.RepoUtils;
import ru.citeck.ecos.workflow.perform.CasePerformUtils;

import java.io.Serializable;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Flowable case perform utils
 */
public class FlowableCasePerformUtils {

    /**
     * Constants
     */
    public static final String TASK_CONFIGS = "taskConfigs";
    public static final String TASK_CONF_ASSIGNEE = "assignee";
    public static final String TASK_CONF_CANDIDATE_USERS = "candidateUsers";
    public static final String TASK_CONF_CANDIDATE_GROUPS = "candidateGroups";
    public static final String TASK_CONF_FORM_KEY = "formKey";
    public static final String TASK_CONF_DUE_DATE = "dueDate";
    public static final String TASK_CONF_PRIORITY = "priority";
    public static final String TASK_CONF_CATEGORY = "category";

    public static final String WORKFLOW_VERSION_KEY = "WorkflowVersion";
    public static final String PROC_DEFINITION_NAME = "case-perform";
    public static final String SUB_PROCESS_NAME = "perform-sub-process";

    public static final String DEFAULT_DELIMITER = ",";

    public static final String OPTIONAL_PERFORMERS = "optionalPerformers";
    public static final String EXCLUDED_PERFORMERS = "excludedPerformers";
    public static final String MANDATORY_TASKS = "mandatoryTasks";
    public static final String ABORT_PERFORMING = "abortPerforming";
    public static final String SKIP_PERFORMING = "skipPerforming";
    public static final String PERFORMERS = "performers";
    public static final String PERFORMERS_ROLES_POOL = "performersRolesPool";
    public static final String REASSIGNMENT_KEY = "case-perform-reassignment";

    /**
     * Key comparator
     */
    private static final FlowableCasePerformUtils.DummyComparator KEYS_COMPARATOR = new FlowableCasePerformUtils.DummyComparator();

    /**
     * Variables
     */
    private static final List<String> VARIABLES_SHARING_IGNORED_PREFIXES = Arrays.asList("bpm", "cwf", "wfcf", "cm");
    private static final Pattern VARIABLES_PATTERN = Pattern.compile("^([^_]+)_(.+)");

    /**
     * Services
     */
    private NodeService nodeService;
    private NamespaceService namespaceService;
    private AuthorityService authorityService;
    private DictionaryService dictionaryService;
    private Repository repositoryHelper;
    private CaseRoleService caseRoleService;
    private CaseRoleAssocsDao caseRoleAssocsDao;

    /**
     * Check - is comment mandatory
     * @param execution Execution
     * @param task Task
     * @return Check result
     */
    public boolean isCommentMandatory(ExecutionEntity execution, TaskEntity task) {
        return isInSplitString(execution, CasePerformModel.PROP_OUTCOMES_WITH_MANDATORY_COMMENT,
                task, CasePerformModel.PROP_PERFORM_OUTCOME);
    }

    /**
     * Check - is abort outcome received
     * @param execution Execution
     * @param task Task
     * @return Check result
     */
    public boolean isAbortOutcomeReceived(ExecutionEntity execution, TaskEntity task) {
        return isInSplitString(execution, CasePerformModel.PROP_ABORT_OUTCOMES,
                task, CasePerformModel.PROP_PERFORM_OUTCOME);
    }


    /**
     * Save task result
     * @param execution Execution
     * @param task Task
     */
    public void saveTaskResult(ExecutionEntity execution, TaskEntity task) {

        String outcome = (String) task.getVariableLocal(toString(CasePerformModel.PROP_PERFORM_OUTCOME));
        if (outcome == null) return;
        String comment = (String) task.getVariableLocal(toString(WorkflowModel.PROP_COMMENT));

        NodeRef person = repositoryHelper.getPerson();
        String userName = (String) nodeService.getProperty(person, ContentModel.PROP_USERNAME);
        String resultName = "perform-result-" + userName + "-" + outcome;

        Map<QName, Serializable> properties = new HashMap<>();
        properties.put(CasePerformModel.PROP_RESULT_OUTCOME, outcome);
        properties.put(CasePerformModel.PROP_RESULT_DATE, new Date());
        properties.put(CasePerformModel.PROP_COMMENT, comment);
        properties.put(ContentModel.PROP_NAME, resultName);

        NodeRef bpmPackage = (NodeRef) execution.getVariable("bpm_package");

        QName assocQName = QName.createQName(CasePerformModel.NAMESPACE, resultName);
        NodeRef result = nodeService.createNode(bpmPackage, CasePerformModel.ASSOC_PERFORM_RESULTS,
                assocQName, CasePerformModel.TYPE_PERFORM_RESULT,
                properties).getChildRef();

        nodeService.createAssociation(result, person, CasePerformModel.ASSOC_RESULT_PERSON);

        NodeRef performer = (NodeRef) task.getVariable(toString(CasePerformModel.ASSOC_PERFORMER));
        if (performer != null) {
            nodeService.createAssociation(result, performer, CasePerformModel.ASSOC_RESULT_PERFORMER);
        }
    }

    /**
     * Share variables
     * @param from From execution instance
     * @param to To execution instance
     */
    public void shareVariables(VariableScope from, VariableScope to) {
        Map<String, Object> variables = from.getVariables();
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            if (isSharedVariable(entry.getKey())) {
                to.setVariable(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Check - is variable shared
     * @param name Variable name
     * @return Check result
     */
    boolean isSharedVariable(String name) {
        Matcher matcher = VARIABLES_PATTERN.matcher(name);
        return matcher.matches() && !VARIABLES_SHARING_IGNORED_PREFIXES.contains(matcher.group(1));
    }

    /**
     * Is in split string
     * @param stringScope String scope
     * @param stringKey String key
     * @param valueScope Value scope
     * @param valueKey Value key
     * @return Check result
     */
    public boolean isInSplitString(VariableScope stringScope, QName stringKey,
                            VariableScope valueScope, QName valueKey) {

        String[] values = getSplitString(stringScope, stringKey);
        String value = (String) valueScope.getVariableLocal(toString(valueKey));

        for (String stringValue : values) {
            if (stringValue.equals(value)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Add all if not contains
     * @param collection Collection
     * @param items Items
     * @param <T>
     */
    public <T> void addAllIfNotContains(Collection<T> collection, Iterable<T> items) {
        for (T item : items) {
            addIfNotContains(collection, item);
        }
    }

    /**
     * Add if not contains
     * @param collection Collection
     * @param item Item
     * @param <T>
     * @return Has been added
     */
    public <T> boolean addIfNotContains(Collection<T> collection, T item) {
        if (!collection.contains(item)) {
            collection.add(item);
            return true;
        }
        return false;
    }

    /**
     * Get collection of variables
     * @param scope Execution
     * @param key Key
     * @param <T>
     * @return Collection of variables
     */
    public <T> Collection<T> getCollection(VariableScope scope, String key) {
        if (scope.hasVariable(key)) {
            Object var = scope.getVariable(key);
            if (var instanceof Collection) {
                return (Collection<T>) var;
            }
        }
        Collection<T> varCollection = new ArrayList<>();
        scope.setVariable(key, varCollection);
        return varCollection;
    }

    /**
     * Get map of variables
     * @param scope Execution
     * @param key Key
     * @param <K>
     * @param <V>
     * @return Map of variables
     */
    public static <K, V> Map<K, V> getMap(VariableScope scope, String key) {
        if (scope.hasVariable(key)) {
            Object var = scope.getVariable(key);
            if (var instanceof Map) {
                return (Map<K, V>) var;
            }
        }
        Map<K, V> varMap = new TreeMap<>(KEYS_COMPARATOR);
        scope.setVariable(key, varMap);
        return varMap;
    }

    public static <K, V> Map<K, V> createMap() {
        return new TreeMap<>(KEYS_COMPARATOR);
    }

    /**
     * Get split string
     * @param scope Execution
     * @param key Key
     * @return Split result
     */
    public String[] getSplitString(VariableScope scope, QName key) {
        return getSplitString(scope, toString(key));
    }

    /**
     * Get split string
     * @param scope Execution
     * @param key Key
     * @return Split result
     */
    public String[] getSplitString(VariableScope scope, String key) {
        String variable = (String)scope.getVariable(key);
        if (variable == null) {
            return new String[0];
        }
        return variable.split(DEFAULT_DELIMITER);
    }

    /**
     * Transform qname to string
     * @param qname Qname
     * @return String representation
     */
    public String toString(QName qname) {
        return qname.toPrefixString(namespaceService).replaceAll(":", "_");
    }

    /**
     * Transform authority to node reference
     * @param authority Authority
     * @return Node reference
     */
    public NodeRef authorityToNodeRef(Object authority) {
        NodeRef result = null;
        if (authority instanceof IdentityLink) {
            IdentityLink identityLink = (IdentityLink) authority;
            String id = identityLink.getGroupId();
            if (id == null) {
                id = identityLink.getUserId();
            }
            result = id != null ? authorityService.getAuthorityNodeRef(id) : null;
        } else if (authority instanceof String) {
            result = authorityService.getAuthorityNodeRef((String) authority);
        } else if (authority instanceof NodeRef) {
            result = (NodeRef) authority;
        }
        return result;
    }

    /**
     * Get contained authorities
     * @param container Container
     * @param type Authority type
     * @param recurse Is recursive
     * @return Set of authorities
     */
    public Set<NodeRef> getContainedAuthorities(NodeRef container, AuthorityType type, boolean recurse) {

        QName containerType = nodeService.getType(container);
        if (dictionaryService.isSubClass(containerType, ContentModel.TYPE_AUTHORITY_CONTAINER)) {
            String groupName = (String) nodeService.getProperty(container, ContentModel.PROP_AUTHORITY_NAME);
            Set<String> authorities = authorityService.getContainedAuthorities(type, groupName, !recurse);
            Set<NodeRef> authoritiesRefs = new HashSet<>();

            for (String authority : authorities) {
                authoritiesRefs.add(authorityService.getAuthorityNodeRef(authority));
            }

            return authoritiesRefs;
        }
        return Collections.emptySet();
    }

    /**
     * Check - has task candidate
     * @param task Task
     * @param candidate Candidate
     * @return Check result
     */
    public boolean hasCandidate(TaskEntity task, NodeRef candidate) {
        if (candidate == null) {
            return false;
        }

        Set<IdentityLink> candidates = task.getCandidates();

        for (IdentityLink taskCandidate : candidates) {
            NodeRef candidateRef = authorityToNodeRef(taskCandidate);
            if (candidate.equals(candidateRef)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get first group candidate
     * @param task Task
     * @return Candidate
     */
    public NodeRef getFirstGroupCandidate(TaskEntity task) {

        Set<IdentityLink> candidates = task.getCandidates();

        for (IdentityLink taskCandidate : candidates) {
            String groupId = taskCandidate.getGroupId();
            if (groupId != null) {
                return authorityService.getAuthorityNodeRef(groupId);
            }
        }
        return null;
    }

    /**
     * Set performer
     * @param task Task
     * @param performer Performer
     */
    public void setPerformer(TaskEntity task, final NodeRef performer) {

        String performerKey = toString(CasePerformModel.ASSOC_PERFORMER);

        final NodeRef currentPerformer = (NodeRef) task.getVariable(performerKey);
        List<NodeRef> caseRoleRefs = caseRoleAssocsDao.getRolesByAssoc(
            new FlowableVariableScopeDelegate(task), CasePerformModel.ASSOC_CASE_ROLE);

        if (!caseRoleRefs.isEmpty()) {

            NodeRef caseRoleRef = caseRoleRefs.get(0);

            AuthenticationUtil.runAsSystem(() -> {
                caseRoleService.setDelegate(caseRoleRef, currentPerformer, performer);
                caseRoleService.updateRole(caseRoleRef);
                return null;
            });

            Set<NodeRef> assignees = caseRoleService.getAssignees(caseRoleRef);
            if (assignees.contains(performer)) {
                task.setVariableLocal(performerKey, performer);
                persistReassign(caseRoleRef, task.getProcessInstanceId(), currentPerformer, performer);
            }
        }
    }

    /**
     * Persist reassign
     * @param caseRole Case role
     * @param workflowId Workflow id
     * @param from From node
     * @param to To node
     */
    public void persistReassign(NodeRef caseRole, String workflowId, NodeRef from, NodeRef to) {
        Map<NodeRef, Map<String, Map<NodeRef, NodeRef>>> reassignmentByRole = TransactionalResourceHelper.getMap(REASSIGNMENT_KEY);
        Map<String, Map<NodeRef, NodeRef>> reassignmentByWorkflow = reassignmentByRole.get(caseRole);
        if (reassignmentByWorkflow == null) {
            reassignmentByWorkflow = new HashMap<>(1);
            reassignmentByRole.put(caseRole, reassignmentByWorkflow);
        }
        Map<NodeRef, NodeRef> reassignment = reassignmentByWorkflow.get(workflowId);
        if (reassignment == null) {
            reassignment = new HashMap<>(1);
            reassignmentByWorkflow.put(workflowId, reassignment);
        }
        reassignment.put(from, to);
    }

    /**
     * Get case role
     * @param performer Performer node
     * @param execution Execution
     * @return Role node
     */
    public NodeRef getCaseRole(NodeRef performer, ExecutionEntity execution) {

        Map<NodeRef, List<NodeRef>> pool = getMap(execution, PERFORMERS_ROLES_POOL);

        List<NodeRef> roles = pool.get(performer);
        if (roles != null && !roles.isEmpty()) {
            return roles.remove(roles.size() - 1);
        }

        return null;
    }

    /**
     * Get task roles
     * @param execution Execution
     * @return Task roles
     */
    public List<NodeRef> getTaskRoles(ExecutionEntity execution) {

        List<NodeRef> roles = new ArrayList<>();

        NodeRef pack = (NodeRef) execution.getVariable(toString(WorkflowModel.ASSOC_PACKAGE));
        NodeRef caseTask = null;

        List<AssociationRef> assocs = nodeService.getSourceAssocs(pack, ICaseTaskModel.ASSOC_WORKFLOW_PACKAGE);
        if (assocs != null && !assocs.isEmpty()) {
            caseTask = assocs.get(0).getSourceRef();
        }

        if (caseTask != null) {
            List<AssociationRef> roleAssocs = nodeService.getTargetAssocs(caseTask, CasePerformModel.ASSOC_PERFORMERS_ROLES);
            for (AssociationRef roleAssocRef : roleAssocs) {
                roles.add(roleAssocRef.getTargetRef());
            }
        }

        return roles;
    }

    /**
     * Fill roles by performers
     * @param execution Execution
     */
    public void fillRolesByPerformers(ExecutionEntity execution) {
        Map<NodeRef, List<NodeRef>> pool = getMap(execution, PERFORMERS_ROLES_POOL);
        Collection<NodeRef> performers = getCollection(execution, CasePerformUtils.PERFORMERS);

        List<NodeRef> roles = getTaskRoles(execution);

        for (NodeRef performer : performers) {
            List<NodeRef> performerRoles = new ArrayList<>();
            for (NodeRef roleRef : roles) {
                if (caseRoleService.isRoleMember(roleRef, performer)) {
                    performerRoles.add(roleRef);
                }
            }
            pool.put(performer, performerRoles);
        }
    }

    /**
     * Get authority name
     * @param authority Authority node
     * @return Authority name
     */
    public String getAuthorityName(NodeRef authority) {
        return RepoUtils.getAuthorityName(authority, nodeService, dictionaryService);
    }

    /**
     * Services setters
     */

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setNamespaceService(NamespaceService namespaceService) {
        this.namespaceService = namespaceService;
    }

    public void setAuthorityService(AuthorityService authorityService) {
        this.authorityService = authorityService;
    }

    public void setDictionaryService(DictionaryService dictionaryService) {
        this.dictionaryService = dictionaryService;
    }

    public void setRepositoryHelper(Repository repositoryHelper) {
        this.repositoryHelper = repositoryHelper;
    }

    public void setCaseRoleService(CaseRoleService caseRoleService) {
        this.caseRoleService = caseRoleService;
    }

    @Autowired
    public void setCaseRoleAssocsDao(CaseRoleAssocsDao caseRoleAssocsDao) {
        this.caseRoleAssocsDao = caseRoleAssocsDao;
    }

    private static class DummyComparator implements Serializable, Comparator<Object> {
        private static final long serialVersionUID = 2252429774415071539L;
        @Override
        public int compare(Object o1, Object o2) {
            if (Objects.equals(o1, o2)) {
                return 0;
            }
            if (o1 == null) {
                return -1;
            }
            if (o2 == null) {
                return 1;
            }
            if (!o1.getClass().equals(o2.getClass())) {
                return o1.getClass().toString().compareTo(o2.getClass().toString());
            }
            if (o1 instanceof Comparable) {
                return ((Comparable) o1).compareTo(o2);
            }
            return o1.toString().compareTo(o2.toString());
        }
    }
}
