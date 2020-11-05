package ru.citeck.ecos.records.language.predicate.converters;

import lombok.AllArgsConstructor;
import lombok.Data;
import ru.citeck.ecos.records2.RecordRef;

import java.util.Map;

@Data
@AllArgsConstructor
public class PredToFtsContext {
    private final RecordRef typeRef;
    private final Map<String, String> attsMapping;
}
