package ru.citeck.ecos.webscripts.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.utils.NewUIUtils;

import java.io.IOException;

@Slf4j
public class NewUIInfoGet extends AbstractWebScript {

    private static final String RECORD_REF_PARAM = "recordRef";

    private NewUIUtils newUIUtils;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void execute(WebScriptRequest req, WebScriptResponse res) throws IOException {

        String recordRef = req.getParameter(RECORD_REF_PARAM);

        Resp resp = new Resp();
        try {
            resp.isNewUIEnabled = newUIUtils.isNewUIEnabled();
            resp.newUIRedirectUrl = newUIUtils.getNewUIRedirectUrl();

            if (StringUtils.isNotBlank(recordRef)) {
                resp.recordUIType = newUIUtils.getUITypeForRecordAndUser(RecordRef.valueOf(recordRef));
                resp.isOldCardDetailsRequired = resp.recordUIType.equals(NewUIUtils.UI_TYPE_SHARE);
            }
        } catch (Exception e) {
            log.error("Exception", e);
        }

        objectMapper.writeValue(res.getOutputStream(), resp);
        res.setStatus(Status.STATUS_OK);
    }

    @Autowired
    public void setNewUIUtils(NewUIUtils newUIUtils) {
        this.newUIUtils = newUIUtils;
    }

    @Data
    public static class Resp {

        private boolean isNewUIEnabled;
        private boolean isOldCardDetailsRequired;
        private String newUIRedirectUrl;
        private String recordUIType;
    }
}
