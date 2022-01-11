package ru.citeck.ecos.flowable.event;

import lombok.extern.slf4j.Slf4j;
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
import org.flowable.engine.TaskService;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.events.data.dto.EventDto;
import ru.citeck.ecos.events.data.dto.pasrse.EventDtoFactory;
import ru.citeck.ecos.events.data.dto.task.TaskEventDto;
import ru.citeck.ecos.events.data.dto.task.TaskEventType;
import ru.citeck.ecos.flowable.constants.FlowableConstants;
import ru.citeck.ecos.flowable.services.FlowableCustomCommentService;
import ru.citeck.ecos.flowable.utils.FlowableListenerUtils;
import ru.citeck.ecos.flowable.utils.FlowableUtils;
import ru.citeck.ecos.history.HistoryService;
import ru.citeck.ecos.history.TaskHistoryUtils;
import ru.citeck.ecos.model.CiteckWorkflowModel;
import ru.citeck.ecos.model.ICaseTaskModel;
import ru.citeck.ecos.utils.AuthorityUtils;

import java.util.*;
import java.util.stream.Collectors;

import static ru.citeck.ecos.flowable.constants.FlowableConstants.ENGINE_PREFIX;

@Slf4j
@SuppressWarnings("Duplicates")
@Component
public class FlowableEventFactory {

    private static final Map<String, String> flowableEventNames;
    private static final String ALFRESCO_SOURCE = "alfresco@";

    private final String VAR_OUTCOME_PROPERTY_NAME;
    private final String VAR_COMMENT;
    private final String VAR_DESCRIPTION;

    private final NodeService nodeService;
    private final NamespaceService namespaceService;
    private final WorkflowQNameConverter qNameConverter;
    private final FlowableCustomCommentService flowableCustomCommentService;
    private final TaskService taskService;
    private final TaskHistoryUtils taskHistoryUtils;
    private final AuthorityService authorityService;
    private final AuthorityUtils authorityUtils;

    static {
        flowableEventNames = new HashMap<>(3);
        flowableEventNames.put(TaskListener.EVENTNAME_CREATE, TaskEventType.CREATE.toString());
        flowableEventNames.put(TaskListener.EVENTNAME_ASSIGNMENT, TaskEventType.ASSIGN.toString());
        flowableEventNames.put(TaskListener.EVENTNAME_COMPLETE, TaskEventType.COMPLETE.toString());
        flowableEventNames.put(TaskListener.EVENTNAME_DELETE, TaskEventType.DELETE.toString());
    }

    @Autowired
    public FlowableEventFactory(NodeService nodeService, NamespaceService namespaceService,
                                FlowableCustomCommentService flowableCustomCommentService, TaskService taskService,
                                TaskHistoryUtils taskHistoryUtils, AuthorityService authorityService,
                                AuthorityUtils authorityUtils) {
        this.nodeService = nodeService;
        this.namespaceService = namespaceService;
        this.qNameConverter = new WorkflowQNameConverter(namespaceService);
        this.flowableCustomCommentService = flowableCustomCommentService;
        this.taskService = taskService;
        this.taskHistoryUtils = taskHistoryUtils;
        this.authorityService = authorityService;
        this.authorityUtils = authorityUtils;

        VAR_OUTCOME_PROPERTY_NAME = qNameConverter.mapQNameToName(WorkflowModel.PROP_OUTCOME_PROPERTY_NAME);
        VAR_COMMENT = qNameConverter.mapQNameToName(WorkflowModel.PROP_COMMENT);
        VAR_DESCRIPTION = qNameConverter.mapQNameToName(WorkflowModel.PROP_WORKFLOW_DESCRIPTION);
    }

