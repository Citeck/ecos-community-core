package ru.citeck.ecos.flowable.listeners.global.impl.task.ecosevents;

import lombok.RequiredArgsConstructor;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.flowable.listeners.global.GlobalAllTaskListener;

@Component
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class EcosEventsBpmnTaskListener implements GlobalAllTaskListener {

    private final EcosEventsTaskEventEmitter emitter;

    @Override
    public void notify(DelegateTask delegateTask) {
        emitter.emitTaskEvent(delegateTask);
    }
}
