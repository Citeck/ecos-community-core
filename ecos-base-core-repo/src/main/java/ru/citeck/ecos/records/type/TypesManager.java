package ru.citeck.ecos.records.type;

import org.alfresco.service.namespace.QName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.model.lib.num.dto.NumTemplateDef;
import ru.citeck.ecos.webapp.api.entity.EntityRef;
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef;

public interface TypesManager {

    TypeDef getType(EntityRef typeRef);

    NumTemplateDef getNumTemplate(EntityRef templateRef);

    Long getNextNumber(EntityRef templateRef, ObjectData model);

    @NotNull
    EntityRef getEcosTypeByAlfType(@Nullable QName alfType);
}
