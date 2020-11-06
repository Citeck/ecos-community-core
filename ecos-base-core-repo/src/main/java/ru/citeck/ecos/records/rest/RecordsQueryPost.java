package ru.citeck.ecos.records.rest;

import ecos.com.fasterxml.jackson210.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.extensions.webscripts.*;
import ru.citeck.ecos.commons.utils.ExceptionUtils;
import ru.citeck.ecos.records3.RecordsServiceFactory;
import ru.citeck.ecos.records3.record.request.RequestContext;
import ru.citeck.ecos.records3.rest.RestHandlerAdapter;

import java.io.IOException;

/**
 * @author Pavel Simonov
 */
public class RecordsQueryPost extends AbstractWebScript {

    private RecordsRestUtils utils;
    private RecordsServiceFactory recordsServiceFactory;
    private RestHandlerAdapter restHandlerAdapter;

    @Override
    public void execute(WebScriptRequest req, WebScriptResponse res) throws IOException {
        RequestContext.doWithCtxJ(recordsServiceFactory, utils::prepareCtxData, ctx -> {
            try {
                JsonNode queryBody = utils.readBody(req, JsonNode.class);
                Object result = restHandlerAdapter.queryRecords(queryBody);
                utils.writeResp(res, result);
            } catch (IOException e) {
                ExceptionUtils.throwException(e);
            }
            return null;
        });
    }

    @Autowired
    public void setRecordsServiceFactory(RecordsServiceFactory recordsServiceFactory) {
        this.recordsServiceFactory = recordsServiceFactory;
        restHandlerAdapter = recordsServiceFactory.getRestHandlerAdapter();
    }

    @Autowired
    public void setUtils(RecordsRestUtils utils) {
        this.utils = utils;
    }
}
