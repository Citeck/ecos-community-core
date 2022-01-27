package ru.citeck.ecos.records.language.predicate.converters.impl;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.dictionary.constraint.ListOfValuesConstraint;
import org.alfresco.repo.i18n.MessageService;
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
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.config.EcosConfigService;
import ru.citeck.ecos.domain.auth.EcosReqContext;
import ru.citeck.ecos.model.EcosTypeModel;
import ru.citeck.ecos.node.EcosTypeService;
import ru.citeck.ecos.records.language.predicate.converters.AssocToCustomSearchFieldsConfig;
import ru.citeck.ecos.records.language.predicate.converters.PredToFtsContext;
import ru.citeck.ecos.records.language.predicate.converters.PredicateToFtsConverter;
import ru.citeck.ecos.records.language.predicate.converters.delegators.ConvertersDelegator;
import ru.citeck.ecos.records.language.predicate.converters.impl.utils.TimeUtils;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.predicate.model.*;
import ru.citeck.ecos.search.AssociationIndexPropertyRegistry;
import ru.citeck.ecos.search.ftsquery.BinOperator;
import ru.citeck.ecos.search.ftsquery.FTSQuery;
import ru.citeck.ecos.utils.AuthorityUtils;
import ru.citeck.ecos.utils.DictUtils;

import javax.xml.datatype.Duration;
import java.io.Serializable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ru.citeck.ecos.model.ClassificationModel.PROP_DOCUMENT_KIND;
import static ru.citeck.ecos.model.ClassificationModel.PROP_DOCUMENT_TYPE;
import static ru.citeck.ecos.records.language.predicate.converters.impl.constants.ValuePredicateToFtsAlfrescoConstants.*;
import static ru.citeck.ecos.records.language.predicate.converters.impl.utils.ValuePredicateToFtsConverterUtils.*;
import static ru.citeck.ecos.records2.predicate.model.ValuePredicate.Type.*;

@Slf4j
@Component
public class ValuePredicateToFtsConverter implements PredicateToFtsConverter {

    private ConvertersDelegator delegator;

    private NodeService nodeService;
    private MessageService messageService;
    private SearchService searchService;
    private EcosTypeService ecosTypeService;
    private NamespaceService namespaceService;
    private EcosConfigService ecosConfigService;
    private AssociationIndexPropertyRegistry associationIndexPropertyRegistry;
    private final Map<QName, List<QName>> assocToCustomSearchFieldsRegistry = new ConcurrentHashMap<>();

    private DictUtils dictUtils;
    private AuthorityUtils authorityUtils;

    public void registerAssocToCustomSearchFields(QName assocQName, List<QName> searchFields) {
        if (assocQName == null) {
            log.warn("Registration of custom search fields are failed, assocQName is null");
            return;
        }

        if (searchFields == null) {
            searchFields = Collections.emptyList();
        }

        assocToCustomSearchFieldsRegistry.put(assocQName, searchFields);
        log.info("Registered custom search fields '{}' for assoc '{}'", searchFields, assocQName);
    }

