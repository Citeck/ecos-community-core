package ru.citeck.ecos.eform.webscripts;

import lombok.extern.log4j.Log4j;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.GUID;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.extensions.surf.util.Content;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.DeclarativeWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.servlet.FormData;
import ru.citeck.ecos.eform.model.EcosEformFileModel;
import ru.citeck.ecos.eform.service.TempFilesParentComponent;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Allow to process post request from file control on eform. This request save file on temp <br>
 * container {@link FileEformPost#ROOT_NODE_REF}.
 *
 * @author Roman Makarskiy
 */
@Log4j
public class FileEformPost extends DeclarativeWebScript {

    private static final String PARAM_NAME_ID = "name";
    private static final String PARAM_FILE = "file";
    private static final String MODEL_NODE_REF = "nodeRef";

    private NodeService nodeService;
    private ContentService contentService;
    private TempFilesParentComponent tempFilesParentComponent;

    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache) {
        String fileNameId = req.getParameter(PARAM_NAME_ID);
        if (StringUtils.isBlank(fileNameId)) {
            status.setCode(Status.STATUS_BAD_REQUEST, "Parameter '" + PARAM_NAME_ID + "' should be set");
            return null;
        }

        File file = new File(req);
        if (file.isInvalid()) {
            status.setCode(Status.STATUS_BAD_REQUEST, "Parameter '" + PARAM_FILE + "' should be set");
            return null;
        }

        NodeRef createdTempFile = saveFile(fileNameId, file);

        if (log.isDebugEnabled()) {
            log.debug("Save temp file: " + file + " ----> " + createdTempFile);
        }

        Map<String, Object> result = new HashMap<>();
        result.put(MODEL_NODE_REF, createdTempFile.toString());
        return result;
    }

    private NodeRef saveFile(String name, File file) {
        Map<QName, Serializable> props = new HashMap<>(2);
        props.put(EcosEformFileModel.PROP_TEMP_FILE_ID, name);
        props.put(ContentModel.PROP_NAME, file.fileName);

        NodeRef createdTempFile = nodeService.createNode(
                tempFilesParentComponent.getOrCreateParentForNewTempFile(),
                ContentModel.ASSOC_CHILDREN,
                QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, GUID.generate()),
                EcosEformFileModel.TYPE_TEMP_FILE,
                props).getChildRef();

        ContentWriter writer = contentService.getWriter(createdTempFile, ContentModel.PROP_CONTENT, true);
        String encoding = StringUtils.isNoneBlank(file.content.getEncoding()) ? file.content.getEncoding() :
                StandardCharsets.UTF_8.name();
        writer.setEncoding(encoding);

        String mimeType = StringUtils.isNoneBlank(file.content.getMimetype()) ? file.content.getMimetype() :
                file.mimetype;
        writer.setMimetype(mimeType);

        writer.putContent(file.content.getInputStream());

        return createdTempFile;
    }

    private class File {
        String fileName;
        String mimetype;
        Content content;

        File(WebScriptRequest request) {
            FormData formData = (FormData) request.parseContent();
            FormData.FormField[] fields = formData.getFields();
            for (FormData.FormField field : fields) {
                if (PARAM_FILE.equals(field.getName()) && field.getIsFile()) {
                    this.fileName = field.getFilename();
                    this.content = field.getContent();
                    this.mimetype = field.getMimetype();
                    break;
                }
            }
        }

        boolean isInvalid() {
            return StringUtils.isAnyBlank(fileName, mimetype) || content == null;
        }

        @Override
        public String toString() {
            return "File{" +
                    "fileName='" + fileName + '\'' +
                    ", mimetype='" + mimetype + '\'' +
                    ", content=" + content +
                    '}';
        }
    }

    @Autowired
    public void setTempFilesParentComponent(TempFilesParentComponent tempFilesParentComponent) {
        this.tempFilesParentComponent = tempFilesParentComponent;
    }

    @Autowired
    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    @Autowired
    public void setContentService(ContentService contentService) {
        this.contentService = contentService;
    }
}
