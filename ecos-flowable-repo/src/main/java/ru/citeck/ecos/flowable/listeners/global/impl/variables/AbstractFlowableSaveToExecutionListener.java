package ru.citeck.ecos.flowable.listeners.global.impl.variables;

import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.beans.factory.annotation.Autowired;
import ru.citeck.ecos.flowable.listeners.global.GlobalAllTaskListener;
import ru.citeck.ecos.flowable.listeners.global.GlobalEndExecutionListener;
import ru.citeck.ecos.flowable.listeners.global.GlobalStartExecutionListener;
import ru.citeck.ecos.flowable.listeners.global.GlobalTakeExecutionListener;
import ru.citeck.ecos.flowable.utils.FlowableListenerUtils;

/**
 * This class is flowable task/execution listener, which provide fill some data to execution variables.
 * You can extends this class and override {@code saveIsRequired}, {@code saveToExecution} methods for save
 * some data in process execution
 *
 * @author Roman Makarskiy
 */
public abstract class AbstractFlowableSaveToExecutionListener implements GlobalStartExecutionListener, GlobalEndExecutionListener,
        GlobalTakeExecutionListener, GlobalAllTaskListener, SaveToExecutionProcessor {

    private static final Log logger = LogFactory.getLog(AbstractFlowableSaveToExecutionListener.class);

    @Autowired
    protected NodeService nodeService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    protected ServiceRegistry serviceRegistry;

    @Override
    public void notify(DelegateExecution execution) {
        NodeRef document = FlowableListenerUtils.getDocument(execution, nodeService);
        if (saveIsRequired(document)) {
            saveToExecution(execution.getId(), document);
        }
    }

    @Override
    public void notify(DelegateTask delegateTask) {
        if (delegateTask == null) {
            return;
        }

        String executionId = delegateTask.getExecutionId();
        if (StringUtils.isBlank(executionId)) {
            return;
        }

        NodeRef document = FlowableListenerUtils.getDocument(delegateTask, nodeService);
        if (saveIsRequired(document)) {
            saveToExecution(executionId, document);
        }
    }

    @Override
    public void setVariable(String executionId, String variableName, Object value) {
        if (logger.isDebugEnabled()) {
            logger.debug("Set variable: <" + variableName + "> value: <" + value + "> executionId: <" + executionId
                    + ">");
        }
        runtimeService.setVariable(executionId, variableName, value);
    }
}
