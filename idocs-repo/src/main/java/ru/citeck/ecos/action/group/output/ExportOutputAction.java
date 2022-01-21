package ru.citeck.ecos.action.group.output;

import org.alfresco.service.cmr.repository.NodeRef;

/**
 * @param <T> type of output config
 */
public interface ExportOutputAction<T> {

    void execute(NodeRef outputFile, T config);

    void validate(T config);

    String getType();
}
