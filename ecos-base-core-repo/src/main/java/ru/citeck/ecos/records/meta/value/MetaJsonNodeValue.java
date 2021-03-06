package ru.citeck.ecos.records.meta.value;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import ru.citeck.ecos.graphql.AlfGqlContext;
import ru.citeck.ecos.records.source.alf.meta.AlfNodeAttValue;
import ru.citeck.ecos.records2.QueryContext;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MetaJsonNodeValue implements MetaValue {

    private String id;
    private JsonNode data;
    private AlfGqlContext context;

    public MetaJsonNodeValue(String id, JsonNode data) {
        this.id = id;
        this.data = data;
    }

    @Override
    public <T extends QueryContext> void init(T context, MetaField field) {
        this.context = (AlfGqlContext) context;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getString() {
        if (data == null || data instanceof NullNode) {
            return null;
        }
        return data.asText();
    }

    @Override
    public List<MetaValue> getAttribute(String name, MetaField field) {

        JsonNode attNode = data.get(name);

        if (attNode == null) {
            return Collections.emptyList();
        }

        return getValue(attNode, field);
    }

    private List<MetaValue> getValue(JsonNode attNode, MetaField field) {

        List<MetaValue> attValue = new ArrayList<>();

        if (attNode instanceof ArrayNode) {
            ArrayNode array = (ArrayNode) attNode;
            for (int i = 0; i < array.size(); i++) {
                attValue.addAll(getValue(array.get(i), field));
            }
        } else {
            if (attNode instanceof ObjectNode) {
                JsonNode nodeRefNode = attNode.get("nodeRef");
                if (nodeRefNode instanceof TextNode) {
                    NodeRef nodeRef = new NodeRef(nodeRefNode.asText());
                    attValue.add(toAlfNodeAtt(nodeRef, field));
                } else {
                    JsonNode qnameNode = attNode.get("fullQName");
                    if (qnameNode instanceof TextNode) {
                        QName qName = QName.createQName(qnameNode.asText());
                        attValue.add(toAlfNodeAtt(qName, field));
                    }
                }
            }
            if (attValue.isEmpty()) {
                attValue.add(new MetaJsonNodeValue(null, attNode));
            }
        }

        return attValue;
    }

    private MetaValue toAlfNodeAtt(Object value, MetaField field) {
        MetaValue result = new AlfNodeAttValue(value);
        result.init(context, field);
        return result;
    }
}
