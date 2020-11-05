package ru.citeck.ecos.records.language.predicate;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.service.cmr.search.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.utils.IterUtils;
import ru.citeck.ecos.domain.model.alf.service.AlfAutoModelService;
import ru.citeck.ecos.records.language.predicate.converters.PredToFtsContext;
import ru.citeck.ecos.records.language.predicate.converters.delegators.ConvertersDelegator;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.predicate.PredicateService;
import ru.citeck.ecos.records2.predicate.PredicateUtils;
import ru.citeck.ecos.records2.predicate.model.*;
import ru.citeck.ecos.records2.querylang.QueryLangConverter;
import ru.citeck.ecos.records2.querylang.QueryLangService;
import ru.citeck.ecos.search.ftsquery.FTSQuery;

import java.util.*;

@Component
@Slf4j
public class PredicateToFtsAlfrescoConverter implements QueryLangConverter<Predicate, String> {

    private ConvertersDelegator delegator;
    private AlfAutoModelService alfAutoModelService;

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
        delegator.delegate(predicate, query, new PredToFtsContext(getAttsMapping(predicate)));

        return query.getQuery();
    }

    private Map<String, String> getAttsMapping(Predicate predicate) {

        if (alfAutoModelService == null) {
            return Collections.emptyMap();
        }

        Set<String> typesInPredicates = new HashSet<>();

        PredicateUtils.mapValuePredicates(predicate, valuePred -> {
            if (RecordConstants.ATT_TYPE.equals(valuePred.getAttribute())) {
                typesInPredicates.add("" + valuePred.getValue());
            }
            return valuePred;
        }, true);

        if (typesInPredicates.size() != 1) {
            return Collections.emptyMap();
        }

        String type = IterUtils.first(typesInPredicates).orElse(null);
        return alfAutoModelService.getPropsMapping(RecordRef.valueOf(type));
    }

    @Autowired
    public void setDelegator(ConvertersDelegator delegator) {
        this.delegator = delegator;
    }

    @Autowired(required = false)
    public void setAlfAutoModelService(AlfAutoModelService alfAutoModelService) {
        this.alfAutoModelService = alfAutoModelService;
    }
}
