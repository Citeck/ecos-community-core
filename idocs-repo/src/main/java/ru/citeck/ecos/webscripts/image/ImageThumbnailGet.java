package ru.citeck.ecos.webscripts.image;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.web.scripts.content.ContentStreamer;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.*;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.TempFileProvider;
import org.apache.commons.lang.StringUtils;
import org.imgscalr.Scalr;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.extensions.webscripts.*;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

public class ImageThumbnailGet extends AbstractWebScript {

    private static final String PARAM_NODE_REF = "nodeRef";
    private static final String PARAM_PROPERTY = "property";
    private static final String PARAM_WIDTH = "width";
    private static final String PARAM_HEIGHT = "height";
    private static final String PARAM_CACHED = "cached";
    private static final String PARAM_ATTACH = "attach";
    private static final String PARAM_STRETCH = "stretch";

    private static final int TEMP_FILE_UPDATE_RATE = 10 * 60 * 1000;// 10 min

    private NodeService nodeService;
    private ContentService contentService;
    private ContentStreamer contentStreamer;
    private MimetypeService mimetypeService;
    private NamespaceService namespaceService;

    @Override
    public void execute(WebScriptRequest req, WebScriptResponse res) throws IOException {

        String nodeRefStr = req.getParameter(PARAM_NODE_REF);
        if (StringUtils.isBlank(nodeRefStr) || !NodeRef.isNodeRef(nodeRefStr)) {
            String msg = PARAM_NODE_REF + " has incorrect value: " + nodeRefStr;
            throw new WebScriptException(HttpServletResponse.SC_BAD_REQUEST, msg);
        }
        if (nodeRefStr.startsWith("alfresco/@")) {
            nodeRefStr = nodeRefStr.replaceFirst("alfresco/@", "");
        }

        NodeRef nodeRef = new NodeRef(nodeRefStr);
        String widthStr = req.getParameter(PARAM_WIDTH);
        String heightStr = req.getParameter(PARAM_HEIGHT);

        int width = StringUtils.isNotBlank(widthStr) ? Integer.parseInt(widthStr) : -1;
        int height = StringUtils.isNotBlank(heightStr) ? Integer.parseInt(heightStr) : -1;
        boolean stretch = "true".equals(req.getParameter(PARAM_STRETCH));

        QName contentProperty = ContentModel.PROP_CONTENT;
        String contentPropertyStr = req.getParameter(PARAM_PROPERTY);
        if (StringUtils.isNotBlank(contentPropertyStr)) {
            contentProperty = QName.resolveToQName(namespaceService, contentPropertyStr);
        }

        ContentReader reader = contentService.getReader(nodeRef, contentProperty);
        if (reader == null || !reader.exists()) {
            throw new WebScriptException(Status.STATUS_NOT_FOUND, "Content not found");
        }

        File tempFile = getTempFile(nodeRef, reader, contentProperty, width, height, stretch);

        long tempLastModified = tempFile.lastModified();
        if (!tempFile.exists() || tempLastModified < reader.getLastModified()) {
            updateImage(reader, tempFile, width, height, stretch);
        } else if (System.currentTimeMillis() - tempLastModified > TEMP_FILE_UPDATE_RATE) {
            tempFile.setLastModified(System.currentTimeMillis());
        }

        Map<QName, Serializable> imageProps = nodeService.getProperties(nodeRef);
        String imageName = (String) imageProps.get(ContentModel.PROP_NAME);
        boolean attach = "true".equals(req.getParameter(PARAM_ATTACH));

        contentStreamer.streamContent(req, res,
                                      tempFile, reader.getLastModified(),
                                      attach, imageName, getModel(req));
        res.setStatus(Status.STATUS_OK);
    }

    private File getTempFile(NodeRef imageRef,
                             ContentReader reader,
                             QName property,
                             int width, int height,
                             boolean stretch) {

        String extension = mimetypeService.getExtension(reader.getMimetype());
        File tmpDir = TempFileProvider.getTempDir().getAbsoluteFile();
        int hash = Objects.hash(imageRef, property, reader.getLastModified(), width, height, stretch);

        return new File(tmpDir, String.format("%s_%s.%s", imageRef.getId(), hash, extension));
    }

    private void updateImage(ContentReader reader,
                             File target,
                             int width,
                             int height,
                             boolean stretch) throws IOException {

        String originalMimetype = reader.getMimetype();
        String originalExtension = mimetypeService.getExtension(originalMimetype);

        BufferedImage originalImage;
        try (InputStream content = reader.getContentInputStream()) {
            originalImage = ImageIO.read(content);
        }

        BufferedImage thumbnailImg;

        if (width == -1 && height == -1) {
            thumbnailImg = originalImage;
        } else if (width > 0 && height == -1) {
            thumbnailImg = Scalr.resize(originalImage, Scalr.Mode.FIT_TO_WIDTH, width);
        } else if (height > 0 && width == -1) {
            thumbnailImg = Scalr.resize(originalImage, Scalr.Mode.FIT_TO_HEIGHT, height);
        } else if (stretch) {
            thumbnailImg = Scalr.resize(originalImage, Scalr.Mode.FIT_EXACT, width, height);
        } else {
            thumbnailImg = Scalr.resize(originalImage, Scalr.Mode.AUTOMATIC, width, height);
        }

        ImageIO.write(thumbnailImg, originalExtension, target);
    }

    private Map<String, Object> getModel(WebScriptRequest req) {
        String cached = req.getParameter(PARAM_CACHED);
        Map<String, Object> model = new HashMap<>();
        model.put("allowBrowserToCache", "false".equals(cached) ? "false" : "true");
        return model;
    }

    @Autowired
    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        nodeService = serviceRegistry.getNodeService();
        contentService = serviceRegistry.getContentService();
        mimetypeService = serviceRegistry.getMimetypeService();
        namespaceService = serviceRegistry.getNamespaceService();
    }

    @Autowired
    @Qualifier("webscript.content.streamer")
    public void setContentStreamer(ContentStreamer contentStreamer) {
        this.contentStreamer = contentStreamer;
    }
}
