package ru.citeck.ecos.workflow.tasks;

import com.fasterxml.jackson.databind.node.ObjectNode;
import kotlin.Unit;
import lombok.Data;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.events2.EventsService;
import ru.citeck.ecos.events2.emitter.EventsEmitter;
import ru.citeck.ecos.records2.RecordRef;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

@Component
public class TaskErrorEventsEmitter implements TaskEventsEmitter {

    @Autowired
    private EventsService eventsService;

    private EventsEmitter<EcosTaskErrorEvent> taskCompletedWithErrorEmitter;

    @PostConstruct
    public void init() {
        taskCompletedWithErrorEmitter = eventsService.getEmitter(builder -> {
            builder.setSource("ecos-tasks");
            builder.setEventClass(EcosTaskErrorEvent.class);
            builder.setEventType("bpmn-user-task-complete-error");
            return Unit.INSTANCE;
        });
    }

    @Override
    public void emitEndTaskErrorEvent(TaskInfo taskInfo, Throwable cause) {
        EcosTaskErrorEvent taskEvent = new EcosTaskErrorEvent();
        taskEvent.setErrorMessage(cause.getMessage());
        taskEvent.setErrorStackTrace(ExceptionUtils.getStackTrace(cause));
        taskEvent.setAssignee(taskInfo.getAssignee());
        taskEvent.setTaskId(taskInfo.getId());
        taskEvent.setName(new MLText(taskInfo.getTitle()));
        taskEvent.setEngine("flowable");
        if (taskInfo.getWorkflow() != null && taskInfo.getWorkflow().getDefinition() != null) {
            String fullDefId = taskInfo.getWorkflow().getDefinition().getId();
            String processDefId = fullDefId.substring(fullDefId.indexOf("$") + 1);
            taskEvent.setProcDefId(processDefId);
            String versionValue = taskInfo.getWorkflow().getDefinition().getVersion();
            if (StringUtils.isNotBlank(versionValue)) {
                try {
                    taskEvent.setProcDeploymentVersion(Integer.valueOf(versionValue).intValue());
                } catch (IllegalArgumentException e) {
                }
            }
        }
        taskEvent.setCreated(toInstantOrNull(taskInfo.getAttribute("cm_created")));
        taskEvent.setDueDate(toInstantOrNull(taskInfo.getAttribute("bpm_dueDate")));
        taskEvent.setDescription(taskInfo.getDescription());
        taskEvent.setPriority(toIntegerOrNull(taskInfo.getAttribute("bpm_priority")));
        String taskTitle = toStringOrNull(taskInfo.getAttribute("cwf_taskTitle"));
        if (StringUtils.isBlank(taskTitle)) {
            taskTitle = taskInfo.getTitle();
        }
        taskEvent.setName(new MLText(taskTitle));
        taskEvent.setDocument(taskInfo.getDocument());
        taskEvent.setComment(toStringOrNull(taskInfo.getAttribute("comment")));
        taskEvent.setOutcome(toStringOrNull(taskInfo.getAttribute("outcome")));
        taskEvent.setCompletedViaMail(toBoolean(taskInfo.getAttribute("isCompletedViaMail")));
        Object formInfo = taskInfo.getAttribute("_formInfo");
        if (formInfo instanceof ObjectNode) {
            Object outcomeName = ((ObjectNode) formInfo).get("submitName");
            if (outcomeName instanceof ObjectNode) {
                Map<Locale, String> name =
                    DataValue.create(outcomeName).asMap(Locale.class, String.class);
                taskEvent.setOutcomeName(new MLText(name));
            }
        }
        taskCompletedWithErrorEmitter.emit(taskEvent);
    }

    private static Instant toInstantOrNull(Object date) {
        if (date == null || !(date instanceof Date)) {
            return null;
        }
        return ((Date) date).toInstant();
    }

    private static Integer toIntegerOrNull(Object value) {
        if (value == null || !(value instanceof Integer)) {
            return null;
        }
        return (Integer) value;
    }

    private static boolean toBoolean(Object value) {
        if (value == null) {
            return false;
        }
        return Boolean.valueOf(value.toString());
    }

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
    private static class EcosTaskErrorEvent {
        private String taskId;
        private String engine;
        private String assignee;
        private String procDefId;
        private int procDeploymentVersion;
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
        private boolean isCompletedViaMail;
        private String errorMessage;
        private String errorStackTrace;
    }
}
