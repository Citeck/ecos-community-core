package ru.citeck.ecos.stream.event;

import lombok.extern.log4j.Log4j;
import org.activiti.engine.delegate.DelegateTask;
import org.activiti.engine.delegate.TaskListener;
import org.alfresco.repo.workflow.WorkflowModel;
import org.alfresco.repo.workflow.WorkflowQNameConverter;
import org.alfresco.repo.workflow.activiti.ActivitiConstants;
import org.alfresco.service.cmr.repository.AssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.events.data.dto.EventDto;
import ru.citeck.ecos.events.data.dto.pasrse.EventDtoFactory;
import ru.citeck.ecos.events.data.dto.task.TaskEventDto;
import ru.citeck.ecos.events.data.dto.task.TaskEventType;
import ru.citeck.ecos.history.HistoryService;
import ru.citeck.ecos.history.TaskHistoryUtils;
import ru.citeck.ecos.model.ActivityModel;
import ru.citeck.ecos.model.CiteckWorkflowModel;
import ru.citeck.ecos.model.ICaseTaskModel;
import ru.citeck.ecos.role.CaseRoleService;
import ru.citeck.ecos.spring.registry.MappingRegistry;
import ru.citeck.ecos.utils.AuthorityUtils;
import ru.citeck.ecos.workflow.listeners.ListenerUtils;
import ru.citeck.ecos.workflow.listeners.WorkflowDocumentResolverRegistry;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Roman Makarskiy
 */
@Log4j
@Component
public class EventFactory {

    private static final Map<String, String> activitiEventNames;
    private static final String ACTIVITI_PREFIX = ActivitiConstants.ENGINE_ID + "$";
    private static final String ALFRESCO_SOURCE = "alfresco@";

    static {
        activitiEventNames = new HashMap<>(3);
        activitiEventNames.put(TaskListener.EVENTNAME_CREATE, TaskEventType.CREATE.toString());
        activitiEventNames.put(TaskListener.EVENTNAME_ASSIGNMENT, TaskEventType.ASSIGN.toString());
        activitiEventNames.put(TaskListener.EVENTNAME_COMPLETE, TaskEventType.COMPLETE.toString());
        activitiEventNames.put(TaskListener.EVENTNAME_DELETE, TaskEventType.DELETE.toString());
    }

    private final WorkflowDocumentResolverRegistry documentResolverRegistry;
    private final AuthorityUtils authorityUtils;
    private final NamespaceService namespaceService;
    private final AuthorityService authorityService;
    private final NodeService nodeService;
    private final TaskHistoryUtils taskHistoryUtils;
    private final MappingRegistry<String, String> panelOfAuthorized;
    private final CaseRoleService caseRoleService;

    private final WorkflowQNameConverter qNameConverter;
    private final String VAR_OUTCOME_PROPERTY_NAME;
    private final String VAR_COMMENT;
    private final String VAR_DESCRIPTION;

    @Autowired
    public EventFactory(@Qualifier("ecos.workflowDocumentResolverRegistry") WorkflowDocumentResolverRegistry
                                documentResolverRegistry,
                        AuthorityUtils authorityUtils,
                        @Qualifier("NamespaceService") NamespaceService namespaceService,
                        AuthorityService authorityService,
                        @Qualifier("panelOfAuthorized.mappingRegistry") MappingRegistry<String, String>
                                panelOfAuthorized, @Qualifier("NodeService") NodeService nodeService,
                        TaskHistoryUtils taskHistoryUtils,
                        CaseRoleService caseRoleService) {
        this.documentResolverRegistry = documentResolverRegistry;
        this.authorityUtils = authorityUtils;
        this.namespaceService = namespaceService;
        this.qNameConverter = new WorkflowQNameConverter(this.namespaceService);
        this.authorityService = authorityService;
        this.panelOfAuthorized = panelOfAuthorized;
        this.nodeService = nodeService;
        this.taskHistoryUtils = taskHistoryUtils;
        this.caseRoleService = caseRoleService;

        VAR_OUTCOME_PROPERTY_NAME = qNameConverter.mapQNameToName(WorkflowModel.PROP_OUTCOME_PROPERTY_NAME);
        VAR_COMMENT = qNameConverter.mapQNameToName(WorkflowModel.PROP_COMMENT);
        VAR_DESCRIPTION = qNameConverter.mapQNameToName(WorkflowModel.PROP_WORKFLOW_DESCRIPTION);
    }

