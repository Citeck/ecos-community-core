package ru.citeck.ecos.records.notification;

import lombok.Data;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.records2.graphql.meta.annotation.MetaAtt;

@Data
public class TemplateModelDto {

    @MetaAtt("model")
    private ObjectData model;

}
