package ru.citeck.ecos.records;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import ru.citeck.ecos.records.type.TypeDto;
import ru.citeck.ecos.records.type.TypesManager;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.type.ComputedAtt;
import ru.citeck.ecos.records2.type.RecordTypeService;

import java.util.Collections;
import java.util.List;

@Slf4j
public class RecordsTypeServiceImpl implements RecordTypeService {

    private final TypesManager typeInfoProvider;

    public RecordsTypeServiceImpl(TypesManager typeInfoProvider) {

        this.typeInfoProvider = typeInfoProvider;
        if (typeInfoProvider == null) {
            log.warn("TypeInfoProvider is null. Some features of ECOS types won't be allowed");
        }
    }


    @NotNull
    @Override
    public List<ComputedAtt> getComputedAtts(@NotNull RecordRef typeRef) {

        if (typeInfoProvider == null || RecordRef.isEmpty(typeRef)) {
            return Collections.emptyList();
        }

        TypeDto typeDto = typeInfoProvider.getType(typeRef);

        if (typeDto == null) {
            return Collections.emptyList();
        }
        //List<ComputedAttribute> attributes = typeDto.getComputedAttributes();

        /*if (attributes == null) {
            return Collections.emptyList();
        }
        return attributes;*/
        return Collections.emptyList();
    }
}
