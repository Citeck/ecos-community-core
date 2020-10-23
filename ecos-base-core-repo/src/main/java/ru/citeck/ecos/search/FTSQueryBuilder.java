package ru.citeck.ecos.search;

import org.alfresco.service.cmr.search.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component("ftsQueryBuilder")
public class FTSQueryBuilder implements SearchQueryBuilder {

    private FtsAlfrescoQueryMigration legacyBuilder;

    @Override
    public String buildQuery(SearchCriteria criteria) {

        boolean legacyMode = criteria.getTriplets()
                                     .stream()
                                     .noneMatch(c -> c.getPredicate().startsWith("join-by-"));

        if (legacyMode) {
            return legacyBuilder.buildQuery(criteria);
        }

        TermGroup group = getTermGroup(criteria);

        StringBuilder searchString = new StringBuilder();
        group.append(searchString);
        return searchString.toString();
    }

    public TermGroup getTermGroup(SearchCriteria criteria) {

        TermGroup group = new TermGroup(JoinOperator.AND, false);

        for (CriteriaTriplet triplet : criteria.getTriplets()) {
            if (triplet.getPredicate().equals(SearchPredicate.JOIN_BY_AND.getValue())) {
                group.stopGroup();
                group.startGroup(JoinOperator.AND);
            } else if (triplet.getPredicate().equals(SearchPredicate.JOIN_BY_OR.getValue())) {
                group.stopGroup();
                group.startGroup(JoinOperator.OR);
            } else {
                Optional<Term> term = buildSearchTerm(triplet);
                term.ifPresent(group::add);
            }
        }

        group.stopAll();

        return group;
    }

    private Optional<Term> buildSearchTerm(CriteriaTriplet triplet) {

        triplet = legacyBuilder.convertAssocTriplet(triplet);
        SearchPredicate criterion = SearchPredicate.forName(triplet.getPredicate());

        String field = triplet.getField();
        String value = triplet.getValue();

        String term = null;
        boolean inverse = false;

        switch (criterion) {
            case STRING_CONTAINS:
                term = buildEqualsTerm(field, '*' + value + '*');
                break;
            case STRING_NOT_EQUALS:
            case NUMBER_NOT_EQUALS:
            case DATE_NOT_EQUALS:
            case TYPE_NOT_EQUALS:
            case ASPECT_NOT_EQUALS:
                inverse = true;
            case STRING_EQUALS:
                term = buildExactValueTerm(field, value);
                break;
            case NUMBER_EQUALS:
            case DATE_EQUALS:
            case TYPE_EQUALS:
            case ASPECT_EQUALS:
            case PARENT_EQUALS:
            case PATH_EQUALS:
            case LIST_EQUALS:
            case LIST_NOT_EQUALS:
                term = buildEqualsTerm(field, value);
                break;
            case STRING_STARTS_WITH:
                term = buildEqualsTerm(field, value + '*');
                break;
            case STRING_ENDS_WITH:
                term = buildEqualsTerm(field, '*' + value);
                break;
            case DATE_NOT_EMPTY:
            case BOOLEAN_NOT_EMPTY:
            case NODEREF_NOT_EMPTY:
            case ASSOC_NOT_EMPTY:
            case FLOAT_NOT_EMPTY:
            case INT_NOT_EMPTY:
                term = buildEmptyCheckTerm(field, false);
                break;
            case STRING_NOT_EMPTY:
                term = buildEmptyStringTerm(field, false);
                break;
            case DATE_EMPTY:
            case BOOLEAN_EMPTY:
            case NODEREF_EMPTY:
            case ASSOC_EMPTY:
                term = buildNullCheckTerm(field, true);
                break;
            case STRING_EMPTY:
                term = buildEmptyStringTerm(field, true);
                break;
            case NUMBER_LESS_THAN:
                term = buildLessThanTerm(field, value, false);
                break;
            case NUMBER_GREATER_THAN:
                term = buildGreaterThanTerm(field, value, false);
                break;
            case NUMBER_LESS_OR_EQUAL:
            case DATE_LESS_OR_EQUAL:
            case DATE_LESS_THAN:
                term = buildLessThanTerm(field, value, true);
                break;
            case NUMBER_GREATER_OR_EQUAL:
            case DATE_GREATER_OR_EQUAL:
                term = buildGreaterThanTerm(field, value, true);
                break;
            case BOOLEAN_TRUE:
                term = buildEqualsTerm(field, "true");
                break;
            case BOOLEAN_FALSE:
                term = buildEqualsTerm(field, "false");
                break;
            case ANY:
                term = buildEqualsTerm(field, "*");
                break;
            case NODEREF_NOT_CONTAINS:
            case ASSOC_NOT_CONTAINS:
            case QNAME_NOT_CONTAINS:
                inverse = true;
            case NODEREF_CONTAINS:
            case ASSOC_CONTAINS:
            case QNAME_CONTAINS:
                term = buildListContainsTerm(field, value);
                break;
            case PATH_CHILD:
                term = buildEqualsTerm(field, value + "/*");
                break;
            case PATH_DESCENDANT:
                term = buildEqualsTerm(field, value + "//*");
                break;
            case ID_EQUALS:
                break;
        }

        if (term != null) {
            return Optional.of(new FixedTerm(term, inverse));
        }

        return Optional.empty();
    }

