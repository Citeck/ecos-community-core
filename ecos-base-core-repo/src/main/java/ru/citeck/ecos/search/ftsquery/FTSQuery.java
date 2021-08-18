package ru.citeck.ecos.search.ftsquery;

import com.fasterxml.jackson.databind.util.ISO8601Utils;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.search.*;
import org.alfresco.service.namespace.QName;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import ru.citeck.ecos.utils.search.SearchUtils;

import java.io.Serializable;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Full text search query builder
 * To create new query use FTSQuery.create()
 * To search by constructed query use method query(SearchService searchService)
 *
 * @author Pavel Simonov
 */
public class FTSQuery implements OperatorExpected, OperandExpected {

    private static final Log logger = LogFactory.getLog(FTSQuery.class);

    private static final String ISUNSET = "ISUNSET";
    private static final String ISNULL = "ISNULL";
    private static final String ISNOTNULL = "ISNOTNULL";
    private static final String NOT = "NOT";
    private static final String PARENT = "PARENT";
    private static final String TYPE = "TYPE";
    private static final String PATH = "PATH";
    private static final String ASPECT = "ASPECT";
    private static final String EXISTS = "EXISTS";
    private static final String VALUE_ENVELOP_START = "(";
    private static final String VALUE_ENVELOP_END = ")";

    private static final String CONTAINS_TEMPLATE = "*%s*";
    private static final String RANGE_TEMPLATE = "%s TO %s";
    private static final String QUOTES_TEMPLATE = "\"%s\"";

    private SearchParameters searchParameters;
    private Group group = new Group();

    private FTSQuery() {
        searchParameters = new SearchParameters();
        searchParameters.setLanguage(SearchService.LANGUAGE_FTS_ALFRESCO);
        searchParameters.addStore(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);
        searchParameters.setBulkFetchEnabled(false);
    }

    private FTSQuery(FTSQuery other) {
        searchParameters = other.searchParameters.copy();
        group = other.group.copy();
    }

    /**
     * Create new query
     */
    public static OperandExpected create() {
        return new FTSQuery();
    }

    /**
     * Create unsafe FTSQuery which return self from build methods
     */
    public static FTSQuery createRaw() {
        return new FTSQuery();
    }

    @Override
    public FTSQuery values(Map<QName, Serializable> values) {
        return values(values, BinOperator.AND, false);
    }

    @Override
    public FTSQuery values(Map<QName, Serializable> values, BinOperator joinOperator) {
        return values(values, joinOperator, false);
    }

    @Override
    public FTSQuery values(Map<QName, Serializable> values, BinOperator joinOperator, boolean exact) {
        int count = values.size();
        if (count == 0) {
            throw new IllegalArgumentException("Values is empty");
        }
        open();
        for (Map.Entry<QName, Serializable> entry : values.entrySet()) {
            value(entry.getKey(), entry.getValue(), exact);
            if (--count > 0) {
                group.setBiOperator(new BinOperatorTerm(joinOperator));
            }
        }
        close();
        return this;
    }

    @Override
    public FTSQuery any(QName field, Iterable<Serializable> values) {
        Iterator<Serializable> it = values.iterator();
        if (!it.hasNext()) {
            throw new IllegalArgumentException("Values is empty");
        }
        open();
        while (it.hasNext()) {
            value(field, it.next());
            if (it.hasNext()) or();
        }
        close();
        return this;
    }

    @Override
    public FTSQuery empty(QName field) {
        return open()
            .not().exists(field).or()
            .isNull(field).or()
            .isUnset(field)
            .close();
    }

    @Override
    public FTSQuery emptyString(QName field) {
        return not().range(field, "\"\"", "*");
    }

    @Override
    public FTSQuery exact(QName field, Serializable value) {
        return value(field, value, true);
    }

    public FTSQuery value(Serializable value) {
        ValueOperator valueOperator = new ValueOperator();
        valueOperator.field = null;
        valueOperator.value = value;
        valueOperator.exact = false;
        group.addTerm(valueOperator);
        return this;
    }

