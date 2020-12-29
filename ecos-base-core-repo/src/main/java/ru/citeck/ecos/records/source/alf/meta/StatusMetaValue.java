package ru.citeck.ecos.records.source.alf.meta;

import org.alfresco.service.cmr.repository.NodeRef;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;

public class StatusMetaValue implements MetaValue {
    private String id;
    private String name;
    private NodeRef nodeRef;

    StatusMetaValue(String id, String name, NodeRef nodeRef) {
        this.name = name;
        this.id = id;
        this.nodeRef = nodeRef;
    }

    @Override
    public String getDisplayName() {
        return name;
    }

    @Override
    public String getString() {
        return id;
    }

    @Override
    public String getId() {
        return nodeRef.toString();
    }
}