    private String buildListContainsTerm(String field, String value) {

        StringBuilder term = new StringBuilder();

        List<String> listItems = Arrays.asList(value.split(","));
        if (!listItems.isEmpty()) {

            term.append('(');

            Iterator<String> iterator = listItems.iterator();
            while (iterator.hasNext()) {
                String item = iterator.next();
                term.append(buildEqualsTerm(field, item.trim()));
                if (iterator.hasNext()) {
                    term.append(" OR ");
                }
            }
            term.append(')');
        }

        return term.toString();
    }

    private String buildRangeTerm(String field, String value, boolean inclusive, boolean lessThan) {

        StringBuilder term = new StringBuilder();

        term.append(buildSearchField(field));
        term.append(':');
        if (lessThan) {

            term.append("[MIN TO \"")
                .append(value)
                .append("\"")
                .append(inclusive ? "]" : ">");

        } else {

            term.append(inclusive ? "[" : "<")
                .append("\"")
                .append(value)
                .append("\" TO MAX]");
        }

        return term.toString();
    }

    private String buildLessThanTerm(String field, String value, boolean inclusive) {
        return buildRangeTerm(field, value, inclusive, true);
    }

    private String buildGreaterThanTerm(String field, String value, boolean inclusive) {
        return buildRangeTerm(field, value, inclusive, false);
    }

    private String escapeValue(String value) {
        return value.replace("\"", "\\\"");
    }

    private String buildExactValueTerm(String field, String value) {
        return "=" + field + ":\"" + escapeValue(value) + "\"";
    }

    private String buildEqualsTerm(String field, String value) {
        return buildSearchField(field) + ":\"" + escapeValue(value) + '"';
    }

    private String buildSearchField(String field) {
        try {
            return FieldType.forName(field).toString();
        } catch (IllegalArgumentException e) {
            return "@" + LuceneQuery.escapeField(field);
        }
    }

    private String buildEmptyCheckTerm(String field, boolean isEmpty) {

        StringBuilder term = new StringBuilder();
        term.append('(');
        term.append(buildNullCheckTerm(field, isEmpty));
        term.append(isEmpty ? " OR " : " AND NOT ");
        term.append(buildEqualsTerm(field, ""));
        term.append(')');

        return term.toString();
    }

    private String buildEmptyStringTerm(String field, boolean isEmpty) {

        StringBuilder term = new StringBuilder();
        if (isEmpty) {
            term.append("NOT ");
        }
        term.append(field)
            .append(":[\"\" TO *]");

        return term.toString();
    }

