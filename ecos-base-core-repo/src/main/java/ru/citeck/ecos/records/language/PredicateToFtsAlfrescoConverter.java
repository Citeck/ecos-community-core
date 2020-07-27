package ru.citeck.ecos.records.language;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.dictionary.*;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.model.EcosTypeModel;
import ru.citeck.ecos.node.EcosTypeService;
import ru.citeck.ecos.records2.predicate.PredicateService;
import ru.citeck.ecos.records2.predicate.model.*;
import ru.citeck.ecos.records2.querylang.QueryLangConverter;
import ru.citeck.ecos.records2.querylang.QueryLangService;
import ru.citeck.ecos.records2.RecordRef;
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
import static ru.citeck.ecos.records.language.AttributeConstants.*;
import static ru.citeck.ecos.records2.predicate.model.ValuePredicate.Type.CONTAINS;
import static ru.citeck.ecos.records2.predicate.model.ValuePredicate.Type.EQ;

@Component
@Slf4j
public class PredicateToFtsAlfrescoConverter implements QueryLangConverter<Predicate, String> {

    private static final String COMMA_DELIMITER = ",";
    private static final String SLASH_DELIMITER = "/";
    private static final String WORKSPACE_PREFIX = "workspace://SpacesStore/";
    private static final String CURRENT_USER = "$CURRENT";
    private static final int INNER_QUERY_MAX_ITEMS = 20;

    private static final String UNKNOWN_PREDICATE_TYPE = "Unknown predicate type: %s";
    private static final String UNKNOWN_VALUE_PREDICATE_TYPE = "Unknown value predicate type: %s";
    private static final String CANNOT_PARSE_TIME = "Cannot parse time";

    private static final String CM_MODIFIED_ATTRIBUTE = "cm:modified";
    private static final String CM_MODIFIER_ATTRIBUTE = "cm:modifier";
    private static final String WFM_ACTORS_ATTRIBUTE = "wfm:actors";

    private final DictUtils dictUtils;
    private final NodeService nodeService;
    private final SearchService searchService;
    private final AuthorityUtils authorityUtils;
    private final EcosTypeService ecosTypeService;
    private final NamespaceService namespaceService;
    private final AssociationIndexPropertyRegistry associationIndexPropertyRegistry;

    @Autowired
    public PredicateToFtsAlfrescoConverter(DictUtils dictUtils,
                                           SearchService searchService,
                                           QueryLangService queryLangService,
                                           AuthorityUtils authorityUtils, ServiceRegistry serviceRegistry,
                                           AssociationIndexPropertyRegistry associationIndexPropertyRegistry,
                                           NodeService nodeService,
                                           EcosTypeService ecosTypeService) {

        this.dictUtils = dictUtils;
        this.nodeService = nodeService;
        this.searchService = searchService;
        this.authorityUtils = authorityUtils;
        this.ecosTypeService = ecosTypeService;
        this.namespaceService = serviceRegistry.getNamespaceService();
        this.associationIndexPropertyRegistry = associationIndexPropertyRegistry;

        queryLangService.register(this, PredicateService.LANGUAGE_PREDICATE, SearchService.LANGUAGE_FTS_ALFRESCO);
    }

    private void convertPredicate(Predicate predicate, FTSQuery query) {
        if (predicate instanceof ComposedPredicate) {
            convertComposedPredicate(predicate, query);
            return;
        }
        if (predicate instanceof NotPredicate) {
            query.not();
            convertPredicate(((NotPredicate) predicate).getPredicate(), query);
            return;
        }
        if (predicate instanceof ValuePredicate) {
            convertValuePredicate(predicate, query);
            return;
        }
        if (predicate instanceof EmptyPredicate) {
            String attribute = ((EmptyPredicate) predicate).getAttribute();
            consumeQueryField(attribute, query::empty);
            return;
        }
        if (predicate instanceof VoidPredicate) {
            //do nothing
            return;
        }

        throw new RuntimeException(String.format(UNKNOWN_PREDICATE_TYPE, predicate));
    }

    private void convertComposedPredicate(Predicate predicate, FTSQuery query) {
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

            convertPredicate(predicates.get(i), query);
        }

