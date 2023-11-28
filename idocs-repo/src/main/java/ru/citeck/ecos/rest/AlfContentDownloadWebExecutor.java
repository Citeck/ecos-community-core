package ru.citeck.ecos.rest;

import kotlin.Pair;
import lombok.Data;
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
import ru.citeck.ecos.webapp.api.entity.EntityRef;
import ru.citeck.ecos.webapp.api.web.executor.EcosWebExecutor;
import ru.citeck.ecos.webapp.api.web.executor.EcosWebExecutorReq;
import ru.citeck.ecos.webapp.api.web.executor.EcosWebExecutorResp;

@Component
public class AlfContentDownloadWebExecutor implements EcosWebExecutor {

    private static final String PATH = "/content/download";

    private final ContentService contentService;
    private final NodeService nodeService;
    private final TransactionService transactionService;

    @Autowired
    public AlfContentDownloadWebExecutor(ContentService contentService,
                                         NodeService nodeService,
                                         TransactionService transactionService) {
        this.contentService = contentService;
        this.nodeService = nodeService;
        this.transactionService = transactionService;
    }

    @Override
    @Secured(AuthRole.SYSTEM)
    public void execute(@NotNull EcosWebExecutorReq ecosWebExecutorReq, @NotNull EcosWebExecutorResp ecosWebExecutorResp) {

        Body requestBody = ecosWebExecutorReq.getBodyReader().readDto(Body.class);
        if (EntityRef.isEmpty(requestBody.entityRef)) {
            throw new IllegalArgumentException("Request entity ref must be not empty");
        }

        String contentRefStr = requestBody.entityRef.getLocalId();
        NodeRef contentRef = NodeRef.isNodeRef(contentRefStr) ? new NodeRef(contentRefStr) : null;
        if (!NodeUtils.exists(contentRef, nodeService)) {
            throw new IllegalArgumentException("NodeRef " + contentRefStr + " does not exist");
        }

        ContentReader reader = transactionService.getRetryingTransactionHelper().doInTransaction(() ->
            contentService.getReader(contentRef, ContentModel.PROP_CONTENT), true, true);
        if (reader == null || !reader.exists()) {
            throw new RuntimeException("NodeRef " + contentRefStr + " has no content");
        }

        reader.getContent(ecosWebExecutorResp.getBodyWriter().getOutputStream());
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

    @Data
    public static class Body {
        private EntityRef entityRef;
        private String attribute = "";
        private Integer index = 0;
    }
}
