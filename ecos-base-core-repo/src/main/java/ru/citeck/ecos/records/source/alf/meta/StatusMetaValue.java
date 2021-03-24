package ru.citeck.ecos.records.source.alf.meta;

import org.alfresco.service.cmr.repository.NodeRef;
import org.jetbrains.annotations.NotNull;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;

public class StatusMetaValue implements MetaValue {

    public static final String ATT_STATUS_ID = "statusId";
    public static final String ATT_CM_NAME = "cm:name";
    public static final String ATT_CM_TITLE = "cm:title";

    private final String id;
    private final String name;
    private final NodeRef nodeRef;

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

    @Override
    public Object getAttribute(@NotNull String name, @NotNull MetaField field) {
        if (name.equals(ATT_STATUS_ID) || name.equals(ATT_CM_NAME)) {
            return id;
        }
        if (name.equals(ATT_CM_TITLE)) {
            return this.name;
        }
        return null;
    }
}
