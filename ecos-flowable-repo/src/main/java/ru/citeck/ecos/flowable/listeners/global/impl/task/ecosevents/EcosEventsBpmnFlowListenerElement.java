package ru.citeck.ecos.flowable.listeners.global.impl.task.ecosevents;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang.StringUtils;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.flowable.listeners.global.GlobalFlowElementTakeListener;

@Component
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class EcosEventsBpmnFlowListenerElement implements GlobalFlowElementTakeListener {

    private final EcosEventsTaskEventEmitter emitter;

    @Override
    public void notify(DelegateExecution execution) {
        FlowElement flow = execution.getCurrentFlowElement();
        if (flow != null && StringUtils.isNotBlank(flow.getId())) {
            emitter.emitFlowElementTakeEvent(execution);
        }
    }
}
