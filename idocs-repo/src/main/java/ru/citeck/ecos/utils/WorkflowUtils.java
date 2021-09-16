package ru.citeck.ecos.utils;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.workflow.WorkflowModel;
import org.alfresco.repo.workflow.WorkflowQNameConverter;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.dictionary.ModelDefinition;
import org.alfresco.service.cmr.dictionary.PropertyDefinition;
import org.alfresco.service.cmr.dictionary.TypeDefinition;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.MLText;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.cmr.workflow.*;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.namespace.RegexQNamePattern;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.extensions.surf.util.I18NUtil;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.model.CiteckWorkflowModel;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.search.ftsquery.FTSQuery;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ru.citeck.ecos.utils.WorkflowConstants.VAR_TASK_ORIGINAL_OWNER;

/**
 * Workflow service utils
 */
@Component
public class WorkflowUtils {

    private static final String TASK_START_PREFIX = "start";
    private static final String ID_SEPARATOR_REGEX = "\\$";
    private static final Locale[] locales = {new Locale("ru"), new Locale("en")};

    private final WorkflowService workflowService;
    private final AuthorityUtils authorityUtils;
    private final NodeService nodeService;
    private final AuthorityService authorityService;
    private final PersonService personService;
    private final WorkflowAdminService workflowAdminService;
    private final WorkflowQNameConverter qnameConverter;
    private final NamespaceService namespaceService;
    private final DictionaryService dictionaryService;
    private final SearchService searchService;
    private final NodeUtils nodeUtils;

    @Autowired
    public WorkflowUtils(
        @Qualifier("WorkflowService") WorkflowService workflowService,
        AuthorityUtils authorityUtils,
        NodeService nodeService,
        AuthorityService authorityService,
        PersonService personService,
        WorkflowAdminService workflowAdminService,
        NamespaceService namespaceService,
        DictionaryService dictionaryService,
        SearchService searchService,
        NodeUtils nodeUtils
    ) {
        this.nodeUtils = nodeUtils;
        this.searchService = searchService;
        this.workflowService = workflowService;
        this.authorityUtils = authorityUtils;
        this.nodeService = nodeService;
        this.authorityService = authorityService;
        this.personService = personService;
        this.workflowAdminService = workflowAdminService;
        this.namespaceService = namespaceService;
        this.dictionaryService = dictionaryService;
        this.qnameConverter = new WorkflowQNameConverter(namespaceService);
    }

    public String mapQNameToName(QName qname) {
        return qnameConverter.mapQNameToName(qname);
    }

    public QName mapNameToQName(String name) {
        return qnameConverter.mapNameToQName(name);
    }

    /**
     * Get workflow definition by global name
     *
     * @param workflowName Workflow name
     * @return Workflow definition
     */
    public WorkflowDefinition getWorkflowDefinition(String workflowName) {
        if (workflowName == null) {
            return null;
        }
        String[] parts = workflowName.split(ID_SEPARATOR_REGEX);
        if (parts.length != 2) {
            return null;
        }
        if (!workflowAdminService.isEngineEnabled(parts[0])) {
            return null;
        } else {
            return workflowService.getDefinitionByName(workflowName);
        }
    }

    public List<WorkflowTask> getDocumentUserTasks(NodeRef nodeRef) {
        List<WorkflowTask> tasks = new ArrayList<>(getDocumentUserTasks(nodeRef, true));
        tasks.addAll(new ArrayList<>(getDocumentUserTasks(nodeRef, false)));
        return tasks;
    }

    public List<WorkflowTask> getDocumentUserTasks(NodeRef nodeRef, boolean active) {
        return getDocumentUserTasks(nodeRef, active, null);
    }

    /**
     * Get current user document tasks
     */
    public List<WorkflowTask> getDocumentUserTasks(NodeRef nodeRef, boolean active, String engine) {

        List<WorkflowTask> tasks = getDocumentTasks(nodeRef, active, engine);

        if (!tasks.isEmpty()) {

            String userName = AuthenticationUtil.getFullyAuthenticatedUser();
            Set<NodeRef> authorities = authorityUtils.getUserAuthoritiesRefs();

            tasks = tasks.stream()
                .filter(t -> isTaskActor(t, userName, authorities))
                .collect(Collectors.toList());
        }
        return tasks;
    }

    public List<WorkflowTask> getDocumentTasks(NodeRef nodeRef) {
        return getDocumentTasks(RecordRef.create("", nodeRef.toString()));
    }

