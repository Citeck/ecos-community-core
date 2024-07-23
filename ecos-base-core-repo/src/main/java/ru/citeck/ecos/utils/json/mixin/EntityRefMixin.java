package ru.citeck.ecos.utils.json.mixin;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

public abstract class EntityRefMixin {
    @JsonCreator
    public static EntityRef valueOf(Object value) {
        return null;
    }
    @JsonValue
    public abstract String toString();
}
