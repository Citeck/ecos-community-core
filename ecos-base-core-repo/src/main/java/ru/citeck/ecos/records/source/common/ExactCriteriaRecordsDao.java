package ru.citeck.ecos.records.source.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.records2.predicate.PredicateService;
import ru.citeck.ecos.records2.predicate.PredicateUtils;
import ru.citeck.ecos.records2.predicate.model.Predicate;
import ru.citeck.ecos.records2.*;
import ru.citeck.ecos.records2.predicate.RecordElement;
import ru.citeck.ecos.records2.predicate.RecordElements;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records3.RecordsServiceFactory;
import ru.citeck.ecos.search.*;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class ExactCriteriaRecordsDao extends FilteredRecordsDao implements ServiceFactoryAware {

    private RecordsService recordsService;
    private PredicateService predicateService;

    private List<String> filteredFields = Collections.emptyList();
    private final Map<String, PredicateFilter> filters = new HashMap<>();

    public ExactCriteriaRecordsDao() {

        PredicateFilter exactStrFilter = new PredicateFilter("str", (value, array) -> {
            String arrayValue = "";
            if (array != null && array.size() > 0) {
                JsonNode strNode = array.get(0).get("str");
                arrayValue = strNode.isTextual() ? strNode.asText() : "";
            }
            if (StringUtils.isBlank(value)) {
                return arrayValue.isEmpty();
            } else {
                return Objects.equals(value, arrayValue);
            }
        });

        PredicateFilter emptyStrFilter = new PredicateFilter("str",
                (value, array) -> exactStrFilter.filter.apply("", array));

        filters.put(SearchPredicate.STRING_EMPTY.getValue(), emptyStrFilter);
        filters.put(SearchPredicate.DATE_EMPTY.getValue(), emptyStrFilter);
        filters.put(SearchPredicate.STRING_EQUALS.getValue(), exactStrFilter);
    }

    @Override
    protected Function<List<RecordRef>, List<RecordRef>> getFilter(RecordsQuery query) {
        if (PredicateService.LANGUAGE_PREDICATE.equals(query.getLanguage())) {

            Predicate predicate = query.getQuery(Predicate.class);
            Optional<Predicate> predicateOpt = PredicateUtils.filterValuePredicates(predicate, p ->
                    filteredFields.contains(p.getValue().asText())
                        && (p.getAttribute().equals("ISUNSET") || p.getAttribute().equals("ISNULL"))
            );

            if (predicateOpt.isPresent()) {
                return list -> {
                    RecordElements elements = new RecordElements(recordsService, list);
                    List<RecordElement> filtered = predicateService.filter(elements, predicateOpt.get(), query.getMaxItems());
                    return filtered.stream()
                            .map(RecordElement::getRecordRef)
                            .collect(Collectors.toList());
                };
            }
        }

        return list -> list;
    }

    public void setFilteredFields(List<String> filteredFields) {
        this.filteredFields = filteredFields;
    }

    @Override
    public void setRecordsServiceFactory(RecordsServiceFactory serviceFactory) {
        this.recordsService = serviceFactory.getRecordsService();
    }

    @Autowired
    public void setPredicateService(PredicateService predicateService) {
        this.predicateService = predicateService;
    }

    private class CriterionFilter {

        String fieldKey;
        String fieldValue;
        PredicateFilter predicateFilter;

        boolean apply(ObjectData nodeData) {
            DataValue attNode = nodeData.get(fieldKey);
            if (attNode.isObject()) {
                attNode = attNode.get("val");
            }
            ArrayNode arrayNode = attNode.isArray() ? Json.getMapper().convert(attNode, ArrayNode.class) : null;
            return predicateFilter.filter.apply(fieldValue, arrayNode);
        }
    }

    private class PredicateFilter {

        String metaSchema;
        BiFunction<String, ArrayNode, Boolean> filter;

        PredicateFilter(String metaSchema,
                        BiFunction<String, ArrayNode, Boolean> filter) {

            this.metaSchema = metaSchema;
            this.filter = filter;
        }
    }
}