    @Override
    public FTSQuery value(QName field, Serializable value) {
        return value(field, value, false);
    }

    @Override
    public FTSQuery value(QName field, Serializable value, boolean exact) {
        if (value == null) {
            empty(field);
        } else {
            ValueOperator valueOperator = new ValueOperator();
            valueOperator.field = field;
            valueOperator.value = value;
            valueOperator.exact = exact;
            group.addTerm(valueOperator);
        }
        return this;
    }

    @Override
    public FTSQuery contains(QName field, Serializable value) {
        return value(field, String.format(CONTAINS_TEMPLATE, value));
    }

    @Override
    public FTSQuery path(String path) {
        group.addTerm(new SysValueOperator(PATH, path));
        return this;
    }

    @Override
    public FTSQuery isSet(QName field) {
        return isNotNull(field);
    }

    @Override
    public FTSQuery isUnset(QName field) {
        group.addTerm(new SysValueOperator(ISUNSET, field));
        return this;
    }

    @Override
    public FTSQuery isNull(QName field) {
        group.addTerm(new SysValueOperator(ISNULL, field));
        return this;
    }

    @Override
    public FTSQuery isNotNull(QName field) {
        group.addTerm(new SysValueOperator(ISNOTNULL, field));
        return this;
    }

    @Override
    public FTSQuery parent(NodeRef parent) {
        group.addTerm(new SysValueOperator(PARENT, parent));
        return this;
    }

    @Override
    public FTSQuery type(QName typeName) {
        group.addTerm(new SysValueOperator(TYPE, typeName));
        return this;
    }

    @Override
    public FTSQuery aspect(QName aspectName) {
        group.addTerm(new SysValueOperator(ASPECT, aspectName));
        return this;
    }

    public FTSQuery exists(QName fieldName) {
        group.addTerm(new SysValueOperator(EXISTS, fieldName));
        return this;
    }

    @Override
    public FTSQuery rangeTo(QName field, Date to) {
        return range(field, null, to);
    }

    @Override
    public FTSQuery rangeFrom(QName field, Date from) {
        return range(field, from, null);
    }

    @Override
    public FTSQuery range(QName field, Date from, Date to) {
        String fromStr = formatDateRangeTerm(from);
        String toStr = formatDateRangeTerm(to);
        return range(field, fromStr, toStr);
    }

    private String formatDateRangeTerm(Date date) {
        if (date != null) {
            return String.format(QUOTES_TEMPLATE, ISO8601Utils.format(date));
        }
        return null;
    }

    @Override
    public FTSQuery rangeTo(QName field, Number to) {
        return range(field, null, to);
    }

    @Override
    public FTSQuery rangeFrom(QName field, Number from) {
        return range(field, from, null);
    }

    @Override
    public FTSQuery range(QName field, Number from, Number to) {
        String fromStr = from != null ? from.toString() : null;
        String toStr = to != null ? to.toString() : null;
        return range(field, fromStr, toStr);
    }

    @Override
    public FTSQuery range(QName field, String from, String to) {
        return range(field, from, true, to, true);
    }

    @Override
    public FTSQuery range(QName field, String from, boolean frIncl, String to, boolean toIncl) {
        if (from == null && to == null) {
            throw new IllegalArgumentException("At least one of 'from' or 'to' arguments " +
                "must be specified. Field: " + field);
        }

        if (from == null) {
            frIncl = false;
            from = "MIN";
        }
        if (to == null) {
            toIncl = false;
            to = "MAX";
        }

        ValueOperator valueOperator = new ValueOperator();
        valueOperator.field = field;
        valueOperator.value = new Range(from, frIncl, to, toIncl);
        valueOperator.exact = false;
        group.addTerm(valueOperator);
        return this;
    }

    @Override
    public FTSQuery or() {
        group.setBiOperator(new BinOperatorTerm(BinOperator.OR));
        return this;
    }

    @Override
    public FTSQuery and() {
        group.setBiOperator(new BinOperatorTerm(BinOperator.AND));
        return this;
    }

    @Override
    public FTSQuery not() {
        group.setUnOperator(new UnOperatorTerm(NOT));
        return this;
    }

