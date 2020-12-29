package ru.citeck.ecos.records.status;

import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;

public class StatusRecord implements MetaValue {

    private final StatusDto dto;

    public StatusRecord(StatusDto dto) {
        this.dto = dto;
    }

    @Override
    public Object getAttribute(String name, MetaField field) throws Exception {

        switch (name) {
            case "type":
                return dto.getType();
            case "name":
                return dto.getName();
        }

        return null;
    }

    @Override
    public String getDisplayName() {
        return dto.getName();
    }

    @Override
    public String getId() {
        return dto.getId();
    }

    @Override
    public String getString() {
        return dto.getId();
    }
}
