package ru.citeck.ecos.flowable.listeners;

import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import ru.citeck.ecos.events.EventConnection;
import ru.citeck.ecos.flowable.event.FlowableEventFactory;
import ru.citeck.ecos.flowable.listeners.global.GlobalAssignmentTaskListener;
import ru.citeck.ecos.flowable.listeners.global.GlobalCompleteTaskListener;
import ru.citeck.ecos.flowable.listeners.global.GlobalCreateTaskListener;
import ru.citeck.ecos.flowable.listeners.global.GlobalDeleteTaskListener;
import ru.citeck.ecos.utils.TransactionUtils;

public class TaskEventListener implements GlobalCreateTaskListener, GlobalAssignmentTaskListener,
    GlobalCompleteTaskListener, GlobalDeleteTaskListener {

    private final EventConnection eventConnection;
    private final FlowableEventFactory eventFactory;

    @Value("${event.task.create.emit.enabled}")
    private boolean eventTaskCreateEnabled;

    @Value("${event.task.assign.emit.enabled}")
    private boolean eventTaskAssignEnabled;

    @Value("${event.task.complete.emit.enabled}")
    private boolean eventTaskCompleteEnabled;

    @Value("${event.task.delete.emit.enabled}")
    private boolean eventTaskDeleteEnabled;

    @Value("${ecos.server.tenant.id}")
    private String TENANT_ID;

    @Autowired
    public TaskEventListener(EventConnection eventConnection, FlowableEventFactory eventFactory) {
        this.eventConnection = eventConnection;
        this.eventFactory = eventFactory;
    }

    @Override
    public void notify(DelegateTask delegateTask) {
        if (emitRequired(delegateTask)) {
            if (eventConnection == null) {
                throw new RuntimeException("Sending event is required, but connection to event server is not enabled. " +
                    "Check you configs.");
            }
            eventFactory.fromFlowableTask(delegateTask)
                .ifPresent(eventDTO -> TransactionUtils.doAfterCommit(() -> eventConnection.emit(eventDTO,
                    TENANT_ID)));
        }
    }

    @SuppressWarnings("Duplicates")
    private boolean emitRequired(DelegateTask task) {
        String eventName = task.getEventName();
        switch (eventName) {
            case TaskListener.EVENTNAME_CREATE:
                return eventTaskCreateEnabled;
            case TaskListener.EVENTNAME_ASSIGNMENT:
                return eventTaskAssignEnabled;
            case TaskListener.EVENTNAME_COMPLETE:
                return eventTaskCompleteEnabled;
            case TaskListener.EVENTNAME_DELETE:
                return eventTaskDeleteEnabled;
            default:
                return false;
        }
    }

}
