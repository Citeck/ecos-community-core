package ru.citeck.ecos.workflow.listeners;

import lombok.extern.slf4j.Slf4j;
import org.activiti.engine.delegate.DelegateTask;
import org.activiti.engine.delegate.TaskListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import ru.citeck.ecos.events.EventConnection;
import ru.citeck.ecos.stream.event.EventFactory;
import ru.citeck.ecos.utils.TransactionUtils;

/**
 * @author Roman Makarskiy
 */
@Slf4j
public class TaskEventListener extends AbstractTaskListener {

    private final EventConnection eventConnection;
    private final EventFactory eventFactory;

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
    public TaskEventListener(EventConnection eventConnection, EventFactory eventFactory) {
        this.eventConnection = eventConnection;
        this.eventFactory = eventFactory;
    }

    @Override
    protected void notifyImpl(DelegateTask task) {
        if (emitRequired(task)) {
            if (eventConnection == null) {
                throw new RuntimeException("Sending event is required, but connection to event server is not enabled. " +
                        "Check you configs.");
            }
            eventFactory.fromActivitiTask(task)
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
