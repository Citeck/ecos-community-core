package ru.citeck.ecos.records.type;

import org.alfresco.service.namespace.QName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.model.lib.num.dto.NumTemplateDef;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef;

public interface TypesManager {

    TypeDef getType(RecordRef typeRef);

    NumTemplateDef getNumTemplate(RecordRef templateRef);

    Long getNextNumber(RecordRef templateRef, ObjectData model);

    @NotNull
    RecordRef getEcosTypeByAlfType(@Nullable QName alfType);
}
