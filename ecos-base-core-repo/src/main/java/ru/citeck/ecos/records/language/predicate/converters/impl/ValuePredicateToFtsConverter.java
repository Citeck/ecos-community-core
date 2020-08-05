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
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.config.EcosConfigService;
import ru.citeck.ecos.model.EcosTypeModel;
import ru.citeck.ecos.node.EcosTypeService;
import ru.citeck.ecos.records.language.predicate.converters.PredicateToFtsConverter;
import ru.citeck.ecos.records.language.predicate.converters.delegators.ConvertersDelegator;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.predicate.model.ComposedPredicate;
import ru.citeck.ecos.records2.predicate.model.OrPredicate;
import ru.citeck.ecos.records2.predicate.model.Predicate;
import ru.citeck.ecos.records2.predicate.model.ValuePredicate;
import ru.citeck.ecos.search.AssociationIndexPropertyRegistry;
import ru.citeck.ecos.search.ftsquery.BinOperator;
import ru.citeck.ecos.search.ftsquery.FTSQuery;
import ru.citeck.ecos.utils.AuthorityUtils;
import ru.citeck.ecos.utils.DictUtils;

import java.io.Serializable;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ru.citeck.ecos.model.ClassificationModel.PROP_DOCUMENT_KIND;
import static ru.citeck.ecos.model.ClassificationModel.PROP_DOCUMENT_TYPE;
import static ru.citeck.ecos.records.language.predicate.converters.impl.constants.ValuePredicateToFtsAlfrescoConstants.*;
import static ru.citeck.ecos.records2.predicate.model.ValuePredicate.Type.CONTAINS;
import static ru.citeck.ecos.records2.predicate.model.ValuePredicate.Type.EQ;

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
                createQueryForAttributeAll(query, predicateValue);
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
                convertValuePredicateCopyForAttr(valuePredicate, CM_MODIFIER_ATTRIBUTE, query);
                break;
            }
            case MODIFIED: {
                convertValuePredicateCopyForAttr(valuePredicate, CM_MODIFIED_ATTRIBUTE, query);
                break;
            }
            case ACTORS: {
                String actor = getActorByValue(predicateValue);
                delegator.delegate(getOrPredicateForActors(actor), query);
                break;
            }
            default: {
                ClassAttributeDefinition attDef = dictUtils.getAttDefinition(attribute);
                QName field = getQueryField(attDef);
                if (field == null) {
                    break;
                }

                ValuePredicate.Type valuePredType = valuePredicate.getType();

                boolean valueContainsComma = predicateValue.contains(COMMA_DELIMITER);
                boolean valueEqualEqOrContainsPredType = EQ.equals(valuePredType) || CONTAINS.equals(valuePredType);

                if (valueContainsComma && valueEqualEqOrContainsPredType) {
                    List<String> values = Arrays.asList(predicateValue.split(COMMA_DELIMITER));
                    ComposedPredicate orPredicate = new OrPredicate();

                    for (String s : values) {
                        orPredicate.addPredicate(new ValuePredicate(valuePredicate.getAttribute(), valuePredType, s));
                    }
                    delegator.delegate(orPredicate, query);
                    break;
                }

                if (isNodeRefAtt(attDef)) {
                    predicateValue = toValidNodeRef(predicateValue);
                }

                String predValue = getPredicateValue(objectPredicateValue, predicateValue, attDef);
                switch (valuePredType) {
                    case EQ: {
                        query.exact(field, predicateValue);
                        return;
                    }
                    case LIKE: {
                        query.value(field, predicateValue.replaceAll("%", "*"));
                        return;
                    }
                    case CONTAINS: {
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
                                query.value(field, "*" + predicateValue + "*");
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
                        return;
                    }
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
        }
    }

    private void createQueryForAttributeAll(FTSQuery query, String value) {
        query.type(ContentModel.TYPE_CONTENT)
            .and().not().value(ContentModel.PROP_CREATOR, SYSTEM)
            .consistency(QueryConsistency.EVENTUAL);

        addSearchingPropsToQuery(query, value);
        excludeTypesFromQuery(query);
        excludeAspectsFromQuery(query);
    }

    private void addSearchingPropsToQuery(FTSQuery query, String value) {
        query.and().open();

        List<QName> propsForSearch = getQNameConfigValueDelimitedByComma(SEARCH_PROPS);
        propsForSearch.forEach(prop -> query.containsValue(prop, value).or());

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

    private void convertContainsTextPredicate(PropertyDefinition propertyDefinition, FTSQuery query, QName field, String value) {
        QName container = propertyDefinition.getContainerClass().getName();

        List<Serializable> values = getPropertyValuesByConstraintsFromField(container, field, value);
        if (values.isEmpty()) {
            query.value(field, "*" + value + "*");
            return;
        }

        query.any(field, values);
    }

    private String getPredicateValue(Object value, String valueStr, ClassAttributeDefinition attDef) {
        String predValue = null;
        if (value instanceof String) {
            if (attDef instanceof PropertyDefinition) {
                DataTypeDefinition type = ((PropertyDefinition) attDef).getDataType();
                if (DataTypeDefinition.DATETIME.equals(type.getName())) {
                    predValue = convertTime(valueStr);
                }
            }
            return "\"" + (predValue != null ? predValue : valueStr) + "\"";
        }

        return valueStr;
    }

    private OrPredicate getOrPredicateForActors(String actor) {
        Set<String> actorRefs = Stream.concat(
            authorityUtils.getContainingAuthoritiesRefs(actor).stream(),
            Stream.of(authorityUtils.getNodeRef(actor)))
            .map(NodeRef::toString).collect(Collectors.toSet());

        OrPredicate orPredicate = new OrPredicate();
        actorRefs.forEach(a -> {
            ValuePredicate valuePredicate = new ValuePredicate();
            valuePredicate.setType(ValuePredicate.Type.CONTAINS);
            valuePredicate.setAttribute(WFM_ACTORS_ATTRIBUTE);
            valuePredicate.setValue(a);
            orPredicate.addPredicate(valuePredicate);
        });

        return orPredicate;
    }

    private void convertValuePredicateCopyForAttr(ValuePredicate valuePredicate, String attribute, FTSQuery query) {
        ValuePredicate predCopyForAttr = valuePredicate.copy();
        predCopyForAttr.setAttribute(attribute);
        delegator.delegate(predCopyForAttr, query);
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

    private List<Serializable> getPropertyValuesByConstraintsFromField(QName container, QName field, String inputValue) {

        Map<String, String> mapping = dictUtils.getPropertyDisplayNameMappingWithChildren(container, field);

        return mapping.entrySet().stream()
            .filter(e -> checkValueEqualsToKeyOrValue(e, inputValue))
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    private boolean checkValueEqualsToKeyOrValue(Map.Entry<String, String> entry, String inputValue) {
        String inputInLowerCase = inputValue.toLowerCase();
        String key = entry.getKey().toLowerCase();
        String value = entry.getValue().toLowerCase();
        return key.contains(inputInLowerCase) || value.contains(inputInLowerCase);
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

        String assocVal = "*" + value + "*";

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

                if (targetType.getName().getLocalName().equals("category")) {
                    innerQuery.type(ContentModel.TYPE_CATEGORY);
                }

                attributes.putAll(getTargetTypeAttributes(targetType, assocVal));
            }
        }

        innerQuery.and().values(attributes, BinOperator.OR, false);

        List<NodeRef> assocs = innerQuery.query(searchService);
        if (assocs.size() > 0) {
            query.any(field, new ArrayList<>(assocs));
            return;
        }

        query.value(field, value);
    }

    private Map<QName, Serializable> getTargetTypeAttributes(TypeDefinition targetType, String assocVal) {
        List<PropertyDefinition> propertyDefinitions = getPropertyDefinitions(targetType);

        return propertyDefinitions.stream()
            .filter(this::isTextOrMLText)
            .flatMap(def -> Collections.singletonMap(def.getName(), assocVal).entrySet().stream())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private List<PropertyDefinition> getPropertyDefinitions(TypeDefinition targetType) {
        List<PropertyDefinition> propertyDefinitions = new ArrayList<>(targetType.getProperties().values());

        List<AspectDefinition> definitions = targetType.getDefaultAspects(true);
        definitions.forEach(a -> propertyDefinitions.addAll(a.getProperties().values()));

        return propertyDefinitions;
    }

    private boolean isTextOrMLText(PropertyDefinition def) {
        QName dataType = def.getDataType().getName();
        String namespaceURI = def.getName().getNamespaceURI();

        boolean isTextOrMLText = DataTypeDefinition.TEXT.equals(dataType) || DataTypeDefinition.MLTEXT.equals(dataType);
        boolean isSystemProperty = NamespaceService.SYSTEM_MODEL_1_0_URI.equals(namespaceURI);
        boolean isContentProperty = NamespaceService.CONTENT_MODEL_1_0_URI.equals(namespaceURI);

        return isTextOrMLText && !isSystemProperty && !isContentProperty;
    }

    private boolean isNodeRefAtt(ClassAttributeDefinition attDef) {
        if (attDef == null) {
            return false;
        }

        if (attDef instanceof AssociationDefinition) {
            return true;
        }

        if (!(attDef instanceof PropertyDefinition)) {
            return false;
        }

        PropertyDefinition propDef = (PropertyDefinition) attDef;
        DataTypeDefinition dataType = propDef.getDataType();
        if (dataType == null) {
            return false;
        }

        return DataTypeDefinition.NODE_REF.equals(dataType.getName());
    }

    private String toValidNodeRef(String value) {
        int idx = value.lastIndexOf("@workspace://");
        if (idx > -1 && idx < value.length() - 1) {
            value = value.substring(idx + 1);
        }
        return value;
    }

    private String convertTime(String time) {
        ZoneOffset offset = OffsetDateTime.now().getOffset();
        if (!StringUtils.contains(time, 'Z') || offset.getTotalSeconds() == 0) {
            return time;
        }

        try {
            Instant timeInstant = Instant.parse(time);
            return DateTimeFormatter.ISO_ZONED_DATE_TIME.format(timeInstant.atZone(offset));
        } catch (Exception e) {
            log.error(CANNOT_PARSE_TIME, e);
            return time;
        }
    }

    private void consumeQueryField(String field, Consumer<QName> consumer) {
        QName attQName = getQueryField(dictUtils.getAttDefinition(field));
        if (attQName != null) {
            consumer.accept(attQName);
        }
    }

    private void consumeQName(String qname, Consumer<QName> consumer) {
        QName qName = QName.resolveToQName(namespaceService, qname);
        if (qName != null) {
            consumer.accept(qName);
        }
    }

    private QName getQueryField(ClassAttributeDefinition def) {
        if (def == null) {
            return null;
        }

        if (def instanceof AssociationDefinition) {
            return associationIndexPropertyRegistry.getAssociationIndexProperty(def.getName());
        }
        return def.getName();
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
            log.warn("propName: " + propQName + " didn't parse. ", e);
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
}
