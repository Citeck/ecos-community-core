package ru.citeck.ecos.webscripts.zip;

import ecos.com.google.gson.JsonArray;
import ecos.com.google.gson.JsonObject;
import ecos.com.google.gson.JsonParser;
import org.apache.commons.compress.utils.IOUtils;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.repo.content.MimetypeMap;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.extensions.webscripts.*;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.server.utils.Utils;
import ru.citeck.ecos.service.zip.DownloadDocumentsZipService;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class DownloadZipWebscript extends AbstractWebScript {

    private final DownloadDocumentsZipService downloadDocumentsZipService;


    @Autowired
    DownloadZipWebscript(DownloadDocumentsZipService downloadDocumentsZipService) {
        this.downloadDocumentsZipService = downloadDocumentsZipService;
    }


    @Override
    public void execute(WebScriptRequest webScriptRequest, WebScriptResponse webScriptResponse) throws IOException {
        Map<String, String> templateVars = webScriptRequest.getServiceMatch().getTemplateVars();
        if (templateVars == null) {
            String error = "No parameters supplied";
            throw new WebScriptException(Status.STATUS_BAD_REQUEST, error);
        }

        if (isNotJSON(webScriptRequest)) {
            throw new WebScriptException(Status.STATUS_BAD_REQUEST, "No json provided");
        }

        List<RecordRef> documentsRef = getDocumentsRef(webScriptRequest);
        downloadDocumentsZipService.getZip(documentsRef, webScriptResponse.getOutputStream());
        configResponse(webScriptRequest, webScriptResponse);
        outputZip(webScriptResponse);
    }

    private void configResponse(WebScriptRequest webScriptRequest, WebScriptResponse webScriptResponse) throws UnsupportedEncodingException {
        webScriptResponse.setContentType("application/zip");
        webScriptResponse.setContentEncoding("zip");
        webScriptResponse.setHeader("Content-Disposition",
            Utils.encodeContentDispositionForDownload(webScriptRequest, "documents.zip", "", true));
    }

    private void outputZip(WebScriptResponse webScriptResponse) throws IOException {
        webScriptResponse.getOutputStream().flush();
        IOUtils.closeQuietly(webScriptResponse.getOutputStream());
    }

    private boolean isNotJSON(WebScriptRequest webScriptRequest) {
        String contentType = webScriptRequest.getContentType();
        if (contentType != null && contentType.indexOf(';') != -1) {
            contentType = contentType.substring(0, contentType.indexOf(';'));
        }
        return !MimetypeMap.MIMETYPE_JSON.equals(contentType);
    }

    private List<RecordRef> getDocumentsRef(WebScriptRequest webScriptRequest) {
        List<RecordRef> documentsRef;
        try {
            documentsRef = getRecordRefsFromJSON(webScriptRequest);
        } catch (IOException io) {
            throw new WebScriptException(Status.STATUS_INTERNAL_SERVER_ERROR, "Unexpected IOException", io);
        } catch (org.json.simple.parser.ParseException je) {
            throw new WebScriptException(Status.STATUS_BAD_REQUEST, "Unexpected ParseException", je);
        }

        if (documentsRef.size() < 1) {
            throw new WebScriptException(Status.STATUS_BAD_REQUEST, "No documentsRef provided");
        }
        return documentsRef;
    }

    private List<RecordRef> getRecordRefsFromJSON(WebScriptRequest webScriptRequest)
        throws IOException, ParseException {
        JsonParser jsonParser = new JsonParser();
        JsonObject json = (JsonObject) jsonParser.parse(webScriptRequest.getContent().getContent());
        JsonArray documentsRef = (JsonArray) json.get("documentsRef");

        List<RecordRef> recordRefs = new ArrayList<>();
        documentsRef.forEach(document -> {
            RecordRef recordRef = RecordRef.valueOf(document.getAsString());
            recordRefs.add(recordRef);
        });
        return recordRefs;
    }
}