    @Override
    public FTSQuery open() {
        group.startGroup();
        return this;
    }

    @Override
    public FTSQuery close() {
        group.stopGroup();
        return this;
    }

    @Override
    public FTSQuery transactionalIfPossible() {
        return consistency(QueryConsistency.TRANSACTIONAL_IF_POSSIBLE);
    }

    @Override
    public FTSQuery transactional() {
        return consistency(QueryConsistency.TRANSACTIONAL);
    }

    @Override
    public FTSQuery eventual() {
        return consistency(QueryConsistency.EVENTUAL);
    }

    @Override
    public FTSQuery consistency(QueryConsistency consistency) {
        searchParameters.setQueryConsistency(consistency);
        return this;
    }

    @Override
    public FTSQuery bulkFetch(boolean value) {
        searchParameters.setBulkFetchEnabled(value);
        return this;
    }

    @Override
    public FTSQuery addSort(QName field, boolean ascending) {
        searchParameters.addSort("@" + field, ascending);
        return this;
    }

    @Override
    public FTSQuery addSort(String field, boolean ascending) {
        searchParameters.addSort(field, ascending);
        return this;
    }

    @Override
    public FTSQuery addSort(SearchParameters.SortDefinition sortDefinition) {
        searchParameters.addSort(sortDefinition);
        return this;
    }

    @Override
    public FTSQuery maxItems(int value) {
        searchParameters.setMaxItems(value);
        return this;
    }

    @Override
    public FTSQuery skipCount(int value) {
        searchParameters.setSkipCount(value);
        return this;
    }

    @Override
    public FTSQuery permissionsMode(PermissionEvaluationMode mode) {
        searchParameters.setPermissionEvaluation(mode);
        return this;
    }

    @Override
    public FTSQuery unlimited() {
        searchParameters.setLimit(0);
        searchParameters.setLimitBy(LimitBy.UNLIMITED);
        searchParameters.setMaxItems(-1);
        searchParameters.setMaxPermissionChecks(Integer.MAX_VALUE);
        searchParameters.setMaxPermissionCheckTimeMillis(Integer.MAX_VALUE);
        return this;
    }

    @Override
    public String getQuery() {
        return group != null ? group.getQuery() : "";
    }

    @Override
    public Optional<NodeRef> queryOne(SearchService searchService) {
        return query(searchService).stream()
            .findFirst();
    }

    @Override
    public Optional<NodeRef> queryOne(SearchService searchService, Predicate<NodeRef> filter) {
        return query(searchService).stream()
            .filter(filter)
            .findFirst();
    }

    @Override
    public List<NodeRef> query(SearchService searchService, Predicate<NodeRef> filter) {
        return query(searchService).stream()
            .filter(filter)
            .collect(Collectors.toList());
    }

    @Override
    public List<NodeRef> query(SearchService searchService) {
        QueryResult result = queryDetails(searchService);
        return result.getNodeRefs();
    }

    @Override
    public QueryResult queryDetails(SearchService searchService) {

        String query = group.getQuery();

        if (logger.isDebugEnabled()) {
            logger.debug("FTSQuery: " + query);
        }

        searchParameters.setQuery(query);

        return new QueryResult(SearchUtils.query(searchParameters, searchService));
    }

    @Override
    public FTSQuery copy() {
        return new FTSQuery(this);
    }

    @Override
    public boolean equals(Object o) {

        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        FTSQuery query = (FTSQuery) o;

        return Objects.equals(searchParameters, query.searchParameters) &&
            Objects.equals(group, query.group);
    }

    @Override
    public int hashCode() {
        int result = searchParameters.hashCode();
        result = 31 * result + group.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "FTSQuery: '" + getQuery() + "'";
    }

    private static class Range implements Serializable {

        public String from;
        public String to;

        public boolean frIncl;
        public boolean toIncl;

        Range(String from, boolean frIncl, String to, boolean toIncl) {
            this.from = from;
            this.to = to;
            this.frIncl = frIncl;
            this.toIncl = toIncl;
        }

