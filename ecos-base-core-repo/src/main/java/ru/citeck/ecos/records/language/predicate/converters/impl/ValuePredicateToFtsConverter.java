package ru.citeck.ecos.records.language.predicate.converters.impl;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.dictionary.*;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.search.QueryConsistency;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.Pair;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.config.EcosConfigService;
import ru.citeck.ecos.model.EcosTypeModel;
import ru.citeck.ecos.node.EcosTypeService;
import ru.citeck.ecos.records.language.predicate.converters.PredicateToFtsConverter;
import ru.citeck.ecos.records.language.predicate.converters.delegators.ConvertersDelegator;
import ru.citeck.ecos.records.language.predicate.converters.impl.utils.TimeUtils;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.predicate.model.*;
import ru.citeck.ecos.search.AssociationIndexPropertyRegistry;
import ru.citeck.ecos.search.ftsquery.BinOperator;
import ru.citeck.ecos.search.ftsquery.FTSQuery;
import ru.citeck.ecos.utils.AuthorityUtils;
import ru.citeck.ecos.utils.DictUtils;

import javax.xml.datatype.Duration;
import java.io.Serializable;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ru.citeck.ecos.model.ClassificationModel.PROP_DOCUMENT_KIND;
import static ru.citeck.ecos.model.ClassificationModel.PROP_DOCUMENT_TYPE;
import static ru.citeck.ecos.records.language.predicate.converters.impl.constants.ValuePredicateToFtsAlfrescoConstants.*;
import static ru.citeck.ecos.records.language.predicate.converters.impl.utils.ValuePredicateToFtsConverterUtils.*;
import static ru.citeck.ecos.records2.predicate.model.ValuePredicate.Type.*;

@Component
@Slf4j
public class ValuePredicateToFtsConverter implements PredicateToFtsConverter {

    private ConvertersDelegator delegator;

    private NodeService nodeService;
    private SearchService searchService;
    private EcosTypeService ecosTypeService;
    private NamespaceService namespaceService;
    private EcosConfigService ecosConfigService;
    private AssociationIndexPropertyRegistry associationIndexPropertyRegistry;

    private DictUtils dictUtils;
    private AuthorityUtils authorityUtils;

    @Override
    public void convert(Predicate predicate, FTSQuery query) {
        ValuePredicate valuePredicate = (ValuePredicate) predicate;
        String attribute = valuePredicate.getAttribute();

        Object objectPredicateValue = valuePredicate.getValue();
        String predicateValue = objectPredicateValue.toString();

        switch (attribute) {
            case ALL: {
                processAllAttribute(query, predicateValue);
                break;
            }
            case PATH: {
                query.path(predicateValue);
                break;
            }
            case PARENT:
            case _PARENT: {
                query.parent(new NodeRef(toValidNodeRef(predicateValue)));
                break;
            }
            case TYPE:
            case S_TYPE: {
                consumeQName(predicateValue, query::type);
                break;
            }
            case _TYPE:
            case _ETYPE: {
                handleETypeAttribute(query, predicateValue);
                break;
            }
            case ASPECT:
            case S_ASPECT: {
                consumeQName(predicateValue, query::aspect);
                break;
            }
            case IS_NULL: {
                consumeQName(predicateValue, query::isNull);
                break;
            }
            case IS_NOT_NULL: {
                consumeQueryField(predicateValue, query::isNotNull);
                break;
            }
            case IS_UNSET: {
                consumeQueryField(predicateValue, query::isUnset);
                break;
            }
            case MODIFIER: {
                convertValuePredicateCopyForAttr(valuePredicate, ContentModel.PROP_MODIFIER.getPrefixString(), query);
                break;
            }
            case MODIFIED: {
                convertValuePredicateCopyForAttr(valuePredicate, ContentModel.PROP_MODIFIED.getPrefixString(), query);
                break;
            }
            case ACTORS: {
                String actor = getActorByValue(predicateValue);
                delegator.delegate(getOrPredicateForActors(actor), query);
                break;
            }
            default: {
                processDefaultAttribute(query, valuePredicate);
                break;
            }
        }
    }

