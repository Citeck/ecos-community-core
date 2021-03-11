package ru.citeck.ecos.records.language.predicate;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.namespace.QName;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.commons.utils.IterUtils;
import ru.citeck.ecos.domain.model.alf.service.AlfAutoModelService;
import ru.citeck.ecos.node.etype.EcosTypeAlfTypeService;
import ru.citeck.ecos.records.language.predicate.converters.PredToFtsContext;
import ru.citeck.ecos.records.language.predicate.converters.delegators.ConvertersDelegator;
import ru.citeck.ecos.records.source.alf.search.SearchServiceAlfNodesSearch;
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

    private static final String WORKSPACE = "workspace";
    private static final String ALFRESCO_APP_NODE_REF_PREFIX = "alfresco/@" + WORKSPACE;

    private ConvertersDelegator delegator;
    private AlfAutoModelService alfAutoModelService;
    private EcosTypeAlfTypeService ecosTypeAlfTypeService;

    @Autowired
    public PredicateToFtsAlfrescoConverter(QueryLangService queryLangService,
                                           EcosTypeAlfTypeService ecosTypeAlfTypeService) {

        queryLangService.register(
            this,
            PredicateService.LANGUAGE_PREDICATE,
            SearchService.LANGUAGE_FTS_ALFRESCO
        );
        this.ecosTypeAlfTypeService = ecosTypeAlfTypeService;
    }

    @Override
    public String convert(Predicate predicate) {
        if (predicate instanceof VoidPredicate) {
            return "";
        }

        FTSQuery query = FTSQuery.createRaw();
        PredToFtsContext context = getPredToFtsContext(predicate);
        if (context == null) {
            context = new PredToFtsContext(RecordRef.EMPTY, predicate, Collections.emptyMap());
        }
        delegator.delegate(context.getRootPredicate(), query, context);

        String queryStr = query.getQuery();
        if (RecordRef.isNotEmpty(context.getTypeRef())) {
            queryStr += SearchServiceAlfNodesSearch.ECOS_TYPE_DELIM + context.getTypeRef();
        }
        return queryStr;
    }

    @Nullable
    private PredToFtsContext getPredToFtsContext(Predicate predicate) {

        Set<String> typesInPredicates = new HashSet<>();

        Predicate rootPredicate = PredicateUtils.mapValuePredicates(predicate, valuePred -> {
            if (RecordConstants.ATT_TYPE.equals(valuePred.getAttribute())) {

                String typeRefStr = valuePred.getValue().asText();
                typesInPredicates.add(typeRefStr);

                String alfType = ecosTypeAlfTypeService.getAlfTypeToSearch(RecordRef.valueOf(typeRefStr));
                if (alfType != null) {
                    return new ValuePredicate("TYPE", valuePred.getType(), alfType);
                }
            }
            return new ValuePredicate(
                valuePred.getAttribute(),
                valuePred.getType(),
                mapAlfrescoNodeRefs(valuePred.getValue())
            );
        }, true);

        String type = IterUtils.first(typesInPredicates).orElse(null);
        RecordRef typeRef = RecordRef.valueOf(type);

        Map<String, String> propsMapping;
        if (alfAutoModelService != null) {
            propsMapping = alfAutoModelService.getPropsMapping(typeRef);
        } else {
            propsMapping = Collections.emptyMap();
        }

        return new PredToFtsContext(
            typeRef,
            rootPredicate,
            propsMapping
        );
    }

    private DataValue mapAlfrescoNodeRefs(DataValue value) {

        if (value.isTextual() && value.asText().startsWith(ALFRESCO_APP_NODE_REF_PREFIX)) {
            return DataValue.createStr(value.asText().replaceFirst(ALFRESCO_APP_NODE_REF_PREFIX, WORKSPACE));
        }
        if (value.isArray()) {
            DataValue newArr = DataValue.createArr();
            value.forEachJ((str, elem) -> newArr.add(str, mapAlfrescoNodeRefs(elem)));
            return newArr;
        }
        return value;
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
