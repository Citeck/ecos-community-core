package ru.citeck.ecos.records.type;

import org.alfresco.service.namespace.QName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.records2.RecordRef;

public interface TypesManager {

    TypeDto getType(RecordRef typeRef);

    NumTemplateDto getNumTemplate(RecordRef templateRef);

    Long getNextNumber(RecordRef templateRef, ObjectData model);

    @NotNull
    RecordRef getEcosTypeByAlfType(@Nullable QName alfType);
}