        query.close();
    }

    private void convertValuePredicate(Predicate predicate, FTSQuery query) {
        ValuePredicate valuePred = (ValuePredicate) predicate;
        String attribute = valuePred.getAttribute();

        Object value = valuePred.getValue();
        String valueStr = value.toString();

        switch (attribute) {
            case ALL: {
                query.value(valueStr).and().type(ContentModel.PROP_CONTENT);
                break;
            }
            case PATH: {
                query.path(valueStr);
                break;
            }
            case PARENT:
            case _PARENT: {
                query.parent(new NodeRef(toValidNodeRef(valueStr)));
                break;
            }
            case TYPE:
            case S_TYPE: {
                consumeQName(valueStr, query::type);
                break;
            }
            case _TYPE:
            case _ETYPE: {
                handleETypeAttribute(query, valueStr);
                break;
            }
            case ASPECT:
            case S_ASPECT: {
                consumeQName(valueStr, query::aspect);
                break;
            }
            case IS_NULL: {
                consumeQName(valueStr, query::isNull);
                break;
            }
            case IS_NOT_NULL: {
                consumeQueryField(valueStr, query::isNotNull);
                break;
            }
            case IS_UNSET: {
                consumeQueryField(valueStr, query::isUnset);
                break;
            }
            case MODIFIER: {
                convertValuePredicateCopyForAttr(valuePred, CM_MODIFIER_ATTRIBUTE, query);
                break;
            }
            case MODIFIED: {
                convertValuePredicateCopyForAttr(valuePred, CM_MODIFIED_ATTRIBUTE, query);
                break;
            }
            case ACTORS: {
                String actor = getActorByValue(valueStr);
                convertPredicate(getOrPredicateForActors(actor), query);
                break;
            }
            default: {
                ClassAttributeDefinition attDef = dictUtils.getAttDefinition(attribute);
                QName field = getQueryField(attDef);
                if (field == null) {
                    break;
                }

                ValuePredicate.Type valuePredType = valuePred.getType();

                boolean valueContainsComma = valueStr.contains(COMMA_DELIMITER);
                boolean valueEqualEqOrContainsPredType = EQ.equals(valuePredType) || CONTAINS.equals(valuePredType);

                if (valueContainsComma && valueEqualEqOrContainsPredType) {
                    String[] values = valueStr.split(COMMA_DELIMITER);
                    ComposedPredicate orPredicate = new OrPredicate();
                    for (String s : values) {
                        orPredicate.addPredicate(new ValuePredicate(valuePred.getAttribute(), valuePredType, s));
                    }
                    convertPredicate(orPredicate, query);
                    break;
                }

                if (isNodeRefAtt(attDef)) {
                    valueStr = toValidNodeRef(valueStr);
                }

                String predValue = getPredicateValue(value, valueStr, attDef);
                switch (valuePredType) {
                    case EQ: {
                        query.exact(field, valueStr);
                        return;
                    }
                    case LIKE: {
                        query.value(field, valueStr.replaceAll("%", "*"));
                        return;
                    }
                    case CONTAINS: {
                        if (StringUtils.isEmpty(valueStr)) {
                            return;
                        }

                        if (attDef instanceof PropertyDefinition) {
                            PropertyDefinition propertyDefinition = (PropertyDefinition) attDef;
                            DataTypeDefinition dataType = propertyDefinition.getDataType();
                            QName typeName = dataType != null ? dataType.getName() : null;

                            if (DataTypeDefinition.TEXT.equals(typeName)) {
                                convertContainsTextPredicate(propertyDefinition, query, field, valueStr);
                                return;
                            }
                            if (DataTypeDefinition.MLTEXT.equals(typeName)) {
                                query.value(field, "*" + valueStr + "*");
                                return;
                            }
                            if (DataTypeDefinition.CATEGORY.equals(typeName)) {
                                addNodeRefSearchTerms(query, field, DataTypeDefinition.CATEGORY, valueStr);
                                return;
                            }
                            if (DataTypeDefinition.NODE_REF.equals(typeName)) {
                                addNodeRefSearchTerms(query, field, null, valueStr);
                                return;
                            }

                            query.value(field, valueStr);
                            return;
                        }
                        if (attDef instanceof AssociationDefinition) {
                            if (NodeRef.isNodeRef(valueStr)) {
                                query.value(field, valueStr);
                                return;
                            }

                            ClassDefinition targetType = ((AssociationDefinition) attDef).getTargetClass();
                            addNodeRefSearchTerms(query, field, targetType.getName(), valueStr);
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
        convertPredicate(predCopyForAttr, query);
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

        Map<QName, PropertyDefinition> props = new HashMap<>(targetType.getProperties());
        List<AspectDefinition> definitions = targetType.getDefaultAspects(true);
        definitions.forEach(a -> props.putAll(a.getProperties()));

        return props.values().stream()
            .filter(this::isTextOrMLText)
            .flatMap(def -> {
                Map<QName, Serializable> attributes = new HashMap<>();
                attributes.put(def.getName(), assocVal);
                return attributes.entrySet().stream();
            })
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private boolean isTextOrMLText(PropertyDefinition def) {
        QName dataType = def.getDataType().getName();
        String namespaceURI = def.getName().getNamespaceURI();
        return (DataTypeDefinition.TEXT.equals(dataType) || DataTypeDefinition.MLTEXT.equals(dataType)) &&
            !NamespaceService.SYSTEM_MODEL_1_0_URI.equals(namespaceURI) &&
            !NamespaceService.CONTENT_MODEL_1_0_URI.equals(namespaceURI);
    }

    private boolean isNodeRefAtt(ClassAttributeDefinition attDef) {
        if (attDef == null) {
            return false;
        }

        if (attDef instanceof PropertyDefinition) {
            PropertyDefinition propDef = (PropertyDefinition) attDef;
            DataTypeDefinition dataType = propDef.getDataType();
            if (dataType == null) {
                return false;
            }

            return DataTypeDefinition.NODE_REF.equals(dataType.getName());
        }

        return attDef instanceof AssociationDefinition;
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

    @Override
    public String convert(Predicate predicate) {
        if (predicate instanceof VoidPredicate) {
            return "";
        }

        FTSQuery query = FTSQuery.createRaw();
        convertPredicate(predicate, query);

        return query.getQuery();
    }
}

class AttributeConstants {
    public static final String MODIFIED = "_modified";
    public static final String MODIFIER = "_modifier";
    public static final String ACTORS = "_actors";
    public static final String ALL = "ALL";
    public static final String PATH = "PATH";
    public static final String PARENT = "PARENT";
    public static final String _PARENT = "_parent";
    public static final String TYPE = "TYPE";
    public static final String S_TYPE = "type";
    public static final String _TYPE = "_type";
    public static final String _ETYPE = "_etype";
    public static final String ASPECT = "ASPECT";
    public static final String S_ASPECT = "aspect";
    public static final String IS_NULL = "ISNULL";
    public static final String IS_NOT_NULL = "ISNOTNULL";
    public static final String IS_UNSET = "ISUNSET";
}
