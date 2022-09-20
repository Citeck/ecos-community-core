package ru.citeck.ecos.workflow.tasks;

public interface TaskEventsEmitter {
    void emitEndTaskErrorEvent(TaskInfo taskInfo, Throwable cause);
}
