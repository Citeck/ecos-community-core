package ru.citeck.ecos.records.rest;

import org.springframework.extensions.webscripts.*;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.records3.record.request.RequestCtxData;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;

/**
 * @author Pavel Simonov
 */
@Component
public class RecordsRestUtils {

    void prepareCtxData(RequestCtxData.Builder builder) {
    }

    <T> T readBody(WebScriptRequest req, Class<T> type) throws IOException {
        return Json.getMapper().read(req.getContent().getContent(), type);
    }

    <T> void writeRespRecords(WebScriptResponse res,
                              T result,
                              Function<T, List<?>> getRecordList,
                              boolean isSingleRecord) throws IOException {

        if (isSingleRecord) {
            List<?> records = getRecordList.apply(result);
            if (records.isEmpty()) {
                throw new WebScriptException(Status.STATUS_INTERNAL_SERVER_ERROR, "Records list is empty");
            }
            writeResp(res, records.get(0));
        } else {
            writeResp(res, result);
        }
    }

    void writeResp(WebScriptResponse res, Object result) throws IOException {
        res.setContentType(Format.JSON.mimetype() + ";charset=UTF-8");
        Json.getMapper().write(res.getOutputStream(), result);
        res.setStatus(Status.STATUS_OK);
    }
}
