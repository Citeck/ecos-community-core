package ru.citeck.ecos.records.source.alf.meta;

import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;

public class StatusMetaValue implements MetaValue {
    private String id;
    private String name;

    StatusMetaValue(String id, String name) {
        this.name = name;
        this.id = id;
    }

    @Override
    public String getDisplayName() {
        return name;
    }

    @Override
    public String getString() {
        return id;
    }
}
