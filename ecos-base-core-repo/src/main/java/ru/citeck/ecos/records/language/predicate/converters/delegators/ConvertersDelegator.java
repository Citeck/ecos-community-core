package ru.citeck.ecos.records.language.predicate.converters.delegators;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.records.language.predicate.converters.PredToFtsContext;
import ru.citeck.ecos.records.language.predicate.converters.impl.ComposedPredicateToFtsConverter;
import ru.citeck.ecos.records.language.predicate.converters.impl.EmptyPredicateToFtsConverter;
import ru.citeck.ecos.records.language.predicate.converters.impl.NotPredicateToFtsConverter;
import ru.citeck.ecos.records.language.predicate.converters.impl.ValuePredicateToFtsConverter;
import ru.citeck.ecos.records2.predicate.model.*;
import ru.citeck.ecos.search.ftsquery.FTSQuery;

@Component
@Slf4j
public class ConvertersDelegator {

    private static final String UNKNOWN_PREDICATE_TYPE = "Unknown predicate type: %s";

    private ComposedPredicateToFtsConverter composedPredicateToFtsConverter;
    private NotPredicateToFtsConverter notPredicateToFtsConverter;
    private ValuePredicateToFtsConverter valuePredicateToFtsConverter;
    private EmptyPredicateToFtsConverter emptyPredicateToFtsConverter;

    public void delegate(Predicate predicate, FTSQuery query, PredToFtsContext context) {
        if (predicate instanceof ComposedPredicate) {
            composedPredicateToFtsConverter.convert(predicate, query, context);
            return;
        }
        if (predicate instanceof NotPredicate) {
            notPredicateToFtsConverter.convert(predicate, query, context);
            return;
        }
        if (predicate instanceof ValuePredicate) {
            valuePredicateToFtsConverter.convert(predicate, query, context);
            return;
        }
        if (predicate instanceof EmptyPredicate) {
            emptyPredicateToFtsConverter.convert(predicate, query, context);
            return;
        }
        if (predicate instanceof VoidPredicate) {
            //do nothing
            return;
        }

        throw new RuntimeException(String.format(UNKNOWN_PREDICATE_TYPE, predicate));
    }

    @Autowired
    public void setComposedPredicateToFtsConverter(ComposedPredicateToFtsConverter composedPredicateToFtsConverter) {
        this.composedPredicateToFtsConverter = composedPredicateToFtsConverter;
    }

    @Autowired
    public void setNotPredicateToFtsConverter(NotPredicateToFtsConverter notPredicateToFtsConverter) {
        this.notPredicateToFtsConverter = notPredicateToFtsConverter;
    }

    @Autowired
    public void setValuePredicateToFtsConverter(ValuePredicateToFtsConverter valuePredicateToFtsConverter) {
        this.valuePredicateToFtsConverter = valuePredicateToFtsConverter;
    }

    @Autowired
    public void setEmptyPredicateToFtsConverter(EmptyPredicateToFtsConverter emptyPredicateToFtsConverter) {
        this.emptyPredicateToFtsConverter = emptyPredicateToFtsConverter;
    }
}
