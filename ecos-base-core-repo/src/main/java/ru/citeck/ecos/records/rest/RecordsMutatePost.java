package ru.citeck.ecos.records.rest;

import ecos.com.fasterxml.jackson210.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.extensions.webscripts.*;
import ru.citeck.ecos.records3.rest.RestHandlerAdapter;

import java.io.IOException;

/**
 * @author Pavel Simonov
 */
@Slf4j
public class RecordsMutatePost extends AbstractWebScript {

    private RecordsRestUtils utils;
    private RestHandlerAdapter restHandlerAdapter;

    @Override
    public void execute(WebScriptRequest req, WebScriptResponse res) throws IOException {
        utils.doWithRequestContext(() -> {
            utils.writeResp(res, restHandlerAdapter.mutateRecords(utils.readBody(req, JsonNode.class)));
            return null;
        });
    }

    @Autowired
    public void setRestHandlerAdapter(RestHandlerAdapter restHandlerAdapter) {
        this.restHandlerAdapter = restHandlerAdapter;
    }

    @Autowired
    public void setUtils(RecordsRestUtils utils) {
        this.utils = utils;
    }
}
