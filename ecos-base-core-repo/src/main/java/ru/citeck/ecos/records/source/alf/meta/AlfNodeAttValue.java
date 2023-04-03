package ru.citeck.ecos.records.source.alf.meta;

import com.fasterxml.jackson.databind.util.ISO8601Utils;
import java.io.ByteArrayOutputStream;
import lombok.Getter;
import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.thumbnail.ThumbnailDefinition;
import org.alfresco.repo.thumbnail.ThumbnailRegistry;
import org.alfresco.service.cmr.repository.*;
import org.alfresco.service.cmr.thumbnail.ThumbnailService;
import org.alfresco.service.namespace.QName;
import org.json.JSONArray;
import ru.citeck.ecos.graphql.AlfGqlContext;
import ru.citeck.ecos.graphql.node.Attribute;
import ru.citeck.ecos.graphql.node.GqlAlfNode;
import ru.citeck.ecos.graphql.node.GqlQName;
import ru.citeck.ecos.node.AlfNodeInfo;
import ru.citeck.ecos.node.DisplayNameService;
import ru.citeck.ecos.records.meta.MetaUtils;
import ru.citeck.ecos.records.source.alf.file.FileRepresentation;
import ru.citeck.ecos.records.source.common.MLTextValue;
import ru.citeck.ecos.records2.QueryContext;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;
import ru.citeck.ecos.utils.DictUtils;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.stream.Collectors;

public class AlfNodeAttValue implements MetaValue {

    private static final String AS_CONTENT_DATA_KEY = "content-data";

    private Attribute att;

    private Object rawValue;
    private GqlAlfNode alfNode;
    private GqlQName qName;

    private AlfGqlContext context;

    @Getter(lazy = true)
    private final ContentInfo contentInfo = evalContentInfo();

    public AlfNodeAttValue(Attribute att, Object value) {
        this.att = att;
        this.rawValue = value;
    }

    public AlfNodeAttValue(Object value) {
        this.rawValue = value;
    }

    @Override
    public <T extends QueryContext> void init(T context, MetaField field) {

        this.context = (AlfGqlContext) context;

        if (rawValue instanceof GqlAlfNode) {
            alfNode = (GqlAlfNode) rawValue;
        } else if (rawValue instanceof NodeRef) {
            alfNode = this.context.getNode(rawValue).orElse(null);
        } else if (rawValue instanceof QName) {
            qName = this.context.getQName(rawValue).orElse(null);
        } else if (rawValue instanceof GqlQName) {
            qName = (GqlQName) rawValue;
        }
    }

    @Override
    public String getId() {
        if (alfNode != null) {
            return alfNode.nodeRef();
        } else if (qName != null) {
            return qName.shortName();
        }
        if (rawValue instanceof String) {
            return (String) rawValue;
        }
        return null;
    }

    @Override
    public String getDisplayName() {

        if (rawValue instanceof String && att != null && att.type() == Attribute.Type.PROP) {

            QName name = QName.resolveToQName(context.getNamespaceService(), att.name());
            if (name != null) {

                DictUtils dictUtils = context.getService(DictUtils.QNAME);
                return dictUtils.getPropertyDisplayName(att.getScopeType(), name, (String) rawValue);
            }
        }

        if (alfNode != null) {

            DisplayNameService displayNameService = context.getService(DisplayNameService.QNAME);
            return displayNameService.getDisplayName(new NodeInfo(alfNode));
        }
        if (qName != null) {
            return qName.classTitle();
        }

        return getString();
    }

    @Override
    public String getString() {

        if (rawValue == null) {
            return null;
        }
        if (alfNode != null) {
            return alfNode.nodeRef();
        }
        if (qName != null) {
            return qName.shortName();
        }
        if (rawValue instanceof Date) {
            return ISO8601Utils.format((Date) rawValue);
        }
        if (rawValue instanceof ContentData) {
            String contentUrl = ((ContentData) rawValue).getContentUrl();
            ContentService contentService = context.getServiceRegistry().getContentService();

            return AuthenticationUtil.runAsSystem(() -> {
                ContentReader reader = contentService.getRawReader(contentUrl);
                return reader.exists() ? reader.getContentString() : null;
            });
        }
        if (rawValue instanceof Number) {

            DecimalFormat format = context.getOrPutData("DecimalFormat", DecimalFormat.class, () -> {

                DecimalFormatSymbols locale = DecimalFormatSymbols.getInstance(Locale.ENGLISH);
                DecimalFormat fmt = new DecimalFormat("0", locale);
                fmt.setMaximumFractionDigits(340);
                return fmt;
            });

            return format.format(((Number) rawValue).doubleValue());
        }
        return rawValue.toString();
    }

