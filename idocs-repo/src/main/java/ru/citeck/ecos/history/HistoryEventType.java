package ru.citeck.ecos.history;

public interface HistoryEventType {
    String TASK_COMPLETE = "task.complete";
    String TASK_CREATE = "task.create";
    String TASK_ASSIGN = "task.assign";
    String NODE_UPDATED = "node.updated";
    String NODE_CREATED = "node.created";
    String ASSOC_ADDED = "assoc.added";
    String EMAIL_SENT = "email.sent";
}
