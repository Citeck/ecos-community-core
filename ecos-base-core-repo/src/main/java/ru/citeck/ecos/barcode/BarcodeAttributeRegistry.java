package ru.citeck.ecos.barcode;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.spring.registry.MappingRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Registry for mapping node's value of attribute '_etype' to property.
 */
@Component
public class BarcodeAttributeRegistry {

    private static final String SLASH_DELIMITER = "/";
    private static final String DEFAULT_VALUE = "idocs:barcode";

    private RecordsService recordsService;

    private MappingRegistry<String, String> registry;

    @Autowired
    public BarcodeAttributeRegistry(RecordsService recordsService,
                                    @Qualifier("core.barcode-attribute.type-to-property.mappingRegistry")
                                            MappingRegistry<String, String> registry) {
        this.recordsService = recordsService;
        this.registry = registry;
    }

    public String getAttribute(RecordRef recordRef) {

        DataValue ecosTypeJson = recordsService.getAttribute(recordRef, "_etype?id");
        String ecosType = ecosTypeJson.isTextual() ? ecosTypeJson.asText() : "";

        if (StringUtils.isBlank(ecosType)) {
            return DEFAULT_VALUE;
        } else {
            ecosType = RecordRef.valueOf(ecosType).getId();
        }

        String result = registry.get(ecosType);
        if (StringUtils.isBlank(result)) {
            result = registry.get(splitAndGet(ecosType));
        }

        return StringUtils.isBlank(result) ? DEFAULT_VALUE : result;
    }

    private String splitAndGet(String ecosType) {
        if (ecosType.contains(SLASH_DELIMITER)) {
            List<String> parts = new ArrayList<>(Arrays.asList(ecosType.split(SLASH_DELIMITER)));
            parts.remove(parts.size() - 1);
            return StringUtils.join(parts.iterator(), SLASH_DELIMITER);
        }
        return ecosType;
    }
}
