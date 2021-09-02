package ru.citeck.ecos.flowable.listeners.global.impl.variables;

import lombok.Data;
import org.alfresco.service.cmr.repository.NodeRef;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import ru.citeck.ecos.model.lib.status.constants.StatusConstants;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records3.RecordsService;
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName;
import ru.citeck.ecos.utils.NodeUtils;

/**
 * This class is flowable task/execution listener, which fills case status, case status names before to execution variable.
 *
 * @author Roman Makarskiy
 */
public class FlowableCaseStatusSetListener extends AbstractFlowableSaveToExecutionRecordRefListener {

    private static final Log logger = LogFactory.getLog(FlowableCaseStatusSetListener.class);

    private static final String VAR_KEY_CASE_STATUS = "case_status";
    private static final String VAR_KEY_CASE_STATUS_BEFORE = "case_status_before";

    @Autowired
    private RecordsService recordsService;

    @Override
    public boolean saveIsRequired(RecordRef document) {
        if (RecordRef.isEmpty(document)) {
            return false;
        }
        if (document.getId().contains(NodeUtils.WORKSPACE_PREFIX)) {
            return nodeService.exists(new NodeRef(document.getId()));
        }
        return true;
    }

    @Override
    public void saveToExecution(String executionId, RecordRef document) {

        StatusProps statusAtts = recordsService.getAtts(document, StatusProps.class);

        if (logger.isDebugEnabled()) {
            logger.debug("Set case status name variable: " +
                "<" + VAR_KEY_CASE_STATUS + "> value: <" + statusAtts.getStatus() + ">");
            logger.debug("Set case status before name variable: <" + VAR_KEY_CASE_STATUS_BEFORE + "> value: <"
                    + statusAtts.getStatusBefore() + ">");
        }

        setVariable(executionId, VAR_KEY_CASE_STATUS, statusAtts.getStatus());
        setVariable(executionId, VAR_KEY_CASE_STATUS_BEFORE, statusAtts.getStatusBefore());
    }

    @Data
    static class StatusProps {
        @AttName(StatusConstants.ATT_STATUS_STR)
        private String status;
        @AttName("icase:caseStatusBeforeAssoc.cm:name")
        private String statusBefore;
    }
}
