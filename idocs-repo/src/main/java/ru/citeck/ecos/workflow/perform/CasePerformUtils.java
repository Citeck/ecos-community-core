package ru.citeck.ecos.workflow.perform;

import org.activiti.engine.delegate.VariableScope;
import org.activiti.engine.task.IdentityLink;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.jscript.ScriptNode;
import org.alfresco.repo.model.Repository;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.TransactionalResourceHelper;
import org.alfresco.repo.workflow.WorkflowModel;
import org.alfresco.repo.workflow.activiti.ActivitiScriptNode;
import org.alfresco.repo.workflow.activiti.ActivitiScriptNodeList;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.repository.AssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.AuthorityType;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import ru.citeck.ecos.icase.activity.dto.ActivityInstance;
import ru.citeck.ecos.icase.activity.dto.ActivityRef;
import ru.citeck.ecos.icase.activity.service.eproc.EProcActivityService;
import ru.citeck.ecos.icase.activity.service.eproc.EProcUtils;
import ru.citeck.ecos.icase.activity.service.eproc.importer.parser.CmmnDefinitionConstants;
import ru.citeck.ecos.model.BpmPackageModel;
import ru.citeck.ecos.model.CasePerformModel;
import ru.citeck.ecos.model.EcosProcessModel;
import ru.citeck.ecos.model.ICaseTaskModel;
import ru.citeck.ecos.role.CaseRoleAssocsDao;
import ru.citeck.ecos.role.CaseRoleService;
import ru.citeck.ecos.utils.RepoUtils;
import ru.citeck.ecos.workflow.variable.type.*;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Pavel Simonov
 */
public class CasePerformUtils {

    public static final String PERFORM_STAGES = "performStages";

    public static final String PERFORM_STAGE_IDX = "performStageIdx";
    public static final String REPEAT_PERFORMING = "repeatPerforming";

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
    public static final String ABORT_PROCESS = "abortProcess";

    public static final String REASSIGNMENT_KEY = "case-perform-reassignment";

    private static final DummyComparator KEYS_COMPARATOR = new DummyComparator();

    private static final List<String> VARIABLES_SHARING_IGNORED_PREFIXES = Arrays.asList("bpm", "cwf", "wfcf", "cm");
    private static final Pattern VARIABLES_PATTERN = Pattern.compile("^([^_]+)_(.+)");

    private NodeService nodeService;
    private NamespaceService namespaceService;
    private AuthorityService authorityService;
    private DictionaryService dictionaryService;
    private Repository repositoryHelper;
    private CaseRoleService caseRoleService;
    private EProcActivityService eprocActivityService;
    private CaseRoleAssocsDao caseRoleAssocsDao;

    private final ObjectMapper objectMapper = new ObjectMapper();

    boolean isCommentMandatory(PerformExecution execution, PerformTask task) {
        return isInSplitString(execution, CasePerformModel.PROP_OUTCOMES_WITH_MANDATORY_COMMENT,
                task, CasePerformModel.PROP_PERFORM_OUTCOME);
    }

    boolean isAbortOutcomeReceived(PerformExecution execution, PerformTask task) {
        return isInSplitString(execution, CasePerformModel.PROP_ABORT_OUTCOMES,
                task, CasePerformModel.PROP_PERFORM_OUTCOME);
    }

    void saveTaskResult(PerformExecution execution, PerformTask task) {

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

        NodeRef bpmPackage = ((ScriptNode) execution.getVariable("bpm_package")).getNodeRef();

        QName assocQName = QName.createQName(CasePerformModel.NAMESPACE, resultName);
        NodeRef result = nodeService.createNode(bpmPackage, CasePerformModel.ASSOC_PERFORM_RESULTS,
                assocQName, CasePerformModel.TYPE_PERFORM_RESULT,
                properties).getChildRef();

        nodeService.createAssociation(result, person, CasePerformModel.ASSOC_RESULT_PERSON);

        NodeRef performer = getFirstPerformer(task);
        if (performer != null) {
            nodeService.createAssociation(result, performer, CasePerformModel.ASSOC_RESULT_PERFORMER);
        }
    }

