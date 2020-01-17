package ru.citeck.ecos.records.language;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.alfresco.model.ContentModel;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.dictionary.*;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.predicate.PredicateService;
import ru.citeck.ecos.predicate.model.*;
import ru.citeck.ecos.querylang.QueryLangConverter;
import ru.citeck.ecos.querylang.QueryLangService;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.search.AssociationIndexPropertyRegistry;
import ru.citeck.ecos.search.ftsquery.BinOperator;
import ru.citeck.ecos.search.ftsquery.FTSQuery;
import ru.citeck.ecos.utils.DictUtils;

import java.io.Serializable;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static ru.citeck.ecos.model.ClassificationModel.PROP_DOCUMENT_KIND;
import static ru.citeck.ecos.model.ClassificationModel.PROP_DOCUMENT_TYPE;
import static ru.citeck.ecos.predicate.model.ValuePredicate.Type.CONTAINS;
import static ru.citeck.ecos.predicate.model.ValuePredicate.Type.EQ;

@Component
public class PredicateToFtsAlfrescoConverter implements QueryLangConverter {

    private static final Log logger = LogFactory.getLog(PredicateToFtsAlfrescoConverter.class);
    private static final String COMMA_DELIMITER = ",";
    private static final String SLASH_DELIMITER = "/";
    private static final String WORKSPACE_PREFIX = "workspace://SpacesStore/";
    private static final int INNER_QUERY_MAX_ITEMS = 20;

    private final DictUtils dictUtils;
    private final SearchService searchService;
    private final PredicateService predicateService;
    private final NamespaceService namespaceService;
    private final AssociationIndexPropertyRegistry associationIndexPropertyRegistry;

    @Autowired
    public PredicateToFtsAlfrescoConverter(DictUtils dictUtils,
                                           SearchService searchService,
                                           QueryLangService queryLangService,
                                           PredicateService predicateService,
                                           ServiceRegistry serviceRegistry,
                                           AssociationIndexPropertyRegistry associationIndexPropertyRegistry) {

        this.dictUtils = dictUtils;
        this.searchService = searchService;
        this.predicateService = predicateService;
        this.namespaceService = serviceRegistry.getNamespaceService();
        this.associationIndexPropertyRegistry = associationIndexPropertyRegistry;

        queryLangService.register(this,
                PredicateService.LANGUAGE_PREDICATE,
                SearchService.LANGUAGE_FTS_ALFRESCO);
    }

