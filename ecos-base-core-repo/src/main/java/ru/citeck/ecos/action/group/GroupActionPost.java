package ru.citeck.ecos.action.group;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.repo.transaction.RetryingTransactionHelper;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.transaction.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.extensions.webscripts.*;
import ru.citeck.ecos.utils.json.mixin.EntityRefMixin;
import ru.citeck.ecos.utils.json.mixin.NodeRefMixIn;
import ru.citeck.ecos.utils.json.mixin.QNameMixIn;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.List;

@Slf4j
public class GroupActionPost extends AbstractWebScript {

    private ObjectMapper objectMapper = new ObjectMapper();
    private GroupActionService groupActionService;
    private TransactionService transactionService;

    @PostConstruct
    public void init() {
        objectMapper.addMixInAnnotations(NodeRef.class, NodeRefMixIn.class);
        objectMapper.addMixInAnnotations(QName.class, QNameMixIn.class);
        objectMapper.addMixInAnnotations(EntityRef.class, EntityRefMixin.class);
    }

    @Override
    public void execute(WebScriptRequest req, WebScriptResponse res) throws IOException {

        ActionData actionData = objectMapper.readValue(req.getContent().getContent(), ActionData.class);

        Response response = new Response();

        response.results = execute(actionData);

        res.setContentType(Format.JSON.mimetype() + ";charset=UTF-8");
        objectMapper.writeValue(res.getOutputStream(), response);
        res.setStatus(Status.STATUS_OK);
    }

    private ActionResults<?> execute(ActionData actionData) {
        RetryingTransactionHelper helper = transactionService.getRetryingTransactionHelper();
        return helper.doInTransaction(() -> {
            try {
                return groupActionService.execute(actionData.nodes, actionData.config);
            } catch (Exception e) {
                log.debug("Exception while group action execution", e);
                throw e;
            }
        }, false, true);
    }

    @Autowired
    public void setGroupActionService(GroupActionService groupActionService) {
        this.groupActionService = groupActionService;
    }

    @Autowired
    public void setTransactionService(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ActionData {
        public GroupActionConfig config;
        @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS,
            include = JsonTypeInfo.As.WRAPPER_OBJECT)
        public List<?> nodes;
    }

    public static class Response<T> {
        @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS,
            include = JsonTypeInfo.As.WRAPPER_OBJECT)
        public ActionResults<T> results;
    }
}