        @Override
        public String toString() {
            return (frIncl ? '[' : '<')
                + String.format(RANGE_TEMPLATE, from, to)
                + (toIncl ? ']' : '>');
        }
    }

    private interface Term<T> {
        void toString(StringBuilder builder);

        T copy();
    }

    private interface Operand<T> extends Term<T> {
    }

    private class UnOperatorTerm implements Term<UnOperatorTerm> {

        String operator;
        Term term;

        UnOperatorTerm(String operator) {
            this.operator = operator;
        }

        public void toString(StringBuilder builder) {
            builder.append(operator).append(' ');
            term.toString(builder);
        }

        @Override
        public UnOperatorTerm copy() {
            UnOperatorTerm result = new UnOperatorTerm(operator);
            result.term = term;
            return result;
        }

        @Override
        public boolean equals(Object o) {

            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            UnOperatorTerm that = (UnOperatorTerm) o;

            return operator.equals(that.operator) && term.equals(that.term);
        }

        @Override
        public int hashCode() {
            int result = operator.hashCode();
            result = 31 * result + term.hashCode();
            return result;
        }
    }

    private class BinOperatorTerm implements Term<BinOperatorTerm> {

        Term term0;
        Term term1;
        BinOperator operator;

        BinOperatorTerm(BinOperator operator) {
            this.operator = operator;
        }

        @Override
        public BinOperatorTerm copy() {
            BinOperatorTerm result = new BinOperatorTerm(operator);
            result.term0 = term0;
            result.term1 = term1;
            return result;
        }

        @Override
        public void toString(StringBuilder builder) {
            term0.toString(builder);
            if (term1 == null) {
                return;
            }
            if (term1 instanceof Group && ((Group) term1).isEmpty()) {
                return;
            }
            builder.append(' ').append(operator).append(' ');
            term1.toString(builder);
        }

        @Override
        public boolean equals(Object o) {

            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            BinOperatorTerm operator1 = (BinOperatorTerm) o;

            return term0.equals(operator1.term0) &&
                term1.equals(operator1.term1) &&
                operator.equals(operator1.operator);
        }

        @Override
        public int hashCode() {
            int result = term0.hashCode();
            result = 31 * result + term1.hashCode();
            result = 31 * result + operator.hashCode();
            return result;
        }
    }

    private class SysValueOperator implements Operand<SysValueOperator> {

        String field;
        Serializable value;

        SysValueOperator(String field, Serializable value) {
            this.field = field;
            this.value = value;
        }

        @Override
        public SysValueOperator copy() {
            return new SysValueOperator(field, value);
        }

        @Override
        public void toString(StringBuilder builder) {
            builder.append(field).append(":\"").append(value).append('\"');
        }

        @Override
        public boolean equals(Object o) {

            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            SysValueOperator that = (SysValueOperator) o;

            return Objects.equals(field, that.field) &&
                Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            int result = field != null ? field.hashCode() : 0;
            result = 31 * result + (value != null ? value.hashCode() : 0);
            return result;
        }
    }

    private class ValueOperator implements Operand<ValueOperator> {

        QName field;
        Serializable value;
        boolean exact = false;

        @Override
        public ValueOperator copy() {
            ValueOperator result = new ValueOperator();
            result.field = field;
            result.value = value;
            result.exact = exact;
            return result;
        }

        @Override
        public void toString(StringBuilder builder) {

            if (field == null) {
                builder.append(VALUE_ENVELOP_START)
                    .append(value)
                    .append(VALUE_ENVELOP_END);
                return;
            }

            QueryConsistency consistency = searchParameters.getQueryConsistency();
            char prefix = exact || consistency.equals(QueryConsistency.TRANSACTIONAL) ? '=' : '@';

            String local = field.getLocalName();

            builder.append(prefix)
                .append(QName.NAMESPACE_BEGIN)
                .append(field.getNamespaceURI())
                .append(QName.NAMESPACE_END)
                .append(local.replace("-", "\\-"))
                .append(":");

            if (value instanceof Boolean || value instanceof Range) {
                builder.append(value);
            } else {
                builder.append("\"").append(value).append('\"');
            }
        }