    private String buildNullCheckTerm(String field, boolean isNull) {
        StringBuilder term = new StringBuilder();
        if (isNull) {
            term.append('(');
            term.append(buildEqualsTerm("ISNULL", field));
            term.append(" OR ");
            term.append(buildEqualsTerm("ISUNSET", field));
            term.append(')');
        } else {
            term.append(buildEqualsTerm("ISNOTNULL", field));
        }
        return term.toString();
    }

    @Override
    public boolean supports(String language) {
        return SearchService.LANGUAGE_FTS_ALFRESCO.equals(language);
    }

    @Autowired
    public void setLegacyBuilder(FtsAlfrescoQueryMigration legacyBuilder) {
        this.legacyBuilder = legacyBuilder;
    }

    public interface Term {
        void append(StringBuilder sb);

        default boolean isEmpty() {
            return false;
        }
    }

    public static class FixedTerm implements Term {

        final String value;
        final boolean inverse;

        public FixedTerm(String value) {
            this(value, false);
        }

        public FixedTerm(String value, boolean inverse) {
            this.inverse = inverse;
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public boolean isInverse() {
            return inverse;
        }

        @Override
        public void append(StringBuilder sb) {
            sb.append(inverse ? "NOT " + value : value);
        }
    }

    public static class FieldTerm implements Term {

        final String field;
        final String value;
        final boolean exact;

        public FieldTerm(String field, String value, boolean exact) {
            this.field = field;
            this.value = value;
            this.exact = exact;
        }

        @Override
        public void append(StringBuilder sb) {
            sb.append(exact ? '=' : '@')
              .append(field)
              .append(":\"")
              .append(value)
              .append("\"");
        }
    }

    public enum JoinOperator {
        OR, AND
    }

    public static class TermGroup implements Term {

        private final JoinOperator joinBy;
        private final List<Term> terms = new ArrayList<>();
        private final boolean withBrackets;

        private TermGroup childGroup = null;

        public TermGroup(JoinOperator joinBy) {
            this(joinBy, true);
        }

        public TermGroup(JoinOperator joinBy, boolean withBrackets) {
            this.joinBy = joinBy;
            this.withBrackets = withBrackets;
        }

        public void startGroup(JoinOperator joinBy) {
            if (childGroup != null) {
                childGroup.startGroup(joinBy);
            } else {
                childGroup = new TermGroup(joinBy);
            }
        }

        public boolean stopGroup() {
            if (childGroup != null) {
                if (childGroup.childGroup != null) {
                    childGroup.stopGroup();
                } else {
                    terms.add(childGroup);
                    childGroup = null;
                }
                return true;
            } else {
                return false;
            }
        }

        void stopAll() {
            int groupsCounter = 5;
            while (groupsCounter > 0 && stopGroup()) {
                groupsCounter--;
            }
        }

        void add(Term term) {
            if (childGroup != null) {
                childGroup.add(term);
            } else {
                terms.add(term);
            }
        }

        @Override
        public boolean isEmpty() {
            return terms.isEmpty();
        }

        @Override
        public void append(StringBuilder sb) {

            List<Term> nonEmptyTerms = terms.stream()
                                            .filter(t -> !t.isEmpty())
                                            .collect(Collectors.toList());

            if (nonEmptyTerms.isEmpty()) {
                return;
            }

            if (nonEmptyTerms.size() == 1) {
                nonEmptyTerms.get(0).append(sb);
                return;
            }

            if (withBrackets) {
                sb.append('(');
            }

            boolean queryIsEmpty = true;
            for (Term term : nonEmptyTerms) {
                if (!queryIsEmpty) {
                    sb.append(' ').append(joinBy).append(' ');
                }
                term.append(sb);
                queryIsEmpty = false;
            }

            if (withBrackets) {
                sb.append(')');
            }
        }

        public JoinOperator getJoinBy() {
            return joinBy;
        }

        public List<Term> getTerms() {
            return terms;
        }

        public void setTerms(List<Term> terms) {
            this.terms.clear();
            this.terms.addAll(terms);
        }
    }
}