    private void processAllAttribute(FTSQuery query, String value) {
        query.not().value(ContentModel.PROP_CREATOR, SYSTEM)
            .and().not().value(ContentModel.PROP_CREATOR, SYSTEM2)
            .consistency(QueryConsistency.EVENTUAL);

        includeTypesForQuery(query);
        addSearchingPropsToQuery(query, value);
        excludeTypesFromQuery(query);
        excludeAspectsFromQuery(query);
    }

    private void addSearchingPropsToQuery(FTSQuery query, String value) {
        query.and().open();

        List<QName> propsForSearch = getQNameConfigValueDelimitedByComma(SEARCH_PROPS);
        propsForSearch.forEach(prop -> query.contains(prop, value).or());

        query.close();
    }

    private void excludeTypesFromQuery(FTSQuery query) {
        List<QName> excludedTypes = getQNameConfigValueDelimitedByComma(SEARCH_EXCLUDED_TYPES);
        excludedTypes.forEach(type -> query.and().not().type(type));
    }

    private void excludeAspectsFromQuery(FTSQuery query) {
        List<QName> excludedAspects = getQNameConfigValueDelimitedByComma(SEARCH_EXCLUDED_ASPECTS);
        excludedAspects.forEach(aspect -> query.and().not().aspect(aspect));
    }

    private void includeTypesForQuery(FTSQuery query) {
        List<QName> addTypes = getQNameConfigValueDelimitedByComma(SEARCH_ALL_TYPES_INCLUDED);

        if (addTypes.isEmpty()) {
            return;
        }

        query.and().open();
        addTypes.forEach(addType -> query.type(addType).or());
        query.close();
    }


    private void convertContainsTextPredicate(PropertyDefinition propertyDefinition, FTSQuery query, QName field, String value) {
        QName container = propertyDefinition.getContainerClass().getName();

        List<Serializable> values = getPropertyValuesByConstraintsFromField(container, field, value);
        if (values.isEmpty()) {
            query.contains(field, value);
            return;
        }

        query.any(field, values);
    }

    private List<Serializable> getPropertyValuesByConstraintsFromField(QName container, QName field, String inputValue) {
        Map<String, String> mapping = dictUtils.getPropertyDisplayNameMappingWithChildren(container, field);

        return mapping.entrySet().stream().filter(e -> checkValueEqualsToKeyOrValue(e, inputValue))
            .map(Map.Entry::getKey).collect(Collectors.toList());
    }

    private String getPredicateValue(Object value, String valueStr, ClassAttributeDefinition classAttributeDefinition) {
        if (!(value instanceof String)) {
            return valueStr;
        }

        if (!(classAttributeDefinition instanceof PropertyDefinition)) {
            return String.format(QUOTES_STRING_TEMPLATE, valueStr);
        }

        String predicateValue;
        DataTypeDefinition dataTypeDefinition = ((PropertyDefinition) classAttributeDefinition).getDataType();
        if (DataTypeDefinition.DATETIME.equals(dataTypeDefinition.getName())) {
            predicateValue = TimeUtils.convertTime(valueStr, log);
        } else {
            predicateValue = valueStr;
        }

        return String.format(QUOTES_STRING_TEMPLATE, predicateValue);
    }

    private OrPredicate getOrPredicateForActors(String actor) {
        Set<String> actorRefs = getActorsRef(actor);

        OrPredicate orPredicate = new OrPredicate();
        actorRefs.stream()
            .map(actorRef -> new ValuePredicate(WFM_ACTORS_ATTRIBUTE, ValuePredicate.Type.CONTAINS, actorRef))
            .forEach(orPredicate::addPredicate);
        return orPredicate;
    }

