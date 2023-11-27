package ru.citeck.ecos.rest;

import kotlin.Pair;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.node.NodeUtils;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.transaction.TransactionService;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.context.lib.auth.AuthRole;
import ru.citeck.ecos.webapp.api.web.executor.EcosWebExecutor;
import ru.citeck.ecos.webapp.api.web.executor.EcosWebExecutorReq;
import ru.citeck.ecos.webapp.api.web.executor.EcosWebExecutorResp;

@Component
public class ContentDownloadWebExecutor implements EcosWebExecutor {

    public static final String PATH = "/content/download";

    private final ContentService contentService;
    private final NodeService nodeService;
    private final TransactionService transactionService;

    @Autowired
    public ContentDownloadWebExecutor(ContentService contentService,
                                      NodeService nodeService,
                                      TransactionService transactionService) {
        this.contentService = contentService;
        this.nodeService = nodeService;
        this.transactionService = transactionService;
    }

    @Override
    @Secured(AuthRole.SYSTEM)
    public void execute(@NotNull EcosWebExecutorReq ecosWebExecutorReq, @NotNull EcosWebExecutorResp ecosWebExecutorResp) {

        NodeRef contentRef = ecosWebExecutorReq.getBodyReader().readDto(NodeRef.class);
        if (!NodeUtils.exists(contentRef, nodeService)) {
            return;
        }

        transactionService.getRetryingTransactionHelper().doInTransaction(() -> {
            ContentReader reader = contentService.getReader(contentRef, ContentModel.PROP_CONTENT);
            if (reader == null || !reader.exists()) {
                return null;
            }
            reader.getContent(ecosWebExecutorResp.getBodyWriter().getOutputStream());
            return null;
        }, true, true);
    }

    @NotNull
    @Override
    public Pair<Integer, Integer> getApiVersion() {
        return EcosWebExecutor.Companion.getDEFAULT_API_VERSION();
    }

    @NotNull
    @Override
    public String getPath() {
        return PATH;
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }
}
