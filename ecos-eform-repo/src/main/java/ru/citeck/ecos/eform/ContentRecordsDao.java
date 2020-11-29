package ru.citeck.ecos.eform;

import ecos.com.google.common.io.ByteSource;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.GUID;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.eform.model.EcosEformFileModel;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.request.delete.RecordsDelResult;
import ru.citeck.ecos.records2.request.delete.RecordsDeletion;
import ru.citeck.ecos.records2.request.mutation.RecordsMutResult;
import ru.citeck.ecos.records2.request.mutation.RecordsMutation;
import ru.citeck.ecos.records2.source.dao.AbstractRecordsDao;
import ru.citeck.ecos.records2.source.dao.MutableRecordsDao;

@Component
@Slf4j
public class ContentRecordsDao extends AbstractRecordsDao implements MutableRecordsDao {

    public static final String ID = "content";
    private static final String PARAM_NAME_ID = "name";
    private static final String PARAM_FILE_NAME = "fileName";
    private static final String PARAM_MIMETYPE = "mimetype";
    private static final String PARAM_ENCODING = "encoding";
    private static final NodeRef ROOT_NODE_REF = new NodeRef("workspace://SpacesStore/eform-files-temp-root");

    @Autowired
    private NodeService nodeService;
    @Autowired
    private ContentService contentService;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public RecordsMutResult mutate(RecordsMutation recordsMutation) {
        RecordsMutResult result = new RecordsMutResult();

        for (RecordMeta record : recordsMutation.getRecords()) {
            DataValue value = record.getAttribute(ID);
            if (value == null || DataValue.NULL.equals(value)) {
                continue;
            }
            String name = record.getAttribute(PARAM_NAME_ID, "");
            String fileName = record.getAttribute(PARAM_FILE_NAME, "");
            String mimetype = record.getAttribute(PARAM_MIMETYPE, MimetypeMap.MIMETYPE_BINARY);
            String encoding = record.getAttribute(PARAM_ENCODING, StandardCharsets.UTF_8.name());
            try {
                NodeRef saved = saveFile(name, fileName, mimetype, encoding, value.binaryValue());
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

        ContentWriter writer = contentService.getWriter(createdTempFile, ContentModel.PROP_CONTENT, true);
        writer.setEncoding(encoding);
        writer.setMimetype(mimetype);
        writer.putContent(ByteSource.wrap(content).openStream());

        return createdTempFile;
    }
}
