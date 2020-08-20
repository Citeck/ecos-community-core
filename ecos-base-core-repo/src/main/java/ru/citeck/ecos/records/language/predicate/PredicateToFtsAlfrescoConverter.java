package ru.citeck.ecos.records.language.predicate;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.service.cmr.search.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.records.language.predicate.converters.delegators.ConvertersDelegator;
import ru.citeck.ecos.records2.predicate.PredicateService;
import ru.citeck.ecos.records2.predicate.model.*;
import ru.citeck.ecos.records2.querylang.QueryLangConverter;
import ru.citeck.ecos.records2.querylang.QueryLangService;
import ru.citeck.ecos.search.ftsquery.FTSQuery;

@Component
@Slf4j
public class PredicateToFtsAlfrescoConverter implements QueryLangConverter<Predicate, String> {

    private ConvertersDelegator delegator;

    @Autowired
    public PredicateToFtsAlfrescoConverter(QueryLangService queryLangService) {
        queryLangService.register(this, PredicateService.LANGUAGE_PREDICATE, SearchService.LANGUAGE_FTS_ALFRESCO);
    }

    @Override
    public String convert(Predicate predicate) {
        if (predicate instanceof VoidPredicate) {
            return "";
        }

        FTSQuery query = FTSQuery.createRaw();
        delegator.delegate(predicate, query);

        return query.getQuery();
    }

    @Autowired
    public void setDelegator(ConvertersDelegator delegator) {
        this.delegator = delegator;
    }
}