    public List<WorkflowTask> getDocumentTasks(RecordRef recordRef) {
        List<WorkflowTask> tasks = new ArrayList<>(getDocumentTasks(recordRef, true));
        tasks.addAll(new ArrayList<>(getDocumentTasks(recordRef, false)));
        return tasks;
    }

    public List<WorkflowTask> getDocumentTasks(NodeRef nodeRef, boolean active) {
        return getDocumentTasks(nodeRef, active, null);
    }

    public List<WorkflowTask> getDocumentTasks(RecordRef recordRef, boolean active) {
        return getDocumentTasks(recordRef, active, null);
    }

    public List<WorkflowTask> getDocumentTasks(NodeRef nodeRef,
                                               Boolean tasksStatus,
                                               String engine,
                                               boolean filterByCurrentUser) {

        return getDocumentTasks(
            RecordRef.create("", nodeRef.toString()),
            tasksStatus,
            engine,
            filterByCurrentUser
        );
    }

    public List<WorkflowTask> getDocumentTasks(RecordRef recordRef,
                                               Boolean tasksStatus,
                                               String engine,
                                               boolean filterByCurrentUser) {

        List<WorkflowTask> tasks = new ArrayList<>();

        if (tasksStatus != null) {
            tasks = getDocumentTasks(recordRef, tasksStatus, engine);
        } else {
            tasks.addAll(getDocumentTasks(recordRef, false, engine));
            tasks.addAll(getDocumentTasks(recordRef, true, engine));
        }

        if (filterByCurrentUser) {
            tasks = filterTasksByCurrentUser(tasks);
        }

        return tasks;
    }

    /**
     * @return list of document task. Filtered from prefix {@link WorkflowUtils#TASK_START_PREFIX}
     */
    public List<WorkflowTask> getDocumentTasks(NodeRef nodeRef, boolean active, String engine) {
        return getDocumentTasks(RecordRef.create("", nodeRef.toString()), active, engine);
    }

    public List<WorkflowInstance> getDocumentWorkflows(RecordRef recordRef, Boolean active, String engine) {

        List<WorkflowInstance> workflows = Collections.emptyList();

        if (recordRef.getId().startsWith(NodeUtils.WORKSPACE_PREFIX)) {

            NodeRef nodeRef = new NodeRef(recordRef.getId());
            workflows = workflowService.getWorkflowsForContent(nodeRef, active);

        } else {

            NodeRef packageRef = FTSQuery.create()
                    .type(WorkflowModel.TYPE_PACKAGE).and()
                    .exact(CiteckWorkflowModel.PROP_DOCUMENT_PROP, recordRef.toString())
                    .transactional()
                    .queryOne(searchService)
                    .orElse(null);

            if (packageRef == null) {
                return Collections.emptyList();
            }

            String workflowInstance = (String) nodeService.getProperty(packageRef, WorkflowModel.PROP_WORKFLOW_INSTANCE_ID);
            if (workflowInstance != null && workflowInstance.length() > 0) {
                workflows = Collections.singletonList(workflowService.getWorkflowById(workflowInstance));
            }
        }

        if (workflows.isEmpty()) {
            return Collections.emptyList();
        }

        Stream<WorkflowInstance> workflowsStream = workflows.stream();
        if (StringUtils.isNotBlank(engine)) {
            String enginePrefix = engine + "$";
            workflowsStream = workflowsStream.filter(workflow -> workflow.getId().startsWith(enginePrefix));
        }
        if (active != null) {
            workflowsStream = workflowsStream.filter(workflow -> workflow.isActive() == active);
        }
        workflows = workflowsStream.collect(Collectors.toList());

        return workflows;
    }

    /**
     * @return list of document task. Filtered from prefix {@link WorkflowUtils#TASK_START_PREFIX}
     */
    public List<WorkflowTask> getDocumentTasks(RecordRef recordRef, boolean active, String engine) {

        List<WorkflowInstance> workflows = getDocumentWorkflows(recordRef, active, engine);

        List<WorkflowTask> tasks = new LinkedList<>();

        for (WorkflowInstance workflow : workflows) {
            tasks.addAll(getWorkflowTasks(workflow, active));
        }

        return tasks.stream()
            .filter(workflowTask -> !StringUtils.contains(workflowTask.getId(), TASK_START_PREFIX))
            .collect(Collectors.toList());
    }

