package ru.citeck.ecos.records.language.predicate.converters;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class PredToFtsContext {
    private final Map<String, String> attsMapping;
}
