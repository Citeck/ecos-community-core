/*
 * Copyright (C) 2008-2015 Citeck LLC.
 *
 * This file is part of Citeck EcoS
 *
 * Citeck EcoS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Citeck EcoS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Citeck EcoS. If not, see <http://www.gnu.org/licenses/>.
 */
package ru.citeck.ecos.search;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.search.adaptor.lucene.QueryConstants;
import org.alfresco.service.cmr.dictionary.*;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.Path;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.search.ResultSet;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.log4j.Logger;

import java.util.*;

/**
 * Query builder for Lucene search
 *
 * @author Anton Fateev <anton.fateev@citeck.ru>
 */
public class LuceneQuery implements SearchQueryBuilder {

    private static final Logger logger = Logger.getLogger(LuceneQuery.class);

    private static final String WILD = "*";

    protected static final String SEPARATOR = ":";

    private static final String NAME_SEPARATOR = "-";

    private static final String ESCAPE = "\\";

    private static final String QUOTE = "\"";

    protected static final String FROM_MIN = "MIN TO ";

    protected static final String TO_MAX = " TO MAX";

    private static final String AND = " AND ";

    private static final String OR = " OR ";

    private static final String NOT = "NOT ";

    private static final String OPENING_ROUND_BRACKET = "(";

    private static final String CLOSING_ROUND_BRACKET = ")";

    private NamespaceService namespaceService;
    private DictionaryService dictionaryService;
    private SearchService searchService;
    private NodeService nodeService;

    private AssociationIndexPropertyRegistry associationIndexPropertyRegistry;
    private SearchCriteriaSettingsRegistry searchCriteriaSettingsRegistry;


    public void setNamespaceService(NamespaceService namespaceService) {
        this.namespaceService = namespaceService;
    }

    public void setAssociationIndexPropertyRegistry(AssociationIndexPropertyRegistry registry) {
        this.associationIndexPropertyRegistry = registry;
    }

    public void setDictionaryService(DictionaryService dictionaryService) {
        this.dictionaryService = dictionaryService;
    }