    public List<NodeRef> getTaskActors(String taskId) {
        LinkedList<NodeRef> results = new LinkedList<>();
        WorkflowTask task = workflowService.getTaskById(taskId);

        String assigneeName = (String) task.getProperties().get(ContentModel.PROP_OWNER);
        if (StringUtils.isNotBlank(assigneeName)) {
            NodeRef assignee = personService.getPerson(assigneeName);
            results.add(assignee);
            return results;
        }

        @SuppressWarnings({"unchecked"})
        List<NodeRef> pooledActors = (List<NodeRef>) task.getProperties().get(WorkflowModel.ASSOC_POOLED_ACTORS);
        if (CollectionUtils.isEmpty(pooledActors)) {
            return results;
        }

        String originalOwner = (String) task.getProperties().get(QName.createQName("",
            VAR_TASK_ORIGINAL_OWNER));
        NodeRef originalOwnerNodeRef = StringUtils.isNotBlank(originalOwner)
            ? authorityService.getAuthorityNodeRef(originalOwner) : null;

        if (originalOwnerNodeRef != null) {
            if (pooledActors.contains(originalOwnerNodeRef) && pooledActors.indexOf(originalOwnerNodeRef) != -1) {
                pooledActors.remove(originalOwnerNodeRef);
                pooledActors.add(0, originalOwnerNodeRef);
            }
        }

        results.addAll(pooledActors);

        return results;
    }

    private boolean isTaskActor(WorkflowTask task, String userName, Set<NodeRef> authorities) {

        boolean matches;

        Map<QName, Serializable> properties = task.getProperties();
        String actor = (String) properties.get(ContentModel.PROP_OWNER);
        List<?> pooledActors = (List<?>) properties.get(WorkflowModel.ASSOC_POOLED_ACTORS);

        if (actor != null) {
            matches = actor.equals(userName);
        } else {
            matches = pooledActors != null && !CollectionUtils.intersection(pooledActors, authorities).isEmpty();
        }
        return matches;
    }

    public List<WorkflowTask> getWorkflowTasks(WorkflowInstance workflow, boolean active) {
        return getWorkflowTasks(workflow.getId(), active);
    }

    private List<WorkflowTask> filterTasksByCurrentUser(List<WorkflowTask> tasks) {

        if (tasks == null) {
            return Collections.emptyList();
        }

        String currentUser = AuthenticationUtil.getFullyAuthenticatedUser();
        Set<NodeRef> authorities = authorityUtils.getUserAuthoritiesRefs();

        return tasks.stream()
            .filter(t -> isTaskActor(t, currentUser, authorities))
            .collect(Collectors.toList());
    }

    public List<WorkflowTask> getWorkflowTasks(String workflowId, boolean active, boolean filterByCurrentUser) {
        List<WorkflowTask> tasks = getWorkflowTasks(workflowId, active);
        if (filterByCurrentUser) {
            tasks = filterTasksByCurrentUser(tasks);
        }
        return tasks;
    }

    /**
     * @return List of tasks, may contains tasks with prefix {@link WorkflowUtils#TASK_START_PREFIX}
     */
    public List<WorkflowTask> getWorkflowTasks(String workflowId, boolean active) {
        WorkflowTaskQuery query = new WorkflowTaskQuery();
        if (!active) {
            query.setActive(null);
            query.setTaskState(WorkflowTaskState.COMPLETED);
        }
        query.setOrderBy(new WorkflowTaskQuery.OrderBy[]{WorkflowTaskQuery.OrderBy.TaskDue_Asc});
        query.setProcessId(workflowId);
        return AuthenticationUtil.runAsSystem(() -> workflowService.queryTasks(query, true));
    }

    public String getTaskTitle(WorkflowTask task) {

        String taskTitle = (String) task.getProperties().get(CiteckWorkflowModel.PROP_TASK_TITLE);
        if (StringUtils.isNotBlank(taskTitle)) {
            String taskTitleMessage = I18NUtil.getMessage(taskTitle);
            if (StringUtils.isNotBlank(taskTitleMessage)) {
                taskTitle = taskTitleMessage;
            }
        } else {
            taskTitle = task.getTitle();
        }
        return taskTitle;
    }

