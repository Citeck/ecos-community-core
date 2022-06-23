package ru.citeck.ecos.records.type;

import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.records2.RecordRef;

public interface TypesManager {

    TypeDto getType(RecordRef typeRef);

    NumTemplateDto getNumTemplate(RecordRef templateRef);

    Long getNextNumber(RecordRef templateRef, ObjectData model);

    RecordRef getEcosType(String alfType);
}
