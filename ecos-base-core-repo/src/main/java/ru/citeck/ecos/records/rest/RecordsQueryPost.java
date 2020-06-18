package ru.citeck.ecos.records.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.extensions.surf.util.I18NUtil;
import org.springframework.extensions.webscripts.*;
import ru.citeck.ecos.commons.utils.ExceptionUtils;
import ru.citeck.ecos.records2.QueryContext;
import ru.citeck.ecos.records2.RecordsServiceFactory;
import ru.citeck.ecos.records2.request.rest.QueryBody;
import ru.citeck.ecos.records2.request.rest.RestHandler;

import java.io.IOException;

/**
 * @author Pavel Simonov
 */
public class RecordsQueryPost extends AbstractWebScript {

    private RecordsRestUtils utils;
    private RestHandler restHandler;
    private RecordsServiceFactory recordsServiceFactory;

    @Override
    public void execute(WebScriptRequest req, WebScriptResponse res) throws IOException {
        QueryContext.withContext(recordsServiceFactory, () -> {
            QueryContext.getCurrent().setLocale(I18NUtil.getLocale());
            try {
                QueryBody request = utils.readBody(req, QueryBody.class);
                utils.writeResp(res, restHandler.queryRecords(request));
            } catch (IOException e) {
                ExceptionUtils.throwException(e);
            }
            return null;
        });
    }

    @Autowired
    public void setRecordsServiceFactory(RecordsServiceFactory recordsServiceFactory) {
        this.recordsServiceFactory = recordsServiceFactory;
    }

    @Autowired
    public void setRestQueryHandler(RestHandler restHandler) {
        this.restHandler = restHandler;
    }

    @Autowired
    public void setUtils(RecordsRestUtils utils) {
        this.utils = utils;
    }
}