    public MLText getTaskMLTitle(WorkflowTask task) {
        String taskTitle = (String) task.getProperties().get(CiteckWorkflowModel.PROP_TASK_TITLE);

        MLText result;
        if (StringUtils.isNotBlank(taskTitle)) {
            result = getMessagesByKey(taskTitle);
            if (result.isEmpty()) {
                result.put(Locale.ENGLISH, taskTitle);
            }
        } else {
            result = getMessagesByTask(task);
            if (result.isEmpty()) {
                result.put(Locale.ENGLISH, task.getTitle());
            }
        }

        return result;
    }

    private MLText getMessagesByTask(WorkflowTask task) {
        WorkflowTaskDefinition workflowTaskDefinition = task.getDefinition();
        TypeDefinition typeDefinition = workflowTaskDefinition != null ? workflowTaskDefinition.getMetadata() : null;

        if (typeDefinition == null) {
            return new MLText();
        }

        ModelDefinition model = typeDefinition.getModel();
        String modelName = "";
        if (model != null) {
            modelName = model.getName().getPrefixString()
                .replace(":", "_")
                .concat(".type.");
        }
        String typeName = typeDefinition.getName().getPrefixString().replace(":", "_");
        return getMessagesByKey(modelName + typeName + ".title");
    }

    private MLText getMessagesByKey(String messageKey) {
        MLText result = new MLText();

        for (Locale locale : locales) {
            String message = I18NUtil.getMessage(messageKey, locale);
            if (StringUtils.isNotBlank(message)) {
                result.put(locale, message);
            }
        }

        return result;
    }

    @NotNull
    public RecordRef getTaskDocumentRefFromPackage(@Nullable Object bpmPackage) {

        NodeRef packageRef = nodeUtils.getNodeRefOrNull(bpmPackage);
        if (packageRef == null) {
            return RecordRef.EMPTY;
        }

        String documentProp = (String) nodeService.getProperty(packageRef, CiteckWorkflowModel.PROP_DOCUMENT_PROP);
        if (StringUtils.isNotBlank(documentProp)) {
            return RecordRef.valueOf(documentProp);
        }

        NodeRef documentNodeRef = getTaskDocumentFromPackage(packageRef);
        if (documentNodeRef != null) {
            return RecordRef.create("", documentNodeRef.toString());
        }

        return RecordRef.EMPTY;
    }

    public NodeRef getTaskDocumentFromPackage(Object bpmPackage) {

        if (bpmPackage == null) {
            return null;
        }
        NodeRef packageRef = nodeUtils.getNodeRefOrNull(bpmPackage);
        if (packageRef == null) {
            return null;
        }

        NodeRef documentRef = null;

        List<ChildAssociationRef> packageContent = nodeService.getChildAssocs(packageRef,
            WorkflowModel.ASSOC_PACKAGE_CONTAINS, RegexQNamePattern.MATCH_ALL);

        if (packageContent.isEmpty()) {
            packageContent = nodeService.getChildAssocs(packageRef, ContentModel.ASSOC_CONTAINS,
                RegexQNamePattern.MATCH_ALL);
        }
        if (packageContent != null && !packageContent.isEmpty()) {
            documentRef = packageContent.get(0).getChildRef();
        }

        return documentRef;
    }

    public int convertPriorityBpmnToWorkflowTask(int bpmnPriority) {
        if (bpmnPriority <= 3) {
            return bpmnPriority;
        }

        int min = Math.min(bpmnPriority, 100);
        BigDecimal multiples = new BigDecimal(3).multiply(new BigDecimal(min));
        BigDecimal res = multiples.divide(new BigDecimal(100), RoundingMode.HALF_UP);
        return res.intValue() == 0 ? 1 : res.intValue();
    }

    public Optional<String> getOutcomePropFromModel(String formKey) {

        if (StringUtils.isBlank(formKey)) {
            return Optional.empty();
        }
        QName formKeyQName = QName.resolveToQName(namespaceService, formKey);
        if (formKeyQName == null) {
            return Optional.empty();
        }

        PropertyDefinition prop = dictionaryService.getProperty(formKeyQName, WorkflowModel.PROP_OUTCOME_PROPERTY_NAME);
        String value = prop != null ? prop.getDefaultValue() : null;

        if (value == null) {
            return Optional.empty();
        }

        QName propQName = QName.resolveToQName(namespaceService, value);
        if (propQName == null) {
            return Optional.empty();
        }

        return Optional.of(propQName.toPrefixString(namespaceService).replaceAll(":", "_"));
    }
}