    private Set<String> getActorsRef(String actor) {
        if (StringUtils.isBlank(actor)) {
            return Collections.emptySet();
        }

        Set<NodeRef> containingAuthoritiesRefs = authorityUtils.getContainingAuthoritiesRefs(actor);
        NodeRef actorNodeRef = authorityUtils.getNodeRef(actor);

        return Stream.concat(containingAuthoritiesRefs.stream(), Stream.of(actorNodeRef))
            .map(NodeRef::toString).collect(Collectors.toSet());
    }

    private void convertValuePredicateCopyForAttr(ValuePredicate valuePredicate, String attribute, FTSQuery query) {
        ValuePredicate predicateCopy = valuePredicate.copy();
        predicateCopy.setAttribute(attribute);
        delegator.delegate(predicateCopy, query);
    }

    private String getActorByValue(String value) {
        if (CURRENT_USER.equals(value)) {
            return AuthenticationUtil.getFullyAuthenticatedUser();
        }
        if (NodeRef.isNodeRef(value)) {
            return authorityUtils.getAuthorityName(new NodeRef(value));
        }

        return value;
    }

    private void handleETypeAttribute(FTSQuery query, String value) {
        if (StringUtils.isBlank(value)) {
            return;
        }

        RecordRef typeRef = RecordRef.valueOf(value);
        String typeRecId = typeRef.getId();

        String[] typeKindArray = typeRecId.split(SLASH_DELIMITER);
        String documentTypeValue = WORKSPACE_PREFIX + typeKindArray[0];

        String documentKindValue = null;
        if (typeKindArray.length > 1) {
            documentKindValue = WORKSPACE_PREFIX + typeKindArray[1];
        }

        query.open();
        if (nodeService.exists(new NodeRef(documentTypeValue))) {
            query.value(PROP_DOCUMENT_TYPE, documentTypeValue);
        }
        if (StringUtils.isNotEmpty(documentKindValue) && nodeService.exists(new NodeRef(documentKindValue))) {
            query.and().value(PROP_DOCUMENT_KIND, documentKindValue);
        }

        query.or().value(EcosTypeModel.PROP_TYPE, typeRecId);

        ecosTypeService.getDescendantTypes(typeRef).forEach(type ->
            query.or().value(EcosTypeModel.PROP_TYPE, type.getId())
        );

        query.close();
    }

    private void addNodeRefSearchTerms(FTSQuery query, QName field, QName targetTypeName, String value) {
        if (NodeRef.isNodeRef(value)) {
            query.value(field, value);
            return;
        }

        if (field == null) {
            return;
        }

        FTSQuery innerQuery = FTSQuery.createRaw();
        innerQuery.maxItems(INNER_QUERY_MAX_ITEMS);

        Map<QName, Serializable> attributes = new HashMap<>();

        String assocVal = String.format(CONTAINS_STRING_TEMPLATE, value);

        attributes.put(ContentModel.PROP_TITLE, assocVal);
        attributes.put(ContentModel.PROP_NAME, assocVal);

        if (targetTypeName != null) {

            innerQuery.type(targetTypeName);

            if (targetTypeName.equals(ContentModel.TYPE_PERSON)) {
                attributes.put(ContentModel.PROP_USERNAME, assocVal);
                attributes.put(ContentModel.PROP_USER_USERNAME, assocVal);
                attributes.put(ContentModel.PROP_FIRSTNAME, assocVal);
                attributes.put(ContentModel.PROP_LASTNAME, assocVal);
            }

            TypeDefinition targetType = dictUtils.getTypeDefinition(targetTypeName);
            if (targetType != null) {
                QName targetTypeQName = targetType.getName();
                String targetTypeLocalName = targetTypeQName.getLocalName();
                if (CATEGORY_LOCAL_NAME.equals(targetTypeLocalName)) {
                    innerQuery.type(ContentModel.TYPE_CATEGORY);
                }

                attributes.putAll(getTargetTypeAttributes(targetType, assocVal));
            }
        }

        List<NodeRef> assocs = innerQuery
            .and().values(attributes, BinOperator.OR, false)
            .query(searchService);
        if (assocs.size() > 0) {
            query.any(field, new ArrayList<>(assocs));
            return;
        }

        query.value(field, value);
    }

