package ru.citeck.ecos.records.source.common;

import lombok.SneakyThrows;
import org.alfresco.service.cmr.repository.MLText;
import org.jetbrains.annotations.NotNull;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;
import ru.citeck.ecos.records3.record.atts.value.AttValue;
import ru.citeck.ecos.records3.record.atts.value.factory.MLTextValueFactory;

public class MLTextValue implements MetaValue {

    private static final MLTextValueFactory factory = new MLTextValueFactory();

    private final AttValue value;

    public MLTextValue(MLText text) {
        this.value = factory.getValue(new ru.citeck.ecos.commons.data.MLText(text));
    }

    @Override
    @SneakyThrows
    public String getString() {
        return value.asText();
    }

    @Override
    public Object getAttribute(@NotNull String name, @NotNull MetaField field) throws Exception {
        return value.getAtt(name);
    }

    @Override
    @SneakyThrows
    public Object getJson() {
        return value.asJson();
    }

    @Override
    @SneakyThrows
    public boolean has(@NotNull String name) throws Exception {
        return value.has(name);
    }

    @Override
    @SneakyThrows
    public Object getRaw() {
        return value.asRaw();
    }

    @Override
    @SneakyThrows
    public Object getAs(@NotNull String type, @NotNull MetaField field) {
        return value.getAs(type);
    }
}
