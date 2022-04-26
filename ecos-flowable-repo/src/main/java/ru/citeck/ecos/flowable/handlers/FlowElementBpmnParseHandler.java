package ru.citeck.ecos.flowable.handlers;

import org.flowable.bpmn.model.*;
import org.flowable.engine.delegate.ExecutionListener;
import org.flowable.engine.impl.bpmn.parser.BpmnParse;
import org.flowable.engine.parse.BpmnParseHandler;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.flowable.listeners.global.GlobalFlowElementStartListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@Component
public class FlowElementBpmnParseHandler implements BpmnParseHandler, ApplicationContextAware {

    private ApplicationContext applicationContext;

    @Override
    public Collection<Class<? extends BaseElement>> getHandledTypes() {

        List<Class<? extends FlowElement>> result = new ArrayList<>();

        result.add(SequenceFlow.class);

        result.add(UserTask.class);
        result.add(ManualTask.class);
        result.add(SendTask.class);
        result.add(ReceiveTask.class);
        result.add(ScriptTask.class);
        result.add(ServiceTask.class);

        result.add(ExclusiveGateway.class);
        result.add(InclusiveGateway.class);
        result.add(ParallelGateway.class);

        result.add(StartEvent.class);
        result.add(EndEvent.class);
        result.add(BoundaryEvent.class);
        result.add(IntermediateCatchEvent.class);

        return new ArrayList<>(result);
    }

    @Override
    public void parse(BpmnParse bpmnParse, BaseElement baseElement) {
        if (baseElement instanceof FlowElement) {
            parseFlowElement((FlowElement) baseElement);
        }
    }

    private void parseFlowElement(FlowElement flowElement) {

        List<FlowableListener> listeners = flowElement.getExecutionListeners();
        Collection<String> takeExecutionListeners = getBeansNames(GlobalFlowElementStartListener.class);
        for (String listener : takeExecutionListeners) {
            listeners.add(createFlowableListener(listener, ExecutionListener.EVENTNAME_START));
        }
        flowElement.setExecutionListeners(listeners);
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
