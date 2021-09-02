package ru.citeck.ecos.flowable.listeners.global.impl.variables;

import ru.citeck.ecos.records2.RecordRef;

/**
 * This interface provides methods to {@link AbstractFlowableSaveToExecutionListener}
 *
 * @author Roman Makarskiy
 */

public interface SaveToExecutionRecordRefProcessor {
    boolean saveIsRequired(RecordRef document);
    void saveToExecution(String executionId, RecordRef document);
    void setVariable(String executionId, String variableName, Object value);
}