        @Override
        public boolean equals(Object o) {

            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ValueOperator operator = (ValueOperator) o;

            return exact == operator.exact &&
                Objects.equals(field, operator.field) &&
                Objects.equals(value, operator.value);
        }

        @Override
        public int hashCode() {
            int result = field != null ? field.hashCode() : 0;
            result = 31 * result + (value != null ? value.hashCode() : 0);
            result = 31 * result + (exact ? 1 : 0);
            return result;
        }
    }

    private class Group implements Operand<Group> {

        UnOperatorTerm unOperator = null;
        BinOperatorTerm biOperator = null;
        Group group = null;
        Term term = null;

        private String query = null;
        private int hash = 0;

        boolean isEmpty() {

            boolean termIsEmpty = term == null || term instanceof Group && ((Group) term).isEmpty();

            return termIsEmpty
                && unOperator == null
                && biOperator == null
                && group == null;
        }

        void startGroup() {
            if (group != null) {
                group.startGroup();
            } else {
                Group group = new Group();
                addTerm(group);
                this.group = group;
            }
            query = null;
        }

        void stopGroup() {
            if (group == null) {
                throw new IllegalStateException("Bracket not open");
            }
            Group finalGroup = this;
            while (finalGroup.group.group != null) {
                finalGroup = finalGroup.group;
            }
            finalGroup.group = null;
            query = null;
        }

        void setUnOperator(UnOperatorTerm operator) {
            if (group != null) {
                group.setUnOperator(operator);
            } else if (unOperator != null
                && NOT.equalsIgnoreCase(unOperator.operator)
                && NOT.equalsIgnoreCase(operator.operator)) {
                unOperator = null;
            } else {
                unOperator = operator;
            }
            query = null;
        }

        void setBiOperator(BinOperatorTerm operator) {
            if (group != null) {
                group.setBiOperator(operator);
            } else {
                if (term == null) {
                    if (biOperator != null) {
                        biOperator.operator = operator.operator;
                    }
                } else {
                    biOperator = operator;
                    biOperator.term0 = term;
                    term = null;
                }
            }
            query = null;
        }

        void addTerm(Term term) {
            if (group != null) {
                group.addTerm(term);
            } else {
                Term result = term;
                if (unOperator != null) {
                    unOperator.term = result;
                    result = unOperator;
                    unOperator = null;
                }
                if (biOperator != null) {
                    biOperator.term1 = result;
                    result = biOperator;
                    biOperator = null;
                } else if (this.term != null) {
                    throw new IllegalStateException("Search query building error. You should specify binary operator to combine two terms");
                }
                this.term = result;
            }
            query = null;
        }

        public String getQuery() {

            if (query != null) {
                return query;
            }

            StringBuilder sb = new StringBuilder();
            toString(sb, true);
            query = sb.toString();

            return query;
        }

        @Override
        public Group copy() {
            Group result = new Group();
            result.unOperator = unOperator;
            result.biOperator = biOperator;
            result.group = group;
            result.term = term;
            return result;
        }

        @Override
        public void toString(StringBuilder builder) {
            toString(builder, false);
        }

        private void toString(StringBuilder builder, boolean isRootGroup) {

            Term term = this.term;

            if (term == null && biOperator != null) {
                term = biOperator.term0;
            }

            if (term instanceof Operand) {

                if (term instanceof Group) {
                    ((Group) term).toString(builder, isRootGroup);
                } else {
                    term.toString(builder);
                }

            } else {

                boolean withBrackets = !isRootGroup && term instanceof BinOperatorTerm;

                if (withBrackets) {
                    builder.append('(');
                }
                if (term != null) {
                    term.toString(builder);
                } else if (isRootGroup) {
                    builder.append("()");
                }
                if (withBrackets) {
                    builder.append(')');
                }
            }
        }

        @Override
        public boolean equals(Object o) {

            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Group group = (Group) o;

            return term.equals(group.term);
        }

        @Override
        public int hashCode() {
            if (hash == 0) {
                hash = term.hashCode();
            }
            return hash;
        }
    }
}
