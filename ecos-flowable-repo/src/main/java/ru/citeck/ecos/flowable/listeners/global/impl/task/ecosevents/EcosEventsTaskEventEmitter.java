package ru.citeck.ecos.flowable.listeners.global.impl.task.ecosevents;

import com.fasterxml.jackson.databind.node.ObjectNode;
import kotlin.Unit;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.alfresco.service.cmr.repository.NodeService;
import org.apache.commons.lang.StringUtils;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.task.service.delegate.BaseTaskListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.events2.EventsService;
import ru.citeck.ecos.events2.emitter.EventsEmitter;
import ru.citeck.ecos.flowable.services.FlowableProcessDefinitionService;
import ru.citeck.ecos.flowable.utils.FlowableListenerUtils;
import ru.citeck.ecos.records2.RecordRef;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class EcosEventsTaskEventEmitter {

    private static final String OUTCOME_FIELD = "outcome";
    private static final String COMMENT_FIELD = "comment";

    private final FlowableProcessDefinitionService processDefinitionService;
    private final EventsService eventsService;
    private final NodeService nodeService;

    private EventsEmitter<EcosTaskEvent> taskCompletedEmitter;
    private EventsEmitter<EcosTaskEvent> taskCreatedEmitter;
    private EventsEmitter<FlowElementTakeEvent> flowTakeEmitter;

    @PostConstruct
    public void init() {
        taskCompletedEmitter = eventsService.getEmitter(builder -> {
            builder.setSource("ecos-flowable");
            builder.setEventClass(EcosTaskEvent.class);
            builder.setEventType("bpmn-user-task-complete");
            return Unit.INSTANCE;
        });
        taskCreatedEmitter = eventsService.getEmitter(builder -> {
            builder.setSource("ecos-flowable");
            builder.setEventClass(EcosTaskEvent.class);
            builder.setEventType("bpmn-user-task-create");
            return Unit.INSTANCE;
        });
        flowTakeEmitter = eventsService.getEmitter(builder -> {
            builder.setSource("ecos-flowable");
            builder.setEventClass(FlowElementTakeEvent.class);
            builder.setEventType("bpmn-flow-element-take");
            return Unit.INSTANCE;
        });
    }

    public void emitTaskEvent(DelegateTask task) {
        if (BaseTaskListener.EVENTNAME_CREATE.equals(task.getEventName())) {
            taskCreatedEmitter.emit(createEvent(task));
        } else if (BaseTaskListener.EVENTNAME_COMPLETE.equals(task.getEventName())) {
            taskCompletedEmitter.emit(createEvent(task));
        }
    }

    private EcosTaskEvent createEvent(DelegateTask flowableTask) {

        String processDefId = flowableTask.getProcessDefinitionId();
        ProcessDefinition processDef = processDefinitionService.getProcessDefinitionById(processDefId);
        if (processDef == null) {
            throw new IllegalStateException(
                "Process definition is null. TaskId: " + flowableTask.getId()
                    + " name: " + flowableTask.getName()
                    + " executionId: " + flowableTask.getExecutionId()
                    + " procInstanceId: " + flowableTask.getProcessInstanceId()
                    + " procDefId: " + processDefId
            );
        }

        EcosTaskEvent event = new EcosTaskEvent();
        event.setTaskId(flowableTask.getId());
        event.setEngine("flowable");
        event.setAssignee(flowableTask.getAssignee());
        event.setProcDefId(processDef.getKey());
        event.setProcDefVersion(processDef.getVersion());
        event.setProcInstanceId(flowableTask.getProcessInstanceId());
        event.setElementDefId(flowableTask.getTaskDefinitionKey());
        event.setCreated(toInstantOrNull(flowableTask.getCreateTime()));
        event.setDueDate(toInstantOrNull(flowableTask.getDueDate()));
        event.setDescription(flowableTask.getDescription());
        event.setPriority(flowableTask.getPriority());
        event.setExecutionId(flowableTask.getExecutionId());

        String taskTitle = toStringOrNull(flowableTask.getVariable("cwf_taskTitle"));
        if (StringUtils.isBlank(taskTitle)) {
            taskTitle = flowableTask.getName();
        }
        event.setName(new MLText(taskTitle));
        event.setDocument(FlowableListenerUtils.getDocumentRecordRef(flowableTask, nodeService));

        event.setComment(toStringOrNull(flowableTask.getVariable(COMMENT_FIELD)));
        event.setOutcome(toStringOrNull(flowableTask.getVariable(OUTCOME_FIELD)));

        Object formInfo = flowableTask.getVariable("_formInfo");
        if (formInfo instanceof ObjectNode) {
            Object outcomeName = ((ObjectNode) formInfo).get("submitName");
            if (outcomeName instanceof ObjectNode) {
                Map<Locale, String> name = DataValue.create(outcomeName).asMap(Locale.class, String.class);
                event.setOutcomeName(new MLText(name));
            }
        }

        return event;
    }

    public void emitFlowElementTakeEvent(DelegateExecution execution) {

        String processDefId = execution.getProcessDefinitionId();
        ProcessDefinition processDef = processDefinitionService.getProcessDefinitionById(processDefId);
        FlowElement flowElement = execution.getCurrentFlowElement();

        if (processDef == null || flowElement == null) {
            throw new IllegalStateException(
                "Process definition or flowElement is null. ProcDefId: " + processDefId
                    + " flowId: " + Optional.ofNullable(flowElement).map(FlowElement::getId).orElse(null)
                    + " flowName: " + Optional.ofNullable(flowElement).map(FlowElement::getName).orElse(null)
                    + " executionId " + execution.getId()
            );
        }

        FlowElementTakeEvent event = new FlowElementTakeEvent();

        event.setExecutionId(execution.getId());
        event.setElementDefId(execution.getCurrentFlowElement().getId());
        event.setElementType(flowElement.getClass().getSimpleName());
        event.setEngine("flowable");

        event.setProcDefId(processDef.getKey());
        event.setProcDefVersion(processDef.getVersion());
        event.setProcInstanceId(execution.getProcessInstanceId());
        event.setDocument(FlowableListenerUtils.getDocumentRecordRef(execution, nodeService));

        flowTakeEmitter.emit(event);
    }

    private static Instant toInstantOrNull(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant();
    }

    @Nullable
    private static String toStringOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        return String.valueOf(value);
    }

    @Data
    private static class FlowElementTakeEvent {
        private String executionId;
        private String elementType;
        private String elementDefId;
        private String engine;
        private String procDefId;
        private int procDefVersion;
        private String procInstanceId;
        private RecordRef document;
    }

    @Data
    private static class EcosTaskEvent {
        private String taskId;
        private String engine;
        private String assignee;
        private String procDefId;
        private int procDefVersion;
        private String procInstanceId;
        private String elementDefId;
        private Instant created;
        private Instant dueDate;
        private String description;
        private int priority;
        private String executionId;
        private MLText name;
        private String comment;
        private String outcome;
        private MLText outcomeName;
        private RecordRef document;
    }
}
