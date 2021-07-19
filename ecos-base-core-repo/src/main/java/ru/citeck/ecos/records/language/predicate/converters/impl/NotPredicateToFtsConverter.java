package ru.citeck.ecos.records.language.predicate.converters.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.records.language.predicate.converters.PredToFtsContext;
import ru.citeck.ecos.records.language.predicate.converters.delegators.ConvertersDelegator;
import ru.citeck.ecos.records.language.predicate.converters.PredicateToFtsConverter;
import ru.citeck.ecos.records2.predicate.model.NotPredicate;
import ru.citeck.ecos.records2.predicate.model.Predicate;
import ru.citeck.ecos.search.ftsquery.FTSQuery;

@Component
@Slf4j
public class NotPredicateToFtsConverter implements PredicateToFtsConverter {

    private ConvertersDelegator delegator;

    @Override
    public void convert(Predicate predicate, FTSQuery query, PredToFtsContext context) {
        query.not();
        delegator.delegate(((NotPredicate) predicate).getPredicate(), query, context);
    }

    @Autowired
    public void setDelegator(ConvertersDelegator delegator) {
        this.delegator = delegator;
    }
}