    private void consumeQueryField(String fieldQName, Consumer<QName> consumer) {
        QName qName = getQueryField(dictUtils.getAttDefinition(fieldQName));
        consumeQName(qName, consumer);
    }

    private void consumeQName(String qNameString, Consumer<QName> consumer) {
        consumeQName(resolveQName(qNameString), consumer);
    }

    private void consumeQName(QName qName, Consumer<QName> consumer) {
        if (qName == null) {
            return;
        }

        consumer.accept(qName);
    }

    private QName getQueryField(ClassAttributeDefinition def) {
        if (def == null) {
            return null;
        }

        QName definitionName = def.getName();
        if (def instanceof AssociationDefinition) {
            return associationIndexPropertyRegistry.getAssociationIndexProperty(definitionName);
        }
        return definitionName;
    }

    private List<QName> getQNameConfigValueDelimitedByComma(String key) {
        String searchPropsNames = (String) ecosConfigService.getParamValue(key);
        if (StringUtils.isEmpty(searchPropsNames)) {
            return Collections.emptyList();
        }

        List<String> splitSearchPropsNames = Arrays.asList(searchPropsNames.split(COMMA_DELIMITER));

        return splitSearchPropsNames.stream().distinct()
            .map(this::resolveQName).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private QName resolveQName(String propQName) {
        try {
            return QName.resolveToQName(namespaceService, propQName);
        } catch (Exception e) {
            log.warn(String.format(PROPERTY_NAME_NOT_PARSED, propQName), e);
        }
        return null;
    }

    private void processDefaultAttribute(FTSQuery query, ValuePredicate valuePredicate) {
        String attribute = valuePredicate.getAttribute();
        Object objectPredicateValue = valuePredicate.getValue();
        String predicateValue = objectPredicateValue.toString().replaceAll("\"", "\\\\\"");

        ClassAttributeDefinition attDef = dictUtils.getAttDefinition(attribute);
        QName field = getQueryField(attDef);
        if (field == null) {
            return;
        }

        ValuePredicate.Type valuePredType = valuePredicate.getType();

        ComposedPredicate composedPredicate = getAdvantageComposedPredicate(valuePredType,
                                                                            predicateValue, attribute, attDef);
        if (composedPredicate != null) {
            delegator.delegate(composedPredicate, query);
            return;
        }

        if (isNodeRefAtt(attDef)) {
            predicateValue = toValidNodeRef(predicateValue);
        }

        predicateValue = evalPredicateValue(predicateValue, attDef);
        String predValue = getPredicateValue(objectPredicateValue, predicateValue, attDef);
        switch (valuePredType) {
            case EQ: {
                query.exact(field, predicateValue);
                return;
            }
            case LIKE: {
                query.value(field, predicateValue.replaceAll(PERCENT, STAR));
                return;
            }
            case CONTAINS:
                processValuePredContains(query, predicateValue, field, attDef);
                return;
            case GE: {
                query.range(field, predValue, true, null, false);
                return;
            }
            case GT: {
                query.range(field, predValue, false, null, false);
                return;
            }
            case LE: {
                query.range(field, null, false, predValue, true);
                return;
            }
            case LT: {
                query.range(field, null, false, predValue, false);
                return;
            }
        }
        throw new RuntimeException(String.format(UNKNOWN_VALUE_PREDICATE_TYPE, valuePredType));
    }

    private ComposedPredicate getAdvantageComposedPredicate(ValuePredicate.Type valuePredType, String predicateValue,
                                                            String attribute, ClassAttributeDefinition attDef) {
        boolean valueContainsComma = predicateValue.contains(COMMA_DELIMITER);
        boolean valueEqualEq = EQ.equals(valuePredType);
        boolean valueEqualEqOrContainsPredType =  valueEqualEq || CONTAINS.equals(valuePredType);

        if (valueContainsComma && valueEqualEqOrContainsPredType) {
            ComposedPredicate orPredicate = new OrPredicate();
            Arrays.stream(predicateValue.split(COMMA_DELIMITER))
                .map(value -> new ValuePredicate(attribute, valuePredType, value))
                .forEach(orPredicate::addPredicate);
            return orPredicate;
        }

        boolean valueContainsSlash = predicateValue.contains(SLASH_DELIMITER);
        if (valueEqualEq && valueContainsSlash) {
            ComposedPredicate intervalPredicate = getIntervalPredicate(predicateValue, attribute, attDef);
            if (intervalPredicate != null) {
                return intervalPredicate;
            }
        }

        return null;
    }

    private ComposedPredicate getIntervalPredicate(String predicateValue, String attribute,
                                                   ClassAttributeDefinition attDef) {
        String[] interval = predicateValue.split(SLASH_DELIMITER);

        if (interval.length != 2) {
            return null;
        }

        Pair<String, String> intervalPair = new Pair<>(interval[0], interval[1]);

        DataTypeDefinition dataTypeDefinition = ((PropertyDefinition) attDef).getDataType();
        boolean isDateTime = DataTypeDefinition.DATETIME.equals(dataTypeDefinition.getName());
        boolean isDate = DataTypeDefinition.DATE.equals(dataTypeDefinition.getName());

        if (!isDateTime && !isDate) {
            return null;
        }

        Pair<String, String> newInterval = getDateTimeInterval(intervalPair, isDateTime);

        if (newInterval == null) {
            return null;
        }

        ComposedPredicate andPredicate = new AndPredicate();
        andPredicate.addPredicate(new ValuePredicate(attribute, GE, newInterval.getFirst()));
        andPredicate.addPredicate(new ValuePredicate(attribute, LE, newInterval.getSecond()));

        return andPredicate;
    }

    private Pair<String, String> getDateTimeInterval(Pair<String, String> interval, boolean isDateTime) {
        Pair<String, String> newInterval = new Pair<>(null, null);

        Date date = new Date();
        for (int i = 0; i < 2; i++) {
            boolean isFirstBoundary = i == 0;
            String intervalBoundary = getBoundary(interval, isFirstBoundary);

            date = calcDateByBoundary(intervalBoundary, date);
            if (date == null) {
                return null;
            }

            intervalBoundary = isDateTime
                ? TimeUtils.formatIsoDateTime(date)
                : TimeUtils.formatIsoDate(date);

            setBoundary(newInterval, isFirstBoundary, intervalBoundary);
        }

        return newInterval;
    }

    private String getBoundary(Pair<String, String> interval, boolean isFirst) {
        return isFirst ? interval.getFirst() : interval.getSecond();
    }

    private void setBoundary(Pair<String, String> interval, boolean isFirst, String intervalBoundary) {
        if (isFirst) {
            interval.setFirst(intervalBoundary);
        } else {
            interval.setSecond(intervalBoundary);
        }
    }

    private Date calcDateByBoundary(String intervalBoundary, Date date) {
        Date newDate;
        Duration duration = TimeUtils.parseIsoDuration(intervalBoundary);
        if (duration != null) {
            newDate = new Date(date.getTime());
            duration.addTo(newDate);
        } else {
            newDate = TimeUtils.parseIsoTime(evalConstants(intervalBoundary));
        }

        return newDate;
    }

    private String evalPredicateValue(String predicateValue, ClassAttributeDefinition attDef) {

        if (!(attDef instanceof PropertyDefinition)) {
            return predicateValue;
        }

        DataTypeDefinition dataTypeDefinition = ((PropertyDefinition) attDef).getDataType();
        boolean isDateTime = DataTypeDefinition.DATETIME.equals(dataTypeDefinition.getName());
        boolean isDate = DataTypeDefinition.DATE.equals(dataTypeDefinition.getName());

        if (isDateTime || isDate) {
            predicateValue = evalConstants(predicateValue);
            predicateValue = evalDuration(predicateValue);
        }

        return predicateValue;
    }

    private String evalConstants(String predicateValue) {
        switch (predicateValue) {
            case "$TODAY": {
                return TimeUtils.formatIsoDate(new Date());
            }
            case "$NOW": {
                return TimeUtils.formatIsoDateTime(new Date());
            }
            default:
                return predicateValue;
        }
    }

    private String evalDuration(String predicateValue) {
        Duration duration = TimeUtils.parseIsoDuration(predicateValue);
        if (duration != null) {
            Date date = new Date();
            duration.addTo(date);
            return TimeUtils.formatIsoDateTime(date);
        }

        return predicateValue;
    }

    private void processValuePredContains(FTSQuery query, String predicateValue, QName field,
                                          ClassAttributeDefinition attDef) {
        if (StringUtils.isEmpty(predicateValue)) {
            return;
        }

        if (attDef instanceof PropertyDefinition) {
            PropertyDefinition propertyDefinition = (PropertyDefinition) attDef;
            DataTypeDefinition dataType = propertyDefinition.getDataType();
            QName typeName = dataType != null ? dataType.getName() : null;

            if (DataTypeDefinition.TEXT.equals(typeName)) {
                convertContainsTextPredicate(propertyDefinition, query, field, predicateValue);
                return;
            }
            if (DataTypeDefinition.MLTEXT.equals(typeName)) {
                query.value(field, String.format(CONTAINS_STRING_TEMPLATE, predicateValue));
                return;
            }
            if (DataTypeDefinition.CATEGORY.equals(typeName)) {
                addNodeRefSearchTerms(query, field, DataTypeDefinition.CATEGORY, predicateValue);
                return;
            }
            if (DataTypeDefinition.NODE_REF.equals(typeName)) {
                addNodeRefSearchTerms(query, field, null, predicateValue);
                return;
            }

            query.value(field, predicateValue);
            return;
        }

        if (attDef instanceof AssociationDefinition) {
            if (NodeRef.isNodeRef(predicateValue)) {
                query.value(field, predicateValue);
                return;
            }

            ClassDefinition targetType = ((AssociationDefinition) attDef).getTargetClass();
            addNodeRefSearchTerms(query, field, targetType.getName(), predicateValue);
        }
    }

    @Autowired
    public void setDelegator(ConvertersDelegator delegator) {
        this.delegator = delegator;
    }

    @Autowired
    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    @Autowired
    public void setSearchService(SearchService searchService) {
        this.searchService = searchService;
    }

    @Autowired
    public void setEcosTypeService(EcosTypeService ecosTypeService) {
        this.ecosTypeService = ecosTypeService;
    }

    @Autowired
    public void setNamespaceService(NamespaceService namespaceService) {
        this.namespaceService = namespaceService;
    }

    @Autowired
    public void setEcosConfigService(EcosConfigService ecosConfigService) {
        this.ecosConfigService = ecosConfigService;
    }

    @Autowired
    public void setAssociationIndexPropertyRegistry(AssociationIndexPropertyRegistry associationIndexPropertyRegistry) {
        this.associationIndexPropertyRegistry = associationIndexPropertyRegistry;
    }

    @Autowired
    public void setDictUtils(DictUtils dictUtils) {
        this.dictUtils = dictUtils;
    }

    @Autowired
    public void setAuthorityUtils(AuthorityUtils authorityUtils) {
        this.authorityUtils = authorityUtils;
    }
}
