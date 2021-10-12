package ru.citeck.ecos.flowable.listeners.global.impl.variables;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.NodeService;
import org.apache.commons.lang.StringUtils;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.beans.factory.annotation.Autowired;
import ru.citeck.ecos.flowable.listeners.global.GlobalAllTaskListener;
import ru.citeck.ecos.flowable.listeners.global.GlobalEndExecutionListener;
import ru.citeck.ecos.flowable.listeners.global.GlobalStartExecutionListener;
import ru.citeck.ecos.flowable.listeners.global.GlobalTakeExecutionListener;
import ru.citeck.ecos.flowable.utils.FlowableListenerUtils;
import ru.citeck.ecos.records2.RecordRef;

/**
 * This class is flowable task/execution listener, which provide fill some data to execution variables.
 * You can extends this class and override {@code saveIsRequired}, {@code saveToExecution} methods for save
 * some data in process execution
 *
 * @author Roman Makarskiy
 */
@Slf4j
public abstract class AbstractFlowableSaveToExecutionRecordRefListener implements GlobalStartExecutionListener, GlobalEndExecutionListener,
        GlobalTakeExecutionListener, GlobalAllTaskListener, SaveToExecutionRecordRefProcessor {

    @Autowired
    protected NodeService nodeService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    protected ServiceRegistry serviceRegistry;

    @Override
    public void notify(DelegateExecution execution) {
        RecordRef document = FlowableListenerUtils.getDocumentRecordRef(execution, nodeService);
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

        RecordRef document = FlowableListenerUtils.getDocumentRecordRef(delegateTask, nodeService);
        if (saveIsRequired(document)) {
            saveToExecution(executionId, document);
        }
    }

    @Override
    public void setVariable(String executionId, String variableName, Object value) {
        if (log.isDebugEnabled()) {
            log.debug("Set variable: <" + variableName + "> value: <" + value + "> executionId: <" + executionId + ">");
        }
        runtimeService.setVariable(executionId, variableName, value);
    }
}