    @Override
    public void convert(Predicate predicate, FTSQuery query, PredToFtsContext context) {
        ValuePredicate valuePredicate = (ValuePredicate) predicate;
        String attribute = valuePredicate.getAttribute();

        DataValue objectPredicateValue = valuePredicate.getValue();
        String predicateValue = objectPredicateValue.asText();
        ValuePredicate.Type predicateType = valuePredicate.getType();

        switch (attribute) {
            case ALL:
                processAllAttribute(query, predicateValue);
                break;
            case ECOS_STATUS:
                processEcosStatusAttribute(query, predicateValue);
                break;
            case PATH:
                query.path(predicateValue);
                break;
            case PARENT:
            case _PARENT:
                query.parent(new NodeRef(toValidNodeRef(predicateValue)));
                break;
            case TYPE:
            case S_TYPE:
                consumeQName(predicateValue, query::type);
                break;
            case _TYPE:
            case _ETYPE:
                handleETypeAttribute(query, predicateType, predicateValue);
                break;
            case ASPECT:
            case S_ASPECT:
                consumeQName(predicateValue, query::aspect);
                break;
            case IS_NULL:
                consumeQName(predicateValue, query::isNull);
                break;
            case IS_NOT_NULL:
                consumeQueryField(predicateValue, query::isNotNull);
                break;
            case IS_UNSET:
                consumeQueryField(predicateValue, query::isUnset);
                break;
            case RecordConstants.ATT_MODIFIER:
            case RecordConstants.ATT_MODIFIED:
            case RecordConstants.ATT_CREATOR:
            case RecordConstants.ATT_CREATED:
                QName attQName;
                switch (attribute) {
                    case RecordConstants.ATT_MODIFIER:
                        attQName = ContentModel.PROP_MODIFIER;
                        break;
                    case RecordConstants.ATT_MODIFIED:
                        attQName = ContentModel.PROP_MODIFIED;
                        break;
                    case RecordConstants.ATT_CREATOR:
                        attQName = ContentModel.PROP_CREATOR;
                        break;
                    case RecordConstants.ATT_CREATED:
                        attQName = ContentModel.PROP_CREATED;
                        break;
                    default:
                        throw new IllegalStateException("Incorrect audit attribute: " + attribute);
                }
                convertValuePredicateCopyForAttr(
                    valuePredicate,
                    attQName.toPrefixString(namespaceService),
                    query,
                    context
                );
                break;
            case ACTORS:
                String actor = getActorByValue(predicateValue);
                delegator.delegate(getOrPredicateForActors(actor), query, context);
                break;
            default:
                if (IN.equals(valuePredicate.getType())) {
                    processInAttribute(query, valuePredicate, context);
                } else {
                    processDefaultAttribute(query, valuePredicate, context);
                }
                break;
        }
    }

