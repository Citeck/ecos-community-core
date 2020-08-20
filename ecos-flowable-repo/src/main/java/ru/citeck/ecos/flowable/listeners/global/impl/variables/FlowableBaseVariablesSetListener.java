package ru.citeck.ecos.flowable.listeners.global.impl.variables;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.repo.admin.SysAdminParams;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.util.UrlUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.flowable.variable.FlowableScriptNode;
import ru.citeck.ecos.utils.UrlUtils;

/**
 * @author Roman Makarskiy
 * <p>
 * Listener fot adding base variables to execution.
 */
@Slf4j
@Component
public class FlowableBaseVariablesSetListener extends AbstractFlowableSaveToExecutionListener {

    private static final String VAR_DOCUMENT = "document";
    private static final String VAR_SHARE_URL = "shareUrl";
    private static final String VAR_WEB_URL = "webUrl";

    private final SysAdminParams sysAdminParams;
    private final UrlUtils urlUtils;

    @Autowired
    public FlowableBaseVariablesSetListener(SysAdminParams sysAdminParams, UrlUtils urlUtils) {
        this.sysAdminParams = sysAdminParams;
        this.urlUtils = urlUtils;
    }

    @Override
    public boolean saveIsRequired(NodeRef document) {
        return true;
    }

    @Override
    public void saveToExecution(String executionId, NodeRef document) {
        setDocumentVariable(executionId, document);
        setShareUrlVariable(executionId);
        setWebUrlVariable(executionId);
    }

    private void setDocumentVariable(String executionId, NodeRef document) {
        if (document != null) {
            FlowableScriptNode node = new FlowableScriptNode(document, serviceRegistry);
            setVariable(executionId, VAR_DOCUMENT, node);
        } else {
            setVariable(executionId, VAR_DOCUMENT, null);
        }
    }

    private void setShareUrlVariable(String executionId) {
        String shareUrl = UrlUtil.getShareUrl(sysAdminParams);
        setVariable(executionId, VAR_SHARE_URL, shareUrl);
    }

    private void setWebUrlVariable(String executionId) {
        setVariable(executionId, VAR_WEB_URL, urlUtils.getWebUrl());
    }
}