    private void processPredicate(Predicate predicate, FTSQuery query) {

        if (predicate instanceof ComposedPredicate) {

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

                processPredicate(predicates.get(i), query);
            }

            query.close();

        } else if (predicate instanceof NotPredicate) {

            query.not();
            processPredicate(((NotPredicate) predicate).getPredicate(), query);

        } else if (predicate instanceof ValuePredicate) {

            ValuePredicate valuePred = (ValuePredicate) predicate;
            String attribute = valuePred.getAttribute();
            Object value = valuePred.getValue();
            String valueStr = value.toString();

            switch (attribute) {
                case "PATH":

                    query.path(valueStr);

                    break;
                case "PARENT":
                case "_parent":

                    query.parent(new NodeRef(toValidNodeRef(valueStr)));

                    break;
                case "TYPE":
                case "_type":
                    consumeQName(valueStr, query::type);
                    break;
                case "_etype":
                    handleETypeAttribute(query, valueStr);
                    break;
                case "ASPECT":
                case "aspect":

                    consumeQName(valueStr, query::aspect);

                    break;
                case "ISNULL":

                    consumeQName(valueStr, query::isNull);

                    break;
                case "ISNOTNULL":

                    consumeQueryField(valueStr, query::isNotNull);

                    break;
                case "ISUNSET":

                    consumeQueryField(valueStr, query::isUnset);

                    break;
                default:

                    ClassAttributeDefinition attDef = dictUtils.getAttDefinition(attribute);
                    QName field = getQueryField(attDef);

                    if (field == null) {
                        break;
                    }

                    // accepting multiple values by comma
                    if (valueStr.contains(COMMA_DELIMITER) &&
                            (valuePred.getType().equals(EQ) || valuePred.getType().equals(CONTAINS))) {
                        String[] values = valueStr.split(COMMA_DELIMITER);
                        ComposedPredicate orPredicate = new OrPredicate();
                        for (String s : values) {
                            orPredicate.addPredicate(new ValuePredicate(valuePred.getAttribute(), valuePred.getType(), s));
                        }
                        processPredicate(orPredicate, query);
                        break;
                    }

                    if (isNodeRefAtt(attDef)) {
                        valueStr = toValidNodeRef(valueStr);
                    }

                    switch (valuePred.getType()) {
                        case EQ:
                            query.exact(field, valueStr);
                            break;
                        case LIKE:
                            query.value(field, valueStr.replaceAll("%", "*"));
                            break;
                        case CONTAINS:

                            if (valueStr == null || valueStr.isEmpty()) {
                                return;
                            }

                            if (attDef instanceof PropertyDefinition) {

                                PropertyDefinition propertyDefinition = (PropertyDefinition) attDef;
                                DataTypeDefinition dataType = propertyDefinition.getDataType();
                                QName typeName = dataType != null ? dataType.getName() : null;

                                if (DataTypeDefinition.TEXT.equals(typeName)) {
                                    QName container = propertyDefinition.getContainerClass().getName();

                                    List<String> values = this.getPropertyValuesByConstraintsFromField(container,
                                            field, valueStr);
                                    if (values.size() != 0) {
                                        query.any(field, new ArrayList<>(values));
                                    } else {
                                        query.value(field, "*" + valueStr + "*");
                                    }
                                } else if (DataTypeDefinition.MLTEXT.equals(typeName)) {

                                    query.value(field, "*" + valueStr + "*");
                                } else if (DataTypeDefinition.CATEGORY.equals(typeName)) {
                                    addNodeRefSearchTerms(query, field, DataTypeDefinition.CATEGORY, valueStr);
                                } else if (DataTypeDefinition.NODE_REF.equals(typeName)) {
                                    addNodeRefSearchTerms(query, field, null, valueStr);
                                } else {
                                    query.value(field, valueStr);
                                }

                            } else if (attDef instanceof AssociationDefinition) {

                                if (NodeRef.isNodeRef(valueStr)) {
                                    query.value(field, valueStr);
                                } else {
                                    ClassDefinition targetType = ((AssociationDefinition) attDef).getTargetClass();
                                    addNodeRefSearchTerms(query, field, targetType.getName(), valueStr);
                                }
                            }
                            break;
                        case GE:
                        case GT:
                        case LE:
                        case LT:

                            String predValue = null;
                            if (value instanceof String) {
                                if (attDef instanceof PropertyDefinition) {
                                    DataTypeDefinition type = ((PropertyDefinition) attDef).getDataType();
                                    if (DataTypeDefinition.DATETIME.equals(type.getName())) {
                                        predValue = convertTime(valueStr);
                                    }
                                }
                                predValue = "\"" + (predValue != null ? predValue : valueStr) + "\"";
                            } else {
                                predValue = valueStr;
                            }

                            switch (valuePred.getType()) {

                                case GE:
                                    query.range(field, predValue, true, null, false);
                                    break;
                                case GT:
                                    query.range(field, predValue, false, null, false);
                                    break;
                                case LE:
                                    query.range(field, null, false, predValue, true);
                                    break;
                                case LT:
                                    query.range(field, null, false, predValue, false);
                                    break;
                            }

                            break;
                        default:
                            throw new RuntimeException("Unknown value predicate type: " + valuePred.getType());
                    }
            }
        } else if (predicate instanceof EmptyPredicate) {

            String attribute = ((EmptyPredicate) predicate).getAttribute();
            consumeQueryField(attribute, query::empty);

        } else {
            throw new RuntimeException("Unknown predicate type: " + predicate);
        }
    }

    private void handleETypeAttribute(FTSQuery query, String value) {

        RecordRef recordRef = RecordRef.valueOf(value);
        String recordId = recordRef.getId();

        String documentTypeValue;
        String documentKindValue = null;

        int slashIndex = recordId.indexOf(SLASH_DELIMITER);
        if (slashIndex != -1) {
            String firstPartOfRecordId = recordId.substring(0, slashIndex);
            documentTypeValue = WORKSPACE_PREFIX + firstPartOfRecordId;

            String secondPartOfRecordId = recordId.substring(slashIndex + 1);
            documentKindValue = WORKSPACE_PREFIX + secondPartOfRecordId;
        } else {
            documentTypeValue = WORKSPACE_PREFIX + recordId;
        }

        query.open();
        query.value(PROP_DOCUMENT_TYPE, documentTypeValue);
        if (StringUtils.isNotEmpty(documentKindValue)) {
            query.and();
            query.value(PROP_DOCUMENT_KIND, documentKindValue);
        }
        query.close();
    }

    private List<String> getPropertyValuesByConstraintsFromField(QName container, QName field, String inputValue) {

        Map<String, String> mapping = dictUtils.getPropertyDisplayNameMappingWithChildren(container, field);

        return mapping.entrySet().stream()
                .filter(e -> this.checkValueEqualsToKeyOrValue(e, inputValue))
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
        } else {
            query.value(field, value);
        }

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
        String ns = def.getName().getNamespaceURI();
        return (DataTypeDefinition.TEXT.equals(dataType) || DataTypeDefinition.MLTEXT.equals(dataType)) &&
                !ns.equals(NamespaceService.SYSTEM_MODEL_1_0_URI) &&
                !ns.equals(NamespaceService.CONTENT_MODEL_1_0_URI);
    }

    private boolean isNodeRefAtt(ClassAttributeDefinition attDef) {

        if (attDef == null) {
            return false;
        }

        if (attDef instanceof PropertyDefinition) {
            PropertyDefinition propDef = (PropertyDefinition) attDef;
            DataTypeDefinition dataType = propDef.getDataType();
            if (dataType != null) {
                return DataTypeDefinition.NODE_REF.equals(dataType.getName());
            } else {
                return false;
            }
        } else {
            return attDef instanceof AssociationDefinition;
        }
    }

    private String toValidNodeRef(String value) {

        int idx = value.lastIndexOf("@workspace://");
        if (idx > -1 && idx < value.length() - 1) {
            value = value.substring(idx + 1);
        }
        return value;
    }

    private String convertTime(String time) {

        if (time == null || time.charAt(time.length() - 1) != 'Z') {
            return time;
        }

        ZoneOffset offset = OffsetDateTime.now().getOffset();
        if (offset.getTotalSeconds() == 0) {
            return time;
        }

        try {
            Instant timeInstant = Instant.parse(time);
            return DateTimeFormatter.ISO_ZONED_DATE_TIME.format(timeInstant.atZone(offset));
        } catch (Exception e) {
            logger.error(e);
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
    public JsonNode convert(JsonNode predicateQuery) {

        Predicate predicate = predicateService.readJson(predicateQuery);

        FTSQuery query = FTSQuery.createRaw();
        processPredicate(predicate, query);

        return TextNode.valueOf(query.getQuery());
    }
}