    public Optional<EventDto> fromFlowableTask(@NotNull DelegateTask task) {

        String eventName = flowableEventNames.get(task.getEventName());
        if (eventName == null) {
            log.warn("Unsupported task event: " + task.getEventName());
            return Optional.empty();
        }

        TaskEventDto dto = new TaskEventDto();
        dto.setType(eventName);
        dto.setId(UUID.randomUUID().toString());

        NodeRef document = FlowableListenerUtils.getDocument(task, nodeService);
        if (document != null) {
            dto.setDocument(document.toString());
            dto.setDocId(ALFRESCO_SOURCE + document);
        }

        String taskFormKey = (String) task.getVariable(ActivitiConstants.PROP_TASK_FORM_KEY);
        if (StringUtils.isBlank(taskFormKey)) {
            log.warn("Task form key not found for task: " + task.getId());
            return Optional.empty();
        }

        QName taskType = QName.createQName(taskFormKey, namespaceService);
        dto.setTaskType(taskType.toString());

        dto.setTaskOutcome(getTaskOutcome(task));
        dto.setTaskComment(getTaskComment(task));
        dto.setTaskAttachments(toStringSet(FlowableListenerUtils.getTaskAttachments(task)));

        String assignee = task.getAssignee();
        dto.setAssignee(assignee);
        dto.setInitiator(StringUtils.isNotBlank(assignee) ? assignee : HistoryService.SYSTEM_USER);

        NodeRef bpmPackage = FlowableListenerUtils.getWorkflowPackage(task);
        if (bpmPackage != null) {
            List<AssociationRef> packageAssocs = nodeService.getSourceAssocs(bpmPackage,
                ICaseTaskModel.ASSOC_WORKFLOW_PACKAGE);

            if (CollectionUtils.isEmpty(packageAssocs)) {
                NodeRef caseTask = packageAssocs.get(0).getSourceRef();
                dto.setCaseTask(caseTask.toString());
            }

            String roleName = taskHistoryUtils.getRoleName(packageAssocs, assignee, task.getId(),
                FlowableConstants.ENGINE_ID);

            dto.setTaskRole(roleName);
        }

        ArrayList<NodeRef> pooledActors = FlowableListenerUtils.getPooledActors(task, authorityService);
        dto.setTaskPooledActors(toStringSet(pooledActors));

        Set<String> pooledUsers = new HashSet<>();
        pooledActors.forEach(nodeRef -> pooledUsers.addAll(authorityUtils.getContainedUsers(nodeRef, false)));

        //Flowable does not send assign event, so add assignee to pooled users
        if (StringUtils.equals(eventName, TaskEventType.CREATE.toString()) && pooledUsers.isEmpty()) {
            pooledUsers.add(assignee);
        }

        dto.setTaskPooledUsers(pooledUsers);

        dto.setTaskInstanceId(ENGINE_PREFIX + task.getId());
        dto.setDueDate(task.getDueDate());

        String taskTitleProp = qNameConverter.mapQNameToName(CiteckWorkflowModel.PROP_TASK_TITLE);
        dto.setTaskTitle((String) task.getVariable(taskTitleProp));

        dto.setWorkflowInstanceId(ENGINE_PREFIX + task.getProcessInstanceId());
        dto.setWorkflowDescription((String) task.getVariable(VAR_DESCRIPTION));

        return Optional.of(EventDtoFactory.toEventDto(dto));
    }

    private String getFormCustomOutcome(DelegateTask delegateTask) {
        String fullFormKey = FlowableUtils.getFullFormKey(delegateTask.getFormKey());
        return (String) delegateTask.getVariable(fullFormKey);
    }

    private String getTaskOutcome(DelegateTask delegateTask) {
        QName outcomeProperty = (QName) delegateTask.getVariable(VAR_OUTCOME_PROPERTY_NAME);
        if (outcomeProperty == null) {
            outcomeProperty = WorkflowModel.PROP_OUTCOME;
        }

        String taskOutcome = (String) delegateTask.getVariable(qNameConverter.mapQNameToName(outcomeProperty));
        if (taskOutcome == null) {
            taskOutcome = getFormCustomOutcome(delegateTask);
        }

        return taskOutcome;
    }

    private String getFormCustomComment(DelegateTask delegateTask) {
        String customComment = "";
        List<String> commentFieldIds = flowableCustomCommentService.getFieldIdsByProcessDefinitionId(
            delegateTask.getProcessDefinitionId()
        );
        for (String commentFieldId : commentFieldIds) {
            String comments = (String) taskService.getVariable(delegateTask.getId(), commentFieldId);
            if (comments != null) {
                customComment = comments;
            }
        }
        return customComment;
    }

    private String getTaskComment(DelegateTask delegateTask) {
        String taskComment = (String) delegateTask.getVariable(VAR_COMMENT);
        if (taskComment == null) {
            taskComment = getFormCustomComment(delegateTask);
        }

        return taskComment;
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
