package ru.citeck.ecos.records.language.predicate.converters.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.records.language.predicate.converters.PredToFtsContext;
import ru.citeck.ecos.records.language.predicate.converters.delegators.ConvertersDelegator;
import ru.citeck.ecos.records.language.predicate.converters.PredicateToFtsConverter;
import ru.citeck.ecos.records2.predicate.model.AndPredicate;
import ru.citeck.ecos.records2.predicate.model.ComposedPredicate;
import ru.citeck.ecos.records2.predicate.model.Predicate;
import ru.citeck.ecos.search.ftsquery.FTSQuery;

import java.util.List;

@Component
@Slf4j
public class ComposedPredicateToFtsConverter implements PredicateToFtsConverter {

    private ConvertersDelegator delegator;

    @Override
    public void convert(Predicate predicate, FTSQuery query, PredToFtsContext context) {
        query.open();

        List<Predicate> predicates = ((ComposedPredicate) predicate).getPredicates();
        boolean isJoinByAnd = predicate instanceof AndPredicate;

        for (int i = 0; i < predicates.size(); i++) {
            if (i > 0) {
                if (isJoinByAnd) {
                    query.and();
                } else {
                    query.or();
                }
            }

            delegator.delegate(predicates.get(i), query, context);
        }

        query.close();
    }

    @Autowired
    public void setDelegator(ConvertersDelegator delegator) {
        this.delegator = delegator;
    }
}