    public void setSearchService(SearchService searchService) {
        this.searchService = searchService;
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    @Override
    public String buildQuery(SearchCriteria criteria) {
        return new QueryBuilder().buildQuery(criteria);
    }

    @Override
    public boolean supports(String language) {
        return SearchService.LANGUAGE_LUCENE.equals(language);
    }

    public void setSearchCriteriaSettingsRegistry(SearchCriteriaSettingsRegistry searchCriteriaSettingsRegistry) {
        this.searchCriteriaSettingsRegistry = searchCriteriaSettingsRegistry;
    }

    /**
     * Expand folder nodeRefs if it have class which doesn't match to association target class
     */
    private String buildAssocValue(String field, String fieldValue) {

        if (fieldValue.isEmpty()) {
            return fieldValue;
        }

        QName fieldQName = QName.resolveToQName(namespaceService, field);
        AssociationDefinition assocDefinition = dictionaryService.getAssociation(fieldQName);
        if (assocDefinition == null) {
            return fieldValue;
        }
        QName targetClassName = assocDefinition.getTargetClass().getName();

        List<NodeRef> result = new ArrayList<>();
        String[] values = fieldValue.split("\\,");

        for (String value : values) {

            NodeRef valueRef = new NodeRef(value);
            QName valueTypeName = nodeService.getType(valueRef);

            if (!dictionaryService.isSubClass(valueTypeName, targetClassName)
                    && dictionaryService.isSubClass(valueTypeName, ContentModel.TYPE_FOLDER)) {
                result.addAll(expandAssocValue(valueRef, targetClassName));
            } else {
                result.add(new NodeRef(value));
            }
        }
        return joinToString(result);
    }

    private List<NodeRef> expandAssocValue(NodeRef valueRef, QName valueType) {
        Path path = nodeService.getPath(valueRef);
        String query = "PATH:\"" + path.toPrefixString(namespaceService) + "//.\" " +
                       "AND TYPE:\"" + valueType.toPrefixString(namespaceService) + "\"";
        ResultSet queryResults = searchService.query(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE,
                                                     SearchService.LANGUAGE_FTS_ALFRESCO,
                                                     query);
        return queryResults.getNodeRefs();
    }

    private String getAssocIndexProp(String assocName) {
        return associationIndexPropertyRegistry.getAssociationIndexProperty(assocName, namespaceService);
    }

    CriteriaTriplet convertAssocTriplet(CriteriaTriplet triplet) {

        SearchPredicate criterion = SearchPredicate.forName(triplet.getPredicate());

        String newValue = null;
        String newField = null;

        switch (criterion) {
            case ASSOC_CONTAINS:
            case ASSOC_NOT_CONTAINS:
                try {
                    newValue = buildAssocValue(triplet.getField(), triplet.getValue());
                } catch (Exception e) {
                    logger.warn(String.format("Association value building failed! field is '%s' and value is '%s'",
                                triplet.getField(), triplet.getValue()), e);
                }
            case ASSOC_NOT_EMPTY:
            case ASSOC_EMPTY:
                newField = getAssocIndexProp(triplet.getField());
                break;
            default:
                break;
        }

        if (newValue != null || newField != null) {

            String field = newField != null ? newField : triplet.getField();
            String predicate = triplet.getPredicate();
            String value = newValue != null ? newValue : triplet.getValue();

            return new CriteriaTriplet(field, predicate, value);
        }

        return triplet;
    }

    static String escapeField(String string) {
        StringBuilder result = new StringBuilder(string);
        int iSeparator = 0;
        while ((iSeparator = result.indexOf(NAME_SEPARATOR, iSeparator)) != -1) {
            result.insert(iSeparator, ESCAPE);
            iSeparator +=2;
        }
        iSeparator = result.indexOf(SEPARATOR, iSeparator);
        if (iSeparator != -1) {
            result.insert(iSeparator, ESCAPE);
        }
        return result.toString();
    }

    private static String joinToString(List<NodeRef> nodeRefs) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < nodeRefs.size(); i++) {
            if (i > 0) result.append(",");
            result.append(nodeRefs.get(i));
        }
        return result.toString();
    }

    private String buildField(String field) {
        return buildField(field, false);
    }

    protected String buildField(String field, boolean exact) {
        try {
            return FieldType.forName(field).toString();
        } catch (IllegalArgumentException e) {
            return "@" + escapeField(field);
        }
    }

    protected class QueryBuilder {

        protected StringBuilder query = new StringBuilder();
        private List<QueryElement> queryElementList = new ArrayList<>();
        Boolean shouldAppendQuery;
        protected QueryElement queryElement;
        private String typeName;

        public String buildQuery(SearchCriteria criteria) {

            Iterator<CriteriaTriplet> iterator = criteria.getTripletsIterator();
            extractType(criteria);

            List<CriteriaTriplet> tripletsContainsMoreThanOne = getMiltipleFilteringTriplets(criteria);

            while (iterator.hasNext()) {

                CriteriaTriplet criteriaTriplet = iterator.next();
                boolean ignore = ignoreIfValueEmpty(criteriaTriplet);
                if (!ignore) {
                    shouldAppendQuery = true;
                    queryElement = new QueryElement(criteriaTriplet);

                    if (queryElementList.contains(queryElement) && logicOrEnabled(criteriaTriplet)) {
                        shouldAppendQuery = false;
                    }

                    buildSearchTerm(criteriaTriplet, tripletsContainsMoreThanOne.contains(criteriaTriplet));

                    if (!shouldAppendQuery) {
                        query = appendOrLogic(query);
                    }

                    queryElementList.add(queryElement);

                    if (iterator.hasNext() && shouldAppendQuery) {
                        query.append(AND);
                    }
                }
            }
            String finalQuery = toCorrectQuery(query.toString());

            if (logger.isDebugEnabled()) {
                logger.debug("FINAL QUERY: " + finalQuery);
                for (QueryElement queryElement : queryElementList) {
                    logger.debug(queryElement);
                }
            }

            return finalQuery;
        }

        private List<CriteriaTriplet> getMiltipleFilteringTriplets(SearchCriteria criteria) {

            List<CriteriaTriplet> triplets = criteria.getTriplets();

            if (triplets == null) {
                return Collections.emptyList();
            }

            Map<CriteriaDoublet, Integer> doubletsCount = new HashMap<>();
            triplets.forEach(triplet -> doubletsCount.merge(new CriteriaDoublet(triplet), 1, Integer::sum));

            List<CriteriaTriplet> result = new ArrayList<>();
            triplets.forEach(triplet -> { if (doubletsCount.get(new CriteriaDoublet(triplet)) > 1) {
                result.add(triplet);
            }});

            return result;
        }

        private void extractType(SearchCriteria criteria) {
            Iterator<CriteriaTriplet> iterator = criteria.getTripletsIterator();
            while (iterator.hasNext()) {
                CriteriaTriplet criteriaTriplet = iterator.next();
                boolean ignore = ignoreIfValueEmpty(criteriaTriplet);
                if (!ignore) {
                    SearchPredicate criterion = SearchPredicate.forName(criteriaTriplet.getPredicate());
                    if (SearchPredicate.TYPE_EQUALS.equals(criterion)) {
                        typeName = criteriaTriplet.getValue();
                    }
                }
            }
        }

        private void buildSearchTerm(CriteriaTriplet triplet, boolean tripletsHasMoreOne) {

            SearchPredicate criterion = SearchPredicate.forName(triplet.getPredicate());

            String value = triplet.getValue();

            if (SearchPredicate.JOURNAL_ID.equals(criterion)) {
                String nodeType = getNodeType(value);
                if (!isSafeEmpty(nodeType)) {
                    buildEqualsTerm(FieldType.TYPE.toString(), nodeType);
                    String staticQuery = getStaticQuery(value);
                    if (!isSafeEmpty(staticQuery)) {
                        query.append(AND);
                        query.append(staticQuery);
                    }
                }
                return;
            }

            triplet = convertAssocTriplet(triplet);
            String field = buildField(
                    triplet.getField(),
                    SearchPredicate.STRING_EQUALS.equals(criterion) && !tripletsHasMoreOne
            );

            switch (criterion) {
                case STRING_CONTAINS:
                    buildEqualsTerm(field, WILD + value + WILD);
                    break;
                case QUERY_OR:
                    buildQueryOr(triplet.getField(), value);
                    break;
                case STRING_NOT_EQUALS:
                case NUMBER_NOT_EQUALS:
                case DATE_NOT_EQUALS:
                case TYPE_NOT_EQUALS:
                case ASPECT_NOT_EQUALS:
                    if (shouldAppendQuery) {
                        query.append(NOT);
                    }
                case STRING_EQUALS:
                case NUMBER_EQUALS:
                case DATE_EQUALS:
                    buildEqualsTermWithRoundBracket(field, value);
                    break;
                case TYPE_EQUALS:
                case ASPECT_EQUALS:
                case PARENT_EQUALS:
                case PATH_EQUALS:
                case LIST_EQUALS:
                case LIST_NOT_EQUALS:
                    buildEqualsTerm(field, value);
                    break;
                case STRING_STARTS_WITH:
                    buildEqualsTerm(field, value + WILD);
                    break;
                case STRING_ENDS_WITH:
                    buildEqualsTerm(field, WILD + value);
                    break;
                case DATE_NOT_EMPTY:
                case BOOLEAN_NOT_EMPTY:
                case NODEREF_NOT_EMPTY:
                case ASSOC_NOT_EMPTY:
                case FLOAT_NOT_EMPTY:
                case INT_NOT_EMPTY:
                    buildEmptyCheckTerm(field, false);
                    break;
                case STRING_NOT_EMPTY:
                    buildEmptyStringTerm(field, false);
                    break;
                case DATE_EMPTY:
                case BOOLEAN_EMPTY:
                case NODEREF_EMPTY:
                case ASSOC_EMPTY:
                    buildNullCheckTerm(field, true);
                    break;
                case STRING_EMPTY:
                    buildEmptyStringTerm(field, true);
                    break;
                case NUMBER_LESS_THAN:
                    buildLessThanTerm(field, value, false);
                    break;
                case NUMBER_GREATER_THAN:
                    buildGreaterThanTerm(field, value, false);
                    break;
                case NUMBER_LESS_OR_EQUAL:
                case DATE_LESS_OR_EQUAL:
                case DATE_LESS_THAN:
                    buildLessThanTerm(field, value, true);
                    break;
                case NUMBER_GREATER_OR_EQUAL:
                case DATE_GREATER_OR_EQUAL:
                    buildGreaterThanTerm(field, value, true);
                    break;
                case BOOLEAN_TRUE:
                    buildEqualsTerm(field, "true");
                    break;
                case BOOLEAN_FALSE:
                    buildEqualsTerm(field, "false");
                    break;
                case ANY:
                    buildEqualsTerm(field, WILD);
                    break;
                case NODEREF_NOT_CONTAINS:
                case ASSOC_NOT_CONTAINS:
                case QNAME_NOT_CONTAINS:
                    if (shouldAppendQuery) {
                        query.append(NOT);
                    }
                case NODEREF_CONTAINS:
                case ASSOC_CONTAINS:
                case QNAME_CONTAINS:
                    buildListContainsTerm(field, value);
                    break;
                case PATH_CHILD:
                    buildEqualsTerm(field, value + "/*");
                    break;
                case PATH_DESCENDANT:
                    buildEqualsTerm(field, value + "//*");
                    break;
                case ID_EQUALS:
                    break;
            }
        }

        private boolean ignoreIfValueEmpty(CriteriaTriplet triplet) {
            boolean ignore = false;
            SearchPredicate criterion = SearchPredicate.forName(triplet.getPredicate());
            String value = triplet.getValue();

            if ("".equals(value)) {
                switch (criterion) {
                    case STRING_EQUALS:
                    case NUMBER_EQUALS:
                    case DATE_EQUALS:
                    case TYPE_EQUALS:
                    case ASPECT_EQUALS:
                    case PARENT_EQUALS:
                    case PATH_EQUALS:
                    case LIST_EQUALS:
                    case STRING_STARTS_WITH:
                    case STRING_ENDS_WITH:
                    case NODEREF_CONTAINS:
                    case ASSOC_CONTAINS:
                    case QNAME_CONTAINS:
                    case STRING_CONTAINS:
                    case DATE_GREATER_OR_EQUAL:
                    case DATE_LESS_OR_EQUAL:
                    case DATE_LESS_THAN:
                    case NUMBER_GREATER_THAN:
                    case NUMBER_GREATER_OR_EQUAL:
                    case NUMBER_LESS_THAN:
                    case NUMBER_LESS_OR_EQUAL:
                        ignore = true;
                }
            } else if (SearchPredicate.JOURNAL_ID.equals(criterion)) {
                if (typeName != null) {
                    ignore = true;
                } else {
                    String nodeType = getNodeType(value);
                    ignore = isSafeEmpty(nodeType);
                }
            }
            return ignore;
        }

        private boolean logicOrEnabled(CriteriaTriplet criteriaTriplet) {
            SearchPredicate criterion = SearchPredicate.forName(criteriaTriplet.getPredicate());
            String field = criteriaTriplet.getField();
            QName fieldQName = QName.resolveToQName(namespaceService, field);

            switch (criterion) {
                case ASSOC_CONTAINS:
                    return dictionaryService.getAssociation(fieldQName) != null && !dictionaryService.getAssociation(fieldQName).isTargetMany();
                case NODEREF_CONTAINS:
                    return dictionaryService.getProperty(fieldQName) != null && !dictionaryService.getProperty(fieldQName).isMultiValued();
                case STRING_EQUALS:
                case NUMBER_EQUALS:
                case DATE_EQUALS:
                    return true;
                default:
                    return false;
            }
        }

        private void buildEqualsTerm(String field, String value) {

            if (FieldType.ALL.name().equals(field)) {

                Collection<QName> contentAttributes;
                QName type = null;
                if (typeName != null) {
                    type = QName.resolveToQName(namespaceService, typeName);
                }
                if (type != null) {
                    contentAttributes = getTextAttributesByType(type);
                } else {
                    contentAttributes = dictionaryService.getAllProperties(DataTypeDefinition.TEXT);
                    excludeSysAttributes(contentAttributes);
                }
                StringBuilder allQuery = new StringBuilder();

                for (QName qname : contentAttributes) {
                    allQuery.append(QueryConstants.PROPERTY_FIELD_PREFIX).append(escapeField(qname.toPrefixString()))
                            .append(SEPARATOR).append(QUOTE)
                            .append(value)
                            .append(QUOTE).append(OR);
                }
                int allQueryLength = allQuery.length();
                if (allQueryLength > 0) {
                    int orLength = OR.length();
                    allQuery.delete(allQueryLength - orLength, allQueryLength);
                    query.append(OPENING_ROUND_BRACKET)
                            .append(allQuery)
                            .append(CLOSING_ROUND_BRACKET);
                }

            } else {

                StringBuilder term = new StringBuilder();

                term.append(field)
                    .append(SEPARATOR)
                    .append(QUOTE)
                    .append(value.replace("\"", "\\\""))
                    .append(QUOTE);

                queryElement.setQueryPart(term.toString());

                if (shouldAppendQuery) {
                    query.append(term);
                }
            }
        }

        private void buildQueryOr(String field, String value) {
            StringBuilder _value = new StringBuilder(SEPARATOR)
                    .append(QUOTE).append(WILD).append(value).append(WILD).append(QUOTE);
            List<String> subQueryOr = getSubQueryOr(field);
            if (subQueryOr != null && !subQueryOr.isEmpty()) {
                StringBuilder _term = new StringBuilder(OPENING_ROUND_BRACKET);
                for (String subField : subQueryOr) {
                    _term.append(buildField(subField)).append(_value).append(OR);
                }
                int termLength = _term.length();
                _term.delete(termLength - OR.length(), termLength);
                _term.append(CLOSING_ROUND_BRACKET);
                query.append(_term);
            }
        }

        private void excludeSysAttributes(Collection<QName> contentAttributes) {
            Collection<QName> sysAttributes = new HashSet<>();
            for (QName qName : contentAttributes) {
                if (isSysModel(qName)) {
                    sysAttributes.add(qName);
                }
            }
            if (!sysAttributes.isEmpty()) {
                contentAttributes.removeAll(sysAttributes);
            }
        }

        private boolean isSysModel(QName qName) {
            return NamespaceService.SYSTEM_MODEL_1_0_URI.equals(qName.getNamespaceURI());
        }

        private Collection<QName> getTextAttributesByType(QName type) {
            Collection<QName> textAttributes = new HashSet<>();
            TypeDefinition typeDef = dictionaryService.getType(type);
            extractTextAttribute(typeDef, textAttributes);
            return textAttributes;
        }

        private void extractTextAttribute(ClassDefinition classDef, Collection<QName> textAttributes) {
            if (classDef == null || isSysModel(classDef.getName())) {
                return;
            }
            Map<QName, PropertyDefinition> properties = classDef.getProperties();
            List<AspectDefinition> aspects = classDef.getDefaultAspects();
            ClassDefinition parent = classDef.getParentClassDefinition();
            for (PropertyDefinition pDef : properties.values()) {
                if (DataTypeDefinition.TEXT.equals(pDef.getDataType().getName())) {
                    textAttributes.add(pDef.getName());
                }
            }
            for (AspectDefinition aDef : aspects) {
                extractTextAttribute(aDef, textAttributes);
            }
            extractTextAttribute(parent, textAttributes);
        }

        private void buildEqualsTermWithRoundBracket(String field, String value) {
            if (shouldAppendQuery) {
                query.append(OPENING_ROUND_BRACKET);
            }
            buildEqualsTerm(field, value);
            if (shouldAppendQuery) {
                query.append(CLOSING_ROUND_BRACKET);
            }
        }

        protected void buildRangeTerm(String field, String value, boolean inclusive, boolean lessThan) {
            StringBuilder range = new StringBuilder();
            range.append(inclusive ? "[" : "{");
            if (lessThan) {
                range.append(FROM_MIN);
            }
            range.append(value);
            if (!lessThan) {
                range.append(TO_MAX);
            }
            range.append(inclusive ? "]" : "}");

            StringBuilder term = new StringBuilder();
            term.append(field).append(SEPARATOR).append(range);
            queryElement.setQueryPart(term.toString());
            if (shouldAppendQuery) {
                query.append(term);
            }
        }

        private void buildLessThanTerm(String field, String value, boolean inclusive) {
            buildRangeTerm(field, value, inclusive, true);
        }

        private void buildGreaterThanTerm(String field, String value, boolean inclusive) {
            buildRangeTerm(field, value, inclusive, false);
        }

        private void appendQuery(String value) {
            if (shouldAppendQuery) {
                query.append(value);
            }
            queryElement.setQueryPart(queryElement.getQueryPart() + value);
        }

        private void buildNullCheckTerm(String value, boolean isNull) {
            String propName = getPropertyName(value);
            if (isNull) {
                appendQuery(OPENING_ROUND_BRACKET);
                buildEqualsTerm("ISNULL", propName);
                appendQuery(OR);
                buildEqualsTerm("ISUNSET", propName);
                appendQuery(CLOSING_ROUND_BRACKET);
            } else {
                buildEqualsTerm("ISNOTNULL", propName);
            }
        }

        private void buildEmptyStringTerm(String field, boolean isEmpty) {
            StringBuilder term = new StringBuilder();
            if (isEmpty) {
                term.append("NOT ");
            }
            term.append(field)
                .append(":[\"\" TO *]");

            appendQuery(term.toString());
        }

        private void buildEmptyCheckTerm(String field, boolean isEmpty) {
            appendQuery(OPENING_ROUND_BRACKET);
            buildNullCheckTerm(field, isEmpty);
            appendQuery(isEmpty ? OR : AND + NOT);
            buildEqualsTerm(field, "");
            appendQuery(CLOSING_ROUND_BRACKET);
        }

        private void buildListContainsTerm(String field, String value) {
            List<String> listItems = Arrays.asList(value.split("\\,"));
            if (!listItems.isEmpty()) {
                if (shouldAppendQuery) {
                    query.append(OPENING_ROUND_BRACKET);
                }
                Iterator<String> iterator = listItems.iterator();
                while (iterator.hasNext()) {
                    String item = iterator.next();
                    buildEqualsTerm(field, item.trim());
                    if (iterator.hasNext()) {
                        if (shouldAppendQuery) {
                            query.append(OR);
                        }
                        queryElement.setQueryPart(queryElement.getQueryPart() + OR);
                    }
                }
                if (shouldAppendQuery) {
                    query.append(CLOSING_ROUND_BRACKET);
                }
            }
        }

        private String getPropertyName(String field) {
            return field.replace("@", "").replace("\\", "");
        }

        private StringBuilder appendOrLogic(StringBuilder query) {
            int indexOfField = query.indexOf(queryElement.field);
            int firsIndex = query.indexOf("\"", indexOfField);
            int secondIndex = query.indexOf("\"", firsIndex + 1);

            query.insert(secondIndex + 1, " " + OR + " " + queryElement.queryPart);

            return query;
        }

        private String toCorrectQuery(String query) {
            if (query.endsWith(AND)) {
                query = query.substring(0, query.length() - AND.length());
            }
            return query;
        }

        protected class QueryElement {
            SearchPredicate criterion;
            String field;
            String value;
            String queryPart;

            QueryElement(CriteriaTriplet criteriaTriplet) {
                this.criterion = SearchPredicate.forName(criteriaTriplet.getPredicate());
                this.field = buildField(criteriaTriplet.getField());
                this.value = criteriaTriplet.getValue();
            }

            @Override
            public String toString() {
                return "Part: "
                        + "\ncriterion - " + this.criterion
                        + "\nfield - " + this.field
                        + "\nvalue - " + this.value
                        + "\nqueryPart - " + this.queryPart;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;

                QueryElement queryElement = (QueryElement) o;

                if (criterion != queryElement.criterion) return false;
                return Objects.equals(field, queryElement.field);
            }

            @Override
            public int hashCode() {
                int result = criterion != null ? criterion.hashCode() : 0;
                result = 31 * result + (field != null ? field.hashCode() : 0);
                return result;
            }

            void setQueryPart(String queryPart) {
                this.queryPart = queryPart;
            }

            String getQueryPart() {
                return queryPart;
            }
        }

        protected class CriteriaDoublet {
            String predicate;
            String field;

            CriteriaDoublet(CriteriaTriplet criteriaTriplet) {
                this.predicate = criteriaTriplet.getPredicate();
                this.field = criteriaTriplet.getField();
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) {
                    return true;
                }

                if (!(obj instanceof CriteriaDoublet)) {
                    return false;
                }

                CriteriaDoublet that = (CriteriaDoublet) obj;

                return Objects.equals(predicate, that.predicate) && Objects.equals(field, that.field);
            }

            @Override
            public int hashCode() {
                int result = predicate != null ? predicate.hashCode() : 0;
                result = 31 * result + (field != null ? field.hashCode() : 0);
                return result;
            }
        }
    }

    private boolean isSafeEmpty(String str) {
        return str == null || str.isEmpty();
    }

    private String getNodeType(String journalId) {
        return searchCriteriaSettingsRegistry.getNodeType(journalId);
    }

    private String getStaticQuery(String journalId) {
        return searchCriteriaSettingsRegistry.getStaticQuery(journalId);
    }

    private List<String> getSubQueryOr(String fieldName) {
        return searchCriteriaSettingsRegistry.getSubQueryOr(fieldName);
    }
}
