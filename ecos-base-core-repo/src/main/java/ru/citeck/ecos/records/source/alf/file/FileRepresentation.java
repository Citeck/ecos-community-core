package ru.citeck.ecos.records.source.alf.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.*;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.extensions.surf.util.I18NUtil;
import ru.citeck.ecos.graphql.AlfGqlContext;
import ru.citeck.ecos.graphql.node.Attribute;
import ru.citeck.ecos.graphql.node.GqlAlfNode;
import ru.citeck.ecos.model.ClassificationModel;

import java.io.Serializable;
import java.util.Map;

/**
 * @author Roman Makarskiy
 */
public class FileRepresentation {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static final String FILE_TYPE_DELIMITER = "/";

    static final String MODEL_MIME_TYPE = "mimetype";
    static final String MODEL_FILE_NAME = "filename";
    static final String MODEL_ENCODING = "encoding";
    static final String MODEL_CONTENT = "content";
    static final String MODEL_BASE64_CONTENT = "base64content";
    static final String MODEL_DATA = "data";
    static final String MODEL_NODE_REF = "nodeRef";
    static final String MODEL_URL = "url";
    static final String MODEL_NAME = "name";
    static final String MODEL_SIZE = "size";
    static final String MODEL_FILE_TYPE = "fileType";

    private static final String URL_PATTERN = "/share/page/card-details?nodeRef=%s";

    public static ObjectNode fromAlfNode(GqlAlfNode alfNode, AlfGqlContext context) {
        Map<QName, Serializable> properties = alfNode.getProperties();

        ContentService contentService = context.getService("contentService");
        NodeRef nodeRef = new NodeRef(alfNode.nodeRef());
        ContentReader reader = contentService.getReader(nodeRef, ContentModel.PROP_CONTENT);

        ObjectNode obj = OBJECT_MAPPER.createObjectNode();
        ObjectNode data = OBJECT_MAPPER.createObjectNode();
        data.put(MODEL_NODE_REF, alfNode.nodeRef());

        obj.put(MODEL_DATA, data);
        obj.put(MODEL_URL, String.format(URL_PATTERN, alfNode.nodeRef()));
        Serializable dispName = properties.get(ContentModel.PROP_TITLE);
        if (dispName instanceof MLText) {
            dispName = ((MLText) dispName).getClosestValue(I18NUtil.getLocale());
        }
        if (!(dispName instanceof String) || StringUtils.isBlank((String) dispName)) {
            dispName = properties.get(ContentModel.PROP_NAME);
        }
        obj.put(MODEL_NAME, (String) dispName);
        obj.put(MODEL_SIZE, reader.getSize());

        String typeKind = "";
        Serializable typeRaw = properties.get(ClassificationModel.PROP_DOCUMENT_TYPE);
        if (typeRaw != null) {
            NodeRef typeRef = (NodeRef) typeRaw;
            typeKind = typeRef.getId();
        }

        Serializable kindRaw = properties.get(ClassificationModel.PROP_DOCUMENT_KIND);
        if (kindRaw != null) {
            NodeRef kindRef = (NodeRef) kindRaw;
            typeKind += FILE_TYPE_DELIMITER + kindRef.getId();
        }

        obj.put(MODEL_FILE_TYPE, typeKind);

        return obj;
    }

    public static JSONArray formContentData(ContentData contentData, AlfGqlContext context, Attribute att) {
        NodeService nd = context.getNodeService();
        NodeRef nodeRef = att.getScopeNodeRef();

        String name = (String) nd.getProperty(nodeRef, ContentModel.PROP_NAME);

        JSONArray array = new JSONArray();
        JSONObject obj = new JSONObject();
        try {
            obj.put(MODEL_URL, String.format(URL_PATTERN, nodeRef.toString()));
            obj.put(MODEL_NAME, name);
            obj.put(MODEL_SIZE, contentData.getSize());
        } catch (JSONException e) {
            throw new AlfrescoRuntimeException("Error while generate file representation from content data", e);
        }

        array.put(obj);
        return array;
    }
}
