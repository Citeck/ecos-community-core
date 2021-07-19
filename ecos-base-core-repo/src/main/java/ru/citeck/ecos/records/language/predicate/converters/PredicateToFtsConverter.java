package ru.citeck.ecos.records.language.predicate.converters;

import ru.citeck.ecos.records2.predicate.model.Predicate;
import ru.citeck.ecos.search.ftsquery.FTSQuery;

public interface PredicateToFtsConverter {

    void convert(Predicate predicate, FTSQuery query, PredToFtsContext context);
}
