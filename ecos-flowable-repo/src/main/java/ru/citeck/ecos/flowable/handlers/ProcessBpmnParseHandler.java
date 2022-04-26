package ru.citeck.ecos.flowable.handlers;

import org.flowable.bpmn.model.BaseElement;
import org.flowable.bpmn.model.FlowableListener;
import org.flowable.bpmn.model.ImplementationType;
import org.flowable.bpmn.model.Process;
import org.flowable.engine.delegate.ExecutionListener;
import org.flowable.engine.impl.bpmn.parser.BpmnParse;
import org.flowable.engine.parse.BpmnParseHandler;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.flowable.listeners.global.GlobalEndExecutionListener;
import ru.citeck.ecos.flowable.listeners.global.GlobalStartExecutionListener;
import ru.citeck.ecos.flowable.listeners.global.GlobalTakeExecutionListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Process bpmn parse handler. Add global execution listeners to every processes
 */
@Component
public class ProcessBpmnParseHandler implements BpmnParseHandler, ApplicationContextAware {

    private ApplicationContext applicationContext;

    @Override
    public Collection<Class<? extends BaseElement>> getHandledTypes() {
        List<Class<? extends BaseElement>> result = new ArrayList<>();
        result.add(Process.class);
        return result;
    }

    @Override
    public void parse(BpmnParse bpmnParse, BaseElement baseElement) {
        Process process = (Process) baseElement;
        List<FlowableListener> listeners = process.getExecutionListeners();

        /* Start event */
        Collection<String> startExecutionListeners = getBeansNames(GlobalStartExecutionListener.class);
        for (String listener : startExecutionListeners) {
            listeners.add(createFlowableListener(listener, ExecutionListener.EVENTNAME_START));
        }

        /* Take event */
        Collection<String> takeExecutionListeners = getBeansNames(GlobalTakeExecutionListener.class);
        for (String listener : takeExecutionListeners) {
            listeners.add(createFlowableListener(listener, ExecutionListener.EVENTNAME_TAKE));
        }

        /* End event */
        Collection<String> endExecutionListeners = getBeansNames(GlobalEndExecutionListener.class);
        for (String listener : endExecutionListeners) {
            listeners.add(createFlowableListener(listener, ExecutionListener.EVENTNAME_END));
        }

        process.setExecutionListeners(listeners);
    }

    private List<String> getBeansNames(Class<?> beansClass) {
        return Arrays.asList(applicationContext.getBeanNamesForType(beansClass));
    }

    private FlowableListener createFlowableListener(String executionListener, String event) {
        FlowableListener result = new FlowableListener();
        result.setEvent(event);
        result.setImplementationType(ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION);
        result.setImplementation("${" + executionListener + "}");
        return result;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
