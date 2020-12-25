package ru.citeck.ecos.eform;

import com.glaforge.i18n.io.CharsetToolkit;
import ecos.com.google.common.io.ByteSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.MimetypeService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.GUID;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.eform.model.EcosEformFileModel;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.request.delete.RecordsDelResult;
import ru.citeck.ecos.records2.request.delete.RecordsDeletion;
import ru.citeck.ecos.records2.request.mutation.RecordsMutResult;
import ru.citeck.ecos.records2.request.mutation.RecordsMutation;
import ru.citeck.ecos.records2.source.dao.MutableRecordsDao;
import ru.citeck.ecos.records3.record.dao.RecordsDao;

@Component
@Slf4j
public class TempContentRecordsDao implements RecordsDao, MutableRecordsDao {

    public static final String ID = "tempContent";

    private static final NodeRef ROOT_NODE_REF = new NodeRef("workspace://SpacesStore/eform-files-temp-root");
    private static final String PARAM_NAME = "name";
    private static final String PARAM_FILE_NAME = "fileName";
    private static final String PARAM_MIMETYPE = "mimetype";
    private static final String PARAM_ENCODING = "encoding";
    private static final String PARAM_CONTENT = "content";

    @Autowired
    private NodeService nodeService;
    @Autowired
    private ContentService contentService;
    @Autowired
    private MimetypeService mimetypeService;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public RecordsMutResult mutate(RecordsMutation recordsMutation) {
        RecordsMutResult result = new RecordsMutResult();

        for (RecordMeta record : recordsMutation.getRecords()) {
            String name = record.getAttribute(PARAM_NAME, "");
            String fileName = record.getAttribute(PARAM_FILE_NAME, "");
            DataValue value = record.getAttribute(PARAM_CONTENT);
            if (value == null || DataValue.NULL.equals(value)) {
                log.info("Skip saving temp content with empty data: " + fileName);
                continue;
            }
            DataValue mimetype = record.getAttribute(PARAM_MIMETYPE);
            String encoding = record.getAttribute(PARAM_ENCODING, StandardCharsets.UTF_8.name());
            try {
                NodeRef saved = saveFile(name, fileName, mimetype.isNotNull() ? mimetype.asText() : null, encoding, value.binaryValue());
                result.addRecord(new RecordMeta(saved.toString()));
            } catch (IOException e) {
                log.error("Can't saved temp content: " + fileName, e);
            }
        }
        return result;
    }

    @Override
    public RecordsDelResult delete(RecordsDeletion recordsDeletion) {
        throw new UnsupportedOperationException();
    }

    private NodeRef saveFile(String name, String fileName, String mimetype, String encoding, byte[] content) throws IOException {
        Map<QName, Serializable> props = new HashMap<>();
        props.put(EcosEformFileModel.PROP_TEMP_FILE_ID, name);
        if (StringUtils.isNotBlank(fileName)) {
            props.put(ContentModel.PROP_NAME, fileName);
        }

        NodeRef createdTempFile = nodeService.createNode(
                ROOT_NODE_REF,
                ContentModel.ASSOC_CHILDREN,
                QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, GUID.generate()),
                EcosEformFileModel.TYPE_TEMP_FILE,
                props).getChildRef();

        if (StringUtils.isBlank(mimetype)) {
            String extension = FilenameUtils.getExtension(fileName);
            mimetype = mimetypeService.getMimetype(extension);
            if (StringUtils.isBlank(mimetype)) {
                mimetype = MimetypeMap.MIMETYPE_BINARY;
            }
        }

        if (StringUtils.isBlank(encoding)) {
            encoding = guessEncoding(content, mimetype);
        }

        InputStream contentStream = ByteSource.wrap(content).openStream();
        ContentWriter writer = contentService.getWriter(createdTempFile, ContentModel.PROP_CONTENT, true);
        writer.setEncoding(encoding);
        writer.setMimetype(mimetype);
        writer.putContent(contentStream);

        return createdTempFile;
    }

    private String guessEncoding(byte[] content, String mimetype) {
        Charset defaultEncoding = getDefaultEncoding(mimetype);
        try {
            CharsetToolkit charsetToolkit = new CharsetToolkit(content, defaultEncoding);
            return charsetToolkit.guessEncoding().name();
        } catch (Exception ignored) {
            return defaultEncoding.name();
        }
    }

    private Charset getDefaultEncoding(String mimetype) {
        if (MediaType.TEXT_XML_VALUE.equalsIgnoreCase(mimetype)) {
            return Charset.forName("windows-1251");
        }
        return StandardCharsets.UTF_8;
    }

}