    @Override
    public Object getAttribute(String name, MetaField field) {
        if (alfNode != null) {
            Attribute attribute = alfNode.attribute(name);
            return attribute.getValues()
                    .stream()
                    .map(v -> {
                        AlfNodeAttValue value = new AlfNodeAttValue(v);
                        value.init(context, field);
                        return value;
                    })
                    .collect(Collectors.toList());
        } else if (qName != null) {
            return MetaUtils.getReflectionValue(qName, name);
        } else if (rawValue instanceof MLText) {
            return new MLTextValue((MLText) rawValue);
        } else if (rawValue instanceof ContentData) {
            ContentData content = (ContentData) rawValue;
            switch (name) {
                case "previewInfo":
                    return getContentInfo();
                case "mimetype":
                    return content.getMimetype();
                case "size":
                    return content.getSize();
                case "encoding":
                    return content.getEncoding();
                case "locale":
                    return content.getLocale();
                case "contentUrl":
                    return content.getContentUrl();
                case "bytes":
                    String contentUrl = content.getContentUrl();
                    ContentService contentService = context.getServiceRegistry().getContentService();
                    return AuthenticationUtil.runAsSystem(() -> {
                        ContentReader reader = contentService.getRawReader(contentUrl);
                        if (reader != null && reader.exists()) {
                            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                            reader.getContent(outputStream);
                            return outputStream.toByteArray();
                        } else {
                            return null;
                        }
                    });
            }
        }
        return null;
    }

    @Override
    public Object getAs(String type) {
        if (AS_CONTENT_DATA_KEY.equalsIgnoreCase(type)) {
            if (alfNode != null) {
                return FileRepresentation.fromAlfNode(alfNode, context);
            }

            if (rawValue instanceof ContentData) {
                JSONArray file = FileRepresentation.formContentData((ContentData) rawValue, this.context, att);
                return file.toString();
            }

            throw new AlfrescoRuntimeException("Unsupported state for as key: " + AS_CONTENT_DATA_KEY);
        }
        return null;
    }

    private ContentInfo evalContentInfo() {

        if (!(rawValue instanceof ContentData)) {
            return null;
        }

        ContentData data = (ContentData) rawValue;
        String mimetype = data.getMimetype();
        if (mimetype == null || att == null) {
            return null;
        }
        NodeRef scopeRef = att.getScopeNodeRef();
        if (scopeRef == null) {
            return null;
        }

        MimetypeService mimetypeService = context.getServiceRegistry().getMimetypeService();
        NodeService nodeService = context.getServiceRegistry().getNodeService();

        String url = "alfresco/api/node/workspace/SpacesStore/" + scopeRef.getId() + "/content";
        String originalUrl = url;
        String originalName = String.valueOf(nodeService.getProperty(scopeRef, ContentModel.PROP_NAME));
        String previewMimetype = mimetype;
        String originalExtension = mimetypeService.getExtension(mimetype);
        String previewExtension;

        switch (mimetype) {
            case MimetypeMap.MIMETYPE_PDF:
            case MimetypeMap.MIMETYPE_IMAGE_PNG:
            case MimetypeMap.MIMETYPE_IMAGE_JPEG:
            case MimetypeMap.MIMETYPE_IMAGE_GIF:
                previewExtension = mimetypeService.getExtension(mimetype);
                break;
            default:
                String thumbnailType = getThumbnailType(data);
                if (thumbnailType != null) {
                    url += "/thumbnails/" + thumbnailType;
                    previewExtension = thumbnailType;
                    previewMimetype = mimetypeService.getMimetype(previewExtension);
                } else {
                    url = null;
                    previewExtension = mimetypeService.getExtension(previewMimetype);
                }
        }

        if (url != null) {
            url += "?c=force";
        }

        return new ContentInfo(url, originalUrl, originalName, originalExtension, previewExtension, previewMimetype);
    }

    private String getThumbnailType(ContentData data) {

        ThumbnailService thumbnailService = context.getServiceRegistry().getThumbnailService();
        if (thumbnailService == null) {
            return null;
        }
        ThumbnailRegistry registry = thumbnailService.getThumbnailRegistry();
        if (registry == null) {
            return null;
        }

        List<ThumbnailDefinition> definitions = registry.getThumbnailDefinitions(data.getMimetype(), data.getSize());
        if (definitions == null) {
            definitions = Collections.emptyList();
        }

        for (ThumbnailDefinition definition : definitions) {
            if ("pdf".equals(definition.getName())) {
                return "pdf";
            }
        }
        return null;
    }

    public static class NodeInfo implements AlfNodeInfo {

        private GqlAlfNode alfNode;

        private NodeInfo(GqlAlfNode node) {
            alfNode = node;
        }

        @Override
        public QName getType() {
            return alfNode.getType();
        }

        @Override
        public NodeRef getNodeRef() {
            return new NodeRef(alfNode.nodeRef());
        }

        @Override
        public Map<QName, Serializable> getProperties() {
            return alfNode.getProperties();
        }
    }

    public static class ContentInfo {

        @Getter private final String url;
        @Getter private final String originalUrl;
        @Getter private final String originalName;
        @Getter private final String originalExt;
        @Getter private final String ext;
        @Getter private final String mimetype;

        ContentInfo(String url, String originalUrl, String originalName,
                    String originalExt, String ext, String mimetype) {
            this.url = url;
            this.originalUrl = originalUrl;
            this.originalName = originalName;
            this.originalExt = originalExt;
            this.ext = ext;
            this.mimetype = mimetype;
        }
    }
}