    public Optional<EventDto> fromActivitiTask(DelegateTask task) {

        String eventName = activitiEventNames.get(task.getEventName());
        if (eventName == null) {
            log.warn("Unsupported task event: " + task.getEventName());
            return Optional.empty();
        }

        TaskEventDto dto = new TaskEventDto();
        dto.setType(eventName);
        dto.setId(UUID.randomUUID().toString());

        NodeRef document = documentResolverRegistry.getResolver(task.getExecution()).getDocument(task.getExecution());
        if (document != null) {
            dto.setDocument(document.toString());
            dto.setDocId(ALFRESCO_SOURCE + document.toString());
        }

        QName taskType = QName.createQName(ListenerUtils.getTaskFormKey(task),
                namespaceService);
        dto.setTaskType(taskType.toString());

        dto.setTaskOutcome(getTaskOutcome(task));
        dto.setTaskComment((String) task.getVariable(VAR_COMMENT));
        dto.setTaskAttachments(toStringSet(ListenerUtils.getTaskAttachments(task)));

        //TODO: additional properties?

        String assignee = task.getAssignee();
        NodeRef bpmPackage = ListenerUtils.getWorkflowPackage(task);
        if (bpmPackage != null) {
            List<AssociationRef> packageAssocs = nodeService.getSourceAssocs(bpmPackage,
                    ICaseTaskModel.ASSOC_WORKFLOW_PACKAGE);

            String roleName;
            List<String> authorized = new ArrayList<>();
            panelOfAuthorized.getMapping().forEach((auth, description) -> authorized.add(auth));

            if (StringUtils.isNotBlank(assignee) && CollectionUtils.isNotEmpty(authorized)) {
                List<NodeRef> listRoles = caseRoleService.getRoles(document);
                String authorizedName = taskHistoryUtils.getAuthorizedName(authorized, listRoles, assignee);
                roleName = StringUtils.isNoneBlank(authorizedName) ? authorizedName : taskHistoryUtils.getRoleName(
                        packageAssocs, assignee, task.getId(), ActivitiConstants.ENGINE_ID);
            } else {
                roleName = taskHistoryUtils.getRoleName(packageAssocs, assignee, task.getId(),
                        ActivitiConstants.ENGINE_ID);
                if (!packageAssocs.isEmpty()) {
                    NodeRef caseTask = packageAssocs.get(0).getSourceRef();

                    dto.setCaseTask(caseTask.toString());

                    Integer expectedPerformTime = (Integer) nodeService.getProperty(caseTask,
                            ActivityModel.PROP_EXPECTED_PERFORM_TIME);
                    if (expectedPerformTime == null) {
                        expectedPerformTime = taskHistoryUtils.getDefaultSLA();
                    }

                    dto.setExpectedPerformTime(expectedPerformTime);
                }
            }

            dto.setTaskRole(roleName);
        }

        //TODO: taskOriginalOwner?
        ArrayList<NodeRef> pooledActors = ListenerUtils.getPooledActors(task, authorityService);
        dto.setTaskPooledActors(toStringSet(pooledActors));

        Set<String> pooledUsers = new HashSet<>();
        pooledActors.forEach(nodeRef -> pooledUsers.addAll(authorityUtils.getContainedUsers(nodeRef, false)));
        dto.setTaskPooledUsers(pooledUsers);

        dto.setTaskInstanceId(ACTIVITI_PREFIX + task.getId());
        dto.setDueDate(task.getDueDate());

        String taskTitleProp = qNameConverter.mapQNameToName(CiteckWorkflowModel.PROP_TASK_TITLE);
        dto.setTaskTitle((String) task.getVariable(taskTitleProp));
        dto.setWorkflowInstanceId(ACTIVITI_PREFIX + task.getProcessInstanceId());
        dto.setWorkflowDescription((String) task.getExecution().getVariable(VAR_DESCRIPTION));
        dto.setAssignee(assignee);
        dto.setInitiator(StringUtils.isNotBlank(assignee) ? assignee : HistoryService.SYSTEM_USER);

        return Optional.of(EventDtoFactory.toEventDto(dto));
    }

    private String getTaskOutcome(DelegateTask task) {
        QName outcomeProperty = task.getVariable(VAR_OUTCOME_PROPERTY_NAME) != null
                ? (QName) task.getVariable(VAR_OUTCOME_PROPERTY_NAME) : WorkflowModel.PROP_OUTCOME;
        return (String) task.getVariable(qNameConverter.mapQNameToName(outcomeProperty));
    }

    private Set<String> toStringSet(List<?> set) {
        if (CollectionUtils.isEmpty(set)) {
            return Collections.emptySet();
        }
        return set
                .stream()
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

}