    void shareVariables(VariableScope from, VariableScope to) {
        Map<String, Object> variables = from.getVariables();
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            if (isSharedVariable(entry.getKey())) {
                to.setVariable(entry.getKey(), entry.getValue());
            }
        }
    }

    boolean isSharedVariable(String name) {
        Matcher matcher = VARIABLES_PATTERN.matcher(name);
        return matcher.matches() && !VARIABLES_SHARING_IGNORED_PREFIXES.contains(matcher.group(1));
    }

    boolean isInSplitString(VariableScope stringScope, QName stringKey,
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

    <T> void addAllIfNotContains(Collection<T> collection, Iterable<T> items) {
        for (T item : items) {
            addIfNotContains(collection, item);
        }
    }

    <T> boolean addIfNotContains(Collection<T> collection, T item) {
        if (!collection.contains(item)) {
            collection.add(item);
            return true;
        }
        return false;
    }

    public NodeRefsList getNodeRefsList(VariableScope scope, String key) {
        Object scopeVar = scope.getVariable(key);
        if (scopeVar != null && ArrayList.class.equals(scopeVar.getClass())) {
            scopeVar = new NodeRefsList((ArrayList) scopeVar);
        }
        return scopeVar != null ? (NodeRefsList) scopeVar : new NodeRefsList();
    }

    public StringsList getStringsList(VariableScope scope, String key) {
        Object scopeVar = scope.getVariable(key);
        if (scopeVar != null && ArrayList.class.equals(scopeVar.getClass())) {
            scopeVar = new StringsList((ArrayList) scopeVar);
        }
        return scopeVar != null ? (StringsList) scopeVar : new StringsList();
    }

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

    String[] getSplitString(VariableScope scope, QName key) {
        return getSplitString(scope, toString(key));
    }

    String[] getSplitString(VariableScope scope, String key) {
        String variable = (String) scope.getVariable(key);
        if (variable == null) {
            return new String[0];
        }
        return variable.split(DEFAULT_DELIMITER);
    }

    String toString(QName qname) {
        return qname.toPrefixString(namespaceService).replaceAll(":", "_");
    }

    NodeRef authorityToNodeRef(Object authority) {
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

    Set<NodeRef> getContainedAuthorities(NodeRef container, AuthorityType type, boolean recurse) {

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

    boolean hasCandidate(PerformTask task, NodeRef candidate) {
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

    NodeRef getFirstGroupCandidate(PerformTask task) {

        Set<IdentityLink> candidates = task.getCandidates();

        for (IdentityLink taskCandidate : candidates) {
            String groupId = taskCandidate.getGroupId();
            if (groupId != null) {
                return authorityService.getAuthorityNodeRef(groupId);
            }
        }
        return null;
    }

    public NodeRef getFirstPerformer(VariableScope variableScope) {

        String performerKey = toString(CasePerformModel.ASSOC_PERFORMER);
        Object rawPerformer = variableScope.getVariable(performerKey);

        if (rawPerformer == null) {
            return null;
        }
        if (rawPerformer instanceof NodeRef) {
            return (NodeRef) rawPerformer;
        }
        if (rawPerformer instanceof ActivitiScriptNodeList) {
            ActivitiScriptNodeList list = (ActivitiScriptNodeList) rawPerformer;
            List<NodeRef> result = list.getNodeReferences();
            return CollectionUtils.isNotEmpty(result) ? result.get(0) : null;
        }
        if (rawPerformer instanceof ActivitiScriptNode) {
            return ((ActivitiScriptNode) rawPerformer).getNodeRef();
        }
        if (rawPerformer instanceof ScriptNode) {
            return ((ScriptNode) rawPerformer).getNodeRef();
        }

        throw new IllegalStateException("Receive unsupported instance of performer of class: "
                + rawPerformer.getClass());
    }

    void removeDelegates(PerformTask task) {

        final NodeRef caseRoleRef = caseRoleAssocsDao.getRolesByAssoc(task, CasePerformModel.ASSOC_CASE_ROLE)
            .stream()
            .findFirst()
            .orElse(null);

        if (caseRoleRef != null) {
            AuthenticationUtil.runAsSystem(() -> {
                caseRoleService.removeDelegates(caseRoleRef);
                return null;
            });
        }
    }

    void setPerformer(PerformTask task, final NodeRef performer) {

        final NodeRef currentPerformer = getFirstPerformer(task);
        final NodeRef caseRoleRef = caseRoleAssocsDao.getRolesByAssoc(task, CasePerformModel.ASSOC_CASE_ROLE)
            .stream()
            .findFirst()
            .orElse(null);

        if (caseRoleRef != null) {

            AuthenticationUtil.runAsSystem(() -> {
                caseRoleService.setDelegate(caseRoleRef, currentPerformer, performer);
                caseRoleService.updateRole(caseRoleRef);
                return null;
            });

            Set<NodeRef> assignees = caseRoleService.getAssignees(caseRoleRef);
            if (assignees.contains(performer)) {
                task.setVariableLocal(toString(CasePerformModel.ASSOC_PERFORMER), performer);
                persistReassign(caseRoleRef, task.getProcessInstanceId(), currentPerformer, performer);
            }
        }
    }

   /* void onCreateTask(PerformTask task) {

        final NodeRef currentPerformer = getFirstPerformer(task);
        final NodeRef caseRoleRef = caseRoleAssocsDao.getRolesByAssoc(task, CasePerformModel.ASSOC_CASE_ROLE)
            .stream()
            .findFirst()
            .orElse(null);

        if (caseRoleRef != null) {

            AuthenticationUtil.runAsSystem(() -> {
                caseRoleService.setDelegate(caseRoleRef, currentPerformer, performer);
                caseRoleService.updateRole(caseRoleRef);
                return null;
            });

            Set<NodeRef> assignees = caseRoleService.getAssignees(caseRoleRef);
            if (assignees.contains(performer)) {
                task.setVariableLocal(toString(CasePerformModel.ASSOC_PERFORMER), performer);
                persistReassign(caseRoleRef, task.getProcessInstanceId(), currentPerformer, performer);
            }
        }
    }*/

    void persistReassign(NodeRef caseRole, String workflowId, NodeRef from, NodeRef to) {
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

    NodeRef getCaseRole(NodeRef performer, PerformExecution execution) {

        Map<NodeRef, List<NodeRef>> pool = getRolesPool(execution);

        List<NodeRef> roles = pool.get(performer);
        if (roles != null && !roles.isEmpty()) {
            NodeRef result = roles.remove(roles.size() - 1);
            execution.setVariable(PERFORMERS_ROLES_POOL, pool);
            return result;
        }

        return null;
    }

    List<NodeRef> getTaskRoles(VariableScope execution) {

        ActivitiScriptNode pack = (ActivitiScriptNode) execution.getVariable(toString(WorkflowModel.ASSOC_PACKAGE));

        List<NodeRef> cmmnRoles = getRolesFromEprocCmmn(pack);
        if (CollectionUtils.isNotEmpty(cmmnRoles)) {
            return cmmnRoles;
        }

        NodeRef caseTask = RepoUtils.getFirstSourceAssoc(pack.getNodeRef(),
                ICaseTaskModel.ASSOC_WORKFLOW_PACKAGE, nodeService);
        if (caseTask != null) {
            List<NodeRef> roles = new ArrayList<>();
            List<AssociationRef> roleAssocs = nodeService.getTargetAssocs(caseTask, CasePerformModel.ASSOC_PERFORMERS_ROLES);
            for (AssociationRef roleAssocRef : roleAssocs) {
                roles.add(roleAssocRef.getTargetRef());
            }
            return roles;
        }

        return Collections.emptyList();
    }

    private List<NodeRef> getRolesFromEprocCmmn(ActivitiScriptNode pack) {
        String rawActivityRef = (String) nodeService.getProperty(pack.getNodeRef(), EcosProcessModel.PROP_ACTIVITY_REF);
        if (StringUtils.isBlank(rawActivityRef)) {
            return Collections.emptyList();
        }

        ActivityRef activityRef = ActivityRef.of(rawActivityRef);
        ActivityInstance instance = eprocActivityService.getStateInstance(activityRef);
        if (instance == null) {
            return Collections.emptyList();
        }

        String[] taskRoleVarNames = EProcUtils.getAnyAttribute(instance,
                CmmnDefinitionConstants.TASK_ROLE_VAR_NAMES_SET_KEY, String[].class);
        if (taskRoleVarNames == null || taskRoleVarNames.length == 0) {
            return Collections.emptyList();
        }

        NodeRef documentRef = getDocumentRef(pack);
        List<NodeRef> roles = new ArrayList<>(taskRoleVarNames.length);
        for (String taskRoleVarName : taskRoleVarNames) {
            NodeRef roleRef = caseRoleService.getRole(documentRef, taskRoleVarName);
            roles.add(roleRef);
        }

        return roles;
    }

    private NodeRef getDocumentRef(ActivitiScriptNode pack) {
        return RepoUtils.getFirstChildAssoc(pack.getNodeRef(), BpmPackageModel.ASSOC_PACKAGE_CONTAINS, nodeService);
    }

    void fillRolesByPerformers(VariableScope execution) {

        Map<NodeRef, List<NodeRef>> pool = getRolesPool(execution);
        Collection<NodeRef> performers = getNodeRefsList(execution, CasePerformUtils.PERFORMERS);

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

        execution.setVariable(PERFORMERS_ROLES_POOL, pool);
    }

    String getAuthorityName(NodeRef authority) {
        return RepoUtils.getAuthorityName(authority, nodeService, dictionaryService);
    }

    public TaskConfigs getTaskConfigs(VariableScope scope) {
        Object configs = scope.getVariable(TASK_CONFIGS);
        TaskConfigs result;
        if (configs instanceof TaskConfigs) {
            result = (TaskConfigs) configs;
        } else {
            result = new TaskConfigs();
            scope.setVariable(TASK_CONFIGS, result);
        }
        return result;
    }

    public TaskStages getTaskStages(VariableScope scope) {
        Object stagesObj = scope.getVariable(PERFORM_STAGES);
        if (stagesObj instanceof TaskStages) {
            return (TaskStages) stagesObj;
        }
        if (stagesObj instanceof String) {
            try {
                return objectMapper.readValue((String) stagesObj, TaskStages.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return new TaskStages();
    }

    public Map<NodeRef, List<NodeRef>> getRolesPool(VariableScope scope) {
        Object result = scope.getVariable(PERFORMERS_ROLES_POOL);
        if (result == null) {
            result = new NodeRefToNodeRefsMap();
        }
        return (Map<NodeRef, List<NodeRef>>) result;
    }

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
    public void setEprocActivityService(EProcActivityService eprocActivityService) {
        this.eprocActivityService = eprocActivityService;
    }

    @Autowired
    public void setCaseRoleAssocsDao(CaseRoleAssocsDao caseRoleAssocsDao) {
        this.caseRoleAssocsDao = caseRoleAssocsDao;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
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