    private void processInAttribute(FTSQuery query, ValuePredicate valuePredicate, PredToFtsContext context) {

        DataValue value = valuePredicate.getValue();
        List<DataValue> elements = new ArrayList<>();

        if (value.isArray()) {
            for (DataValue arrValue : value) {
                elements.add(arrValue);
            }
        } else {
            elements.add(value);
        }

        List<DataValue> expandedElements = new ArrayList<>();

        for (DataValue elem : elements) {
            if (elem.isTextual()) {
                String elemStr = elem.asText();
                if ("$CURRENT_AUTHORITIES_REFS".equals(elemStr)) {
                    authorityUtils.getUserAuthoritiesRefs()
                        .forEach(auth -> expandedElements.add(DataValue.createStr(auth.toString())));
                } else {
                    expandedElements.add(elem);
                }
            } else {
                expandedElements.add(elem);
            }
        }

        query.open();
        expandedElements.forEach(elem -> {
            ValuePredicate newPred = new ValuePredicate(valuePredicate.getAttribute(), EQ, elem);
            processDefaultAttribute(query, newPred, context);
            query.or();
        });
        query.close();
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

    private void processEcosStatusAttribute(FTSQuery query, String value) {
        ClassAttributeDefinition attDef = dictUtils.getAttDefinition(ASSOC_CASE_STATUS.toString());
        QName caseStatusAssocField = getQueryField(attDef);
        Optional<NodeRef> statusByName = getStatusByName(value);

        query.open();
        statusByName.ifPresent(status -> query.exact(caseStatusAssocField, status).or());
        query.exact(ASSOC_CASE_STATUS_PROP, value).close();
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

    private void convertValuePredicateCopyForAttr(ValuePredicate valuePredicate,
                                                  String attribute,
                                                  FTSQuery query,
                                                  PredToFtsContext context) {

        ValuePredicate predicateCopy = valuePredicate.copy();
        predicateCopy.setAttribute(attribute);
        delegator.delegate(predicateCopy, query, context);
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

    private void handleETypeAttribute(FTSQuery query, ValuePredicate.Type predicateType, String value) {
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

        query.or().value(EcosTypeModel.PROP_TYPE, typeRecId, EQ.equals(predicateType));

        ecosTypeService.getDescendantTypes(typeRef).forEach(type ->
            query.or().value(EcosTypeModel.PROP_TYPE, type.getId(), EQ.equals(predicateType))
        );

        query.close();
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

    private void processDefaultAttribute(FTSQuery query, ValuePredicate valuePredicate, PredToFtsContext context) {

        String attribute = valuePredicate.getAttribute();
        attribute = context.getAttsMapping().getOrDefault(attribute, attribute);

        DataValue objectPredicateValue = valuePredicate.getValue();
        String predicateValue = objectPredicateValue.asText().replaceAll("\"", "\\\\\"");

        ClassAttributeDefinition attDef = dictUtils.getAttDefinition(attribute);
        QName field = getQueryField(attDef);
        if (field == null) {
            return;
        }

        ValuePredicate.Type valuePredType = valuePredicate.getType();

        ComposedPredicate composedPredicate = getAdvantageComposedPredicate(valuePredType,
            predicateValue, attribute, attDef);
        if (composedPredicate != null) {
            delegator.delegate(composedPredicate, query, context);
            return;
        }

        if (isNodeRefAtt(attDef)) {
            predicateValue = toValidNodeRef(predicateValue);
        }

        predicateValue = evalPredicateValue(predicateValue, attDef);
        String predValue = getPredicateValue(objectPredicateValue, predicateValue, attDef);
        switch (valuePredType) {
            case EQ:
                processExactValuePredicate(query, predicateValue, field, attDef);
                return;
            case LIKE:
                processLikeValuePredicate(query, predicateValue, field, attDef);
                return;
            case CONTAINS:
                processContainsValuePredicate(query, predicateValue, field, attDef);
                return;
            case GE:
                query.range(field, predValue, true, null, false);
                return;
            case GT:
                query.range(field, predValue, false, null, false);
                return;
            case LE:
                query.range(field, null, false, predValue, true);
                return;
            case LT:
                query.range(field, null, false, predValue, false);
                return;
        }
        throw new RuntimeException(String.format(UNKNOWN_VALUE_PREDICATE_TYPE, valuePredType));
    }

    private ComposedPredicate getAdvantageComposedPredicate(ValuePredicate.Type valuePredType, String predicateValue,
                                                            String attribute, ClassAttributeDefinition attDef) {

        boolean valueContainsComma = predicateValue.contains(COMMA_DELIMITER);
        boolean valueEqualEq = EQ.equals(valuePredType);
        boolean valueEqualEqOrContainsPredType = valueEqualEq || CONTAINS.equals(valuePredType);

        if (valueContainsComma && valueEqualEqOrContainsPredType) {
            ComposedPredicate orPredicate = new OrPredicate();
            Arrays.stream(predicateValue.split(COMMA_DELIMITER))
                .map(value -> new ValuePredicate(attribute, valuePredType, value))
                .forEach(orPredicate::addPredicate);
            return orPredicate;
        }

        if (TODAY.equals(predicateValue) && Boolean.TRUE.equals(isDateWithTimeAtt(attDef))) {
            int utcOffset = (int) (EcosReqContext.getUtcOffset() * 60);
            predicateValue = Instant.now()
                .plus(utcOffset, ChronoUnit.MINUTES)
                .truncatedTo(ChronoUnit.DAYS)
                .minus(utcOffset, ChronoUnit.MINUTES).toString() + "/P1D";
        }

        boolean valueContainsSlash = predicateValue.contains(SLASH_DELIMITER);
        if (valueEqualEq && valueContainsSlash) {
            return getIntervalPredicate(predicateValue, attribute, attDef);
        }

        if (attDef instanceof PropertyDefinition
            && ValuePredicate.Type.CONTAINS.equals(valuePredType)
            && StringUtils.isNotBlank(predicateValue)) {

            ListOfValuesConstraint constraint = dictUtils.getListOfValuesConstraint((PropertyDefinition) attDef);

            if (constraint != null && !constraint.getAllowedValues().isEmpty()) {

                String lowercaseValue = predicateValue.toLowerCase();
                List<Predicate> values = new ArrayList<>();

                for (String value : constraint.getAllowedValues()) {

                    if (Objects.equals(value, predicateValue)) {
                        values.clear();
                        break;
                    }

                    String dispName = constraint.getDisplayLabel(value, messageService);

                    if (value.toLowerCase().contains(lowercaseValue)
                        || StringUtils.isNotBlank(dispName)
                        && dispName.toLowerCase().contains(lowercaseValue)) {

                        values.add(ValuePredicate.eq(attribute, value));
                    }
                }

                if (!values.isEmpty()) {
                    return OrPredicate.of(values);
                }
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

        Boolean isDateWithTime = isDateWithTimeAtt(attDef);
        if (isDateWithTime == null) {
            return null;
        }
        Pair<String, String> newInterval = getDateTimeInterval(intervalPair, isDateWithTime);

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

    private void processExactValuePredicate(FTSQuery query, String predicateValue, QName field,
                                            ClassAttributeDefinition attDef) {

        if (attDef instanceof AssociationDefinition) {
            if (NodeRef.isNodeRef(predicateValue)) {
                processNodeRefAssocTerm(query, predicateValue, field, attDef);
                return;
            }

            ClassDefinition targetType = ((AssociationDefinition) attDef).getTargetClass();
            List<NodeRef> innerValues = searchInnerValues(field, targetType.getName(), predicateValue, true);
            if (innerValues.size() > 0) {
                query.any(field, new ArrayList<>(innerValues));
            } else {
                query.alwaysFalse();
            }
        } else {
            query.exact(field, predicateValue);
        }
    }

    private void processLikeValuePredicate(FTSQuery query, String predicateValue, QName field,
                                           ClassAttributeDefinition attDef) {

        predicateValue = predicateValue.replaceAll(PERCENT, STAR);

        if (attDef instanceof AssociationDefinition) {
            if (NodeRef.isNodeRef(predicateValue)) {
                processNodeRefAssocTerm(query, predicateValue, field, attDef);
                return;
            }

            ClassDefinition targetType = ((AssociationDefinition) attDef).getTargetClass();
            List<NodeRef> innerValues = searchInnerValues(field, targetType.getName(), predicateValue, false);
            if (innerValues.size() > 0) {
                query.any(field, new ArrayList<>(innerValues), true);
            } else {
                query.alwaysFalse();
            }
        } else {
            query.value(field, predicateValue);
        }
    }

    private void processContainsValuePredicate(FTSQuery query, String predicateValue, QName field,
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
                addContainsNodeRefSearchTerms(query, field, ContentModel.TYPE_CATEGORY, predicateValue);
                return;
            }
            if (DataTypeDefinition.NODE_REF.equals(typeName)) {
                addContainsNodeRefSearchTerms(query, field, null, predicateValue);
                return;
            }

            query.value(field, predicateValue);
            return;
        }

        if (attDef instanceof AssociationDefinition) {

            if (NodeRef.isNodeRef(predicateValue)) {
                processNodeRefAssocTerm(query, predicateValue, field, attDef);
                return;
            }

            ClassDefinition targetType = ((AssociationDefinition) attDef).getTargetClass();
            addContainsNodeRefSearchTerms(query, field, targetType.getName(), predicateValue);
        }
    }

    private void processNodeRefAssocTerm(FTSQuery query, String predicateValue, QName field, ClassAttributeDefinition attDef) {
        QName qName = attDef.getName();
        if (qName.equals(ASSOC_CASE_STATUS)) {
            queryCaseStatus(query, predicateValue, field);
        } else {
            query.value(field, predicateValue);
        }
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

    private void addContainsNodeRefSearchTerms(FTSQuery query, QName field, QName targetTypeName, String value) {
        if (field == null) {
            return;
        }

        if (NodeRef.isNodeRef(value)) {
            query.value(field, value);
            return;
        }

        String assocVal = String.format(CONTAINS_STRING_TEMPLATE, value);

        List<NodeRef> assocs = searchInnerValues(field, targetTypeName, assocVal, false);
        if (assocs.size() > 0) {
            query.any(field, new ArrayList<>(assocs));
        } else {
            query.alwaysFalse();
        }
    }

    private List<NodeRef> searchInnerValues(QName field, QName targetTypeName, String value, boolean exact) {
        FTSQuery innerQuery = FTSQuery.createRaw();
        innerQuery.maxItems(INNER_QUERY_MAX_ITEMS);
        if (targetTypeName != null) {
            innerQuery.type(targetTypeName);
        }

        Map<QName, Serializable> attributes = new HashMap<>();

        List<QName> customSearchFields = assocToCustomSearchFieldsRegistry.get(field);
        if (customSearchFields != null && !customSearchFields.isEmpty()) {
            for (QName customSearchField : customSearchFields) {
                attributes.put(customSearchField, value);
            }
        } else {
            attributes.put(ContentModel.PROP_TITLE, value);
            attributes.put(ContentModel.PROP_NAME, value);

            if (targetTypeName != null) {

                if (targetTypeName.equals(ContentModel.TYPE_PERSON)) {
                    attributes.put(ContentModel.PROP_USERNAME, value);
                    attributes.put(ContentModel.PROP_USER_USERNAME, value);
                    attributes.put(ContentModel.PROP_FIRSTNAME, value);
                    attributes.put(ContentModel.PROP_LASTNAME, value);
                }

                if (targetTypeName.equals(ContentModel.TYPE_AUTHORITY_CONTAINER)) {
                    attributes.put(ContentModel.PROP_AUTHORITY_DISPLAY_NAME, value);
                }

                TypeDefinition targetType = dictUtils.getTypeDefinition(targetTypeName);
                if (targetType != null) {
                    attributes.putAll(getTargetTypeAttributes(targetType, value));
                }
            }
        }

        return innerQuery
            .and().values(attributes, BinOperator.OR, exact)
            .query(searchService);
    }

    private String getPredicateValue(DataValue value, String valueStr, ClassAttributeDefinition classAttributeDefinition) {
        if (!value.isTextual()) {
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
            case TODAY:
                int utcOffset = (int) (EcosReqContext.getUtcOffset() * 60);
                return TimeUtils.formatIsoDate(Date.from(Instant.now().plus(utcOffset, ChronoUnit.MINUTES)));
            case "$NOW":
                return TimeUtils.formatIsoDateTime(new Date());
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

    private void queryCaseStatus(FTSQuery query, String predicateValue, QName field) {
        NodeRef nodeRef = new NodeRef(predicateValue);
        if (predicateValue.startsWith(WORKSPACE_PREFIX)) {
            String name = (String) nodeService.getProperty(nodeRef, ContentModel.PROP_NAME);
            query.open()
                .exact(field, predicateValue).or()
                .exact(ASSOC_CASE_STATUS_PROP, name)
                .close();
        } else {
            query.open().exact(ASSOC_CASE_STATUS_PROP, nodeRef.getId());
            getStatusByName(nodeRef.getId()).ifPresent(ref -> query.or().exact(field, ref));
            query.close();
        }
    }

    private Optional<NodeRef> getStatusByName(String statusName) {
        return FTSQuery.create().type(TYPE_CASE_STATUS).and()
            .exact(ContentModel.PROP_NAME, statusName)
            .transactional()
            .queryOne(searchService);
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

    /**
     * @return true if attDef is DateTime attribute
     * false if attDef is Date attribute
     * null if attDef is not Date or DateTime attribute
     */
    @Nullable
    private Boolean isDateWithTimeAtt(ClassAttributeDefinition attDef) {

        if (!(attDef instanceof PropertyDefinition)) {
            return null;
        }

        DataTypeDefinition dataTypeDefinition = ((PropertyDefinition) attDef).getDataType();
        if (DataTypeDefinition.DATETIME.equals(dataTypeDefinition.getName())) {
            return true;
        } else if (DataTypeDefinition.DATE.equals(dataTypeDefinition.getName())) {
            return false;
        }
        return null;
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

    @Autowired
    public void setMessageService(MessageService messageService) {
        this.messageService = messageService;
    }

    @Autowired(required = false)
    public void setAssocToCustomSearchFieldsConfigs(List<AssocToCustomSearchFieldsConfig> configs) {
        if (configs == null) {
            return;
        }

        for (AssocToCustomSearchFieldsConfig config : configs) {
            registerAssocToCustomSearchFields(config.getAssocQName(), config.getCustomSearchFields());
        }
    }
}
