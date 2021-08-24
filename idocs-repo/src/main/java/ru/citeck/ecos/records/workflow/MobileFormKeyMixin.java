package ru.citeck.ecos.records.workflow;

import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils;
import ru.citeck.ecos.node.EcosTypeService;
import ru.citeck.ecos.records.type.TypeDto;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records3.RecordsService;
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName;
import ru.citeck.ecos.records3.record.atts.value.AttValueCtx;
import ru.citeck.ecos.records3.record.mixin.AttMixin;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class MobileFormKeyMixin implements AttMixin {

    private static final String MOBILE_SUFFIX = "_mobile";

    private final EcosTypeService ecosTypeService;

    @Autowired
    public MobileFormKeyMixin(WorkflowTaskRecords workflowTaskRecords, EcosTypeService ecosTypeService) {
        workflowTaskRecords.addAttributesMixin(this);
        this.ecosTypeService = ecosTypeService;
    }

    @Nullable
    @Override
    public Object getAtt(@NotNull String name, @NotNull AttValueCtx attValueCtx) throws Exception {

        ValueAtts atts = attValueCtx.getAtts(ValueAtts.class);
        List<String> formKeys = Optional.ofNullable(atts.formKeys).orElse(Collections.emptyList());
        String documentType = Optional.ofNullable(atts.documentType).orElse("");

        if (documentType.isEmpty()) {
            return toMobileKeys(formKeys);
        }

        List<String> types = new ArrayList<>();
        types.add(documentType);

        RecordRef typeRef = TypeUtils.getTypeRef(documentType);
        TypeDto typeDef = ecosTypeService.getTypeDef(typeRef);
        int counter = 0;
        while (++counter < 3 && typeDef != null && RecordRef.isNotEmpty(typeDef.getParentRef())) {
            types.add(typeDef.getParentRef().getId());
            typeDef = ecosTypeService.getTypeDef(typeDef.getParentRef());
        }

        List<String> resultKeys = new ArrayList<>();
        for (String key : formKeys) {
            for (String type : types) {
                resultKeys.add(type + "_" + key);
            }
        }
        resultKeys.addAll(formKeys);

        return toMobileKeys(resultKeys);
    }

    private List<String> toMobileKeys(List<String> keys) {
        return keys.stream()
            .map(this::toMobileKey)
            .collect(Collectors.toList());
    }

    private String toMobileKey(String key) {
        return key + MOBILE_SUFFIX;
    }

    @NotNull
    @Override
    public Collection<String> getProvidedAtts() {
        return Collections.singleton(RecordConstants.ATT_FORM_KEY + MOBILE_SUFFIX);
    }

    @Data
    static class ValueAtts {
        @AttName(RecordConstants.ATT_FORM_KEY)
        private List<String> formKeys;
        @AttName("document._type?localId")
        private String documentType;
    }
}
