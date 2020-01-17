package ru.citeck.ecos.utils;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.workflow.WorkflowModel;
import org.alfresco.repo.workflow.WorkflowQNameConverter;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.MLText;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.cmr.workflow.*;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.namespace.RegexQNamePattern;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.extensions.surf.util.I18NUtil;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.model.CiteckWorkflowModel;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Workflow service utils
 */
@Component
public class WorkflowUtils {

    private static final String ID_SEPARATOR_REGEX = "\\$";
    private static final Locale[] locales = {new Locale("ru"), new Locale("en")};

    private final WorkflowService workflowService;
    private final AuthorityUtils authorityUtils;
    private final NodeService nodeService;
    private final AuthorityService authorityService;
    private final PersonService personService;
    private final WorkflowAdminService workflowAdminService;
    private final WorkflowQNameConverter qnameConverter;

    @Autowired
    public WorkflowUtils(
            @Qualifier("WorkflowService") WorkflowService workflowService,
            AuthorityUtils authorityUtils,
            NodeService nodeService,
            AuthorityService authorityService,
            PersonService personService,
            WorkflowAdminService workflowAdminService,
            NamespaceService namespaceService
    ) {
        this.workflowService = workflowService;
        this.authorityUtils = authorityUtils;
        this.nodeService = nodeService;
        this.authorityService = authorityService;
        this.personService = personService;
        this.workflowAdminService = workflowAdminService;
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
        List<WorkflowTask> tasks = new ArrayList<>(getDocumentTasks(nodeRef, true));
        tasks.addAll(new ArrayList<>(getDocumentTasks(nodeRef, false)));
        return tasks;
    }

    public List<WorkflowTask> getDocumentTasks(NodeRef nodeRef, boolean active) {
        return getDocumentTasks(nodeRef, active, null);
    }

    public List<WorkflowTask> getDocumentTasks(NodeRef nodeRef, Boolean tasksStatus, String engine,
                                               boolean filterByCurrentUser) {

        List<WorkflowTask> tasks = new ArrayList<>();

        if (tasksStatus != null) {
            tasks = getDocumentTasks(nodeRef, tasksStatus, engine);
        } else {
            tasks.addAll(getDocumentTasks(nodeRef, false, engine));
            tasks.addAll(getDocumentTasks(nodeRef, true, engine));
        }

        if (filterByCurrentUser) {
            String currentUser = AuthenticationUtil.getFullyAuthenticatedUser();
            Set<NodeRef> authorities = authorityUtils.getUserAuthoritiesRefs();
            tasks = tasks.stream()
                    .filter(t -> isTaskActor(t, currentUser, authorities))
                    .collect(Collectors.toList());
        }

        return tasks;
    }

    public List<WorkflowTask> getDocumentTasks(NodeRef nodeRef, boolean active, String engine) {

        List<WorkflowInstance> workflows = workflowService.getWorkflowsForContent(nodeRef, active);

        if (StringUtils.isNotBlank(engine)) {
            String enginePrefix = engine + "$";
            workflows = workflows.stream()
                    .filter(workflow -> workflow.getId().startsWith(enginePrefix))
                    .collect(Collectors.toList());
        }

        List<WorkflowTask> tasks = new LinkedList<>();

        for (WorkflowInstance workflow : workflows) {
            tasks.addAll(AuthenticationUtil.runAsSystem(() -> getWorkflowTasks(workflow, active)));
        }

        return tasks;
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

        @SuppressWarnings({"unchecked", "rawtypes"})
        List<NodeRef> pooledActors = (List<NodeRef>) task.getProperties().get(WorkflowModel.ASSOC_POOLED_ACTORS);
        if (CollectionUtils.isEmpty(pooledActors)) {
            return results;
        }

        String originalOwner = (String) task.getProperties().get(QName.createQName("",
                "taskOriginalOwner"));
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

    private List<WorkflowTask> getWorkflowTasks(WorkflowInstance workflow, boolean active) {
        WorkflowTaskQuery query = new WorkflowTaskQuery();
        if (active) {
            query.setActive(true);
            query.setTaskState(WorkflowTaskState.IN_PROGRESS);
        }
        query.setOrderBy(new WorkflowTaskQuery.OrderBy[]{WorkflowTaskQuery.OrderBy.TaskDue_Asc});
        query.setProcessId(workflow.getId());
        return workflowService.queryTasks(query, true);
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

        MLText result = new MLText();

        String taskTitle = (String) task.getProperties().get(CiteckWorkflowModel.PROP_TASK_TITLE);

        if (StringUtils.isNotBlank(taskTitle)) {

            for (Locale locale : locales) {
                String taskTitleMessage = I18NUtil.getMessage(taskTitle, locale);
                if (StringUtils.isNotBlank(taskTitleMessage)) {
                    result.put(locale, taskTitleMessage);
                }
            }

            if (result.isEmpty()) {
                result.put(Locale.ENGLISH, taskTitle);
            }
        } else {
            result.put(Locale.ENGLISH, task.getTitle());
        }

        return result;
    }

    public NodeRef getTaskDocumentFromPackage(Object bpmPackage) {

        if (bpmPackage == null) {
            return null;
        }

        NodeRef documentRef = null;
        NodeRef packageRef = null;

        if (bpmPackage instanceof NodeRef) {
            packageRef = (NodeRef) bpmPackage;
        } else if (bpmPackage instanceof String) {
            String packageStr = (String) bpmPackage;
            if (NodeRef.isNodeRef(packageStr)) {
                packageRef = new NodeRef(packageStr);
            }
        }

        if (packageRef != null) {

            List<ChildAssociationRef> packageContent;

            packageContent = nodeService.getChildAssocs(packageRef,
                    WorkflowModel.ASSOC_PACKAGE_CONTAINS,
                    RegexQNamePattern.MATCH_ALL);

            if (packageContent != null && !packageContent.isEmpty()) {

                documentRef = packageContent.get(0).getChildRef();
            }
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

}
