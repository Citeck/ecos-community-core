package ru.citeck.ecos.node;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import ru.citeck.ecos.model.lib.attributes.dto.AttributeDef;
import ru.citeck.ecos.records.type.TypeDto;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.utils.AlfrescoScopableProcessorExtension;

import java.util.List;

@Slf4j
public class EcosTypeServiceJS extends AlfrescoScopableProcessorExtension {

    private EcosTypeService ecosTypeService;

    public String[] getAttsIdListByType(String type) {

        if (StringUtils.isBlank(type)) {
            return new String[0];
        }
        if (!type.startsWith("emodel/type@")) {
            type = "emodel/type@";
        }
        TypeDto typeDef = ecosTypeService.getTypeDef(RecordRef.valueOf(type));
        if (typeDef == null || typeDef.getResolvedModel() == null) {
            return new String[0];
        }

        List<AttributeDef> attributes = typeDef.getResolvedModel().getAttributes();

        return attributes.stream()
            .map(AttributeDef::getId)
            .toArray(String[]::new);
    }

    public void setEcosTypeService(EcosTypeService ecosTypeService) {
        this.ecosTypeService = ecosTypeService;
    }
}
