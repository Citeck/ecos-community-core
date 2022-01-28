package ru.citeck.ecos.search.ftsquery;

import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.search.PermissionEvaluationMode;
import org.alfresco.service.cmr.search.QueryConsistency;
import org.alfresco.service.cmr.search.SearchParameters;
import org.alfresco.service.namespace.QName;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;

public interface OperandExpected {

    /**
     * Field has any of specified values.
     * Construct multiple key-value pairs and join it by OR operator
     * @param field field to check value
     * @param values values to check
     * @throws IllegalArgumentException if values is empty
     */
    OperatorExpected any(QName field, Iterable<Serializable> values);

    OperatorExpected any(QName field, Iterable<Serializable> values, boolean exact);

    /**
     * Add multiple @key:"value" pairs to query
     * @param values values which would be added to query
     * @throws IllegalArgumentException if values is empty
     */
    OperatorExpected values(Map<QName, Serializable> values);

    /**
     * Add multiple @key:"value" pairs to query
     * @param values values which would be added to query
     * @param joinOperator operator to join specified pairs
     * @throws IllegalArgumentException if values is empty
     * @see BinOperator
     */
    OperatorExpected values(Map<QName, Serializable> values, BinOperator joinOperator);

    /**
     * Add multiple @key:"value" pairs to query
     * @param values values which would be added to query
     * @param joinOperator operator to join specified pairs
     * @param exact search by exact match or not
     * @throws IllegalArgumentException if values is empty
     * @see BinOperator
     */
    OperatorExpected values(Map<QName, Serializable> values, BinOperator joinOperator, boolean exact);

    /**
     * Field has exact value
     * If value is null then it will be empty() check
     * @param value value to check
     * @see FTSQuery#value(QName, Serializable)
     */
    OperatorExpected exact(QName field, Serializable value);

    /**
     * Any field has value
     * @param value value to check
     */
    FTSQuery value(Serializable value);

    /**
     * Field has value
     * If value is null then it will be empty() check
     * @param value value to check
     */
    OperatorExpected value(QName field, Serializable value);

    /**
     * Field has value.
     * If value is null then it will be empty() check
     * @param value value to check
     * @param exact search by exact match or not
     */
    OperatorExpected value(QName field, Serializable value, boolean exact);

    /**
     * Field contains value.
     * If value is null then it will be empty() check
     * @param value value to check
     */
    OperatorExpected contains(QName field, Serializable value);

    /**
     * Search by date field from MIN to "to" argument
     * @param field date field. mandatory
     */
    FTSQuery rangeTo(QName field, Date to);

    /**
     * Search by date field from "from" argument to MAX
     * @param field date field. mandatory
     */
    FTSQuery rangeFrom(QName field, Date from);

    /**
     * Search by date field from "from" to "to" arguments
     * at least one of "from" or "to" arguments must be specified
     * @param from converts to MIN if null
     * @param to converts to MAX if null
     * @param field date field. mandatory
     */
    FTSQuery range(QName field, Date from, Date to);

    /**
     * Search by numeric field from MIN to "to" argument
     * @param field numeric field. mandatory
     */
    FTSQuery rangeTo(QName field, Number to);

    /**
     * Search by numeric field from "from" argument to MAX
     * @param field numeric field. mandatory
     */
    FTSQuery rangeFrom(QName field, Number from);

    /**
     * Search by numeric field from "from" to "to" arguments
     * at least one of "from" or "to" arguments must be specified
     * @param from converts to MIN if null
     * @param to converts to MAX if null
     * @param field numeric field. mandatory
     */
    FTSQuery range(QName field, Number from, Number to);

    /**
     * Search by any field from "from" to "to" arguments
     * at least one of "from" or "to" arguments must be specified
     * @param from converts to MIN if null
     * @param to converts to MAX if null
     * @param field field name. mandatory
     */
    FTSQuery range(QName field, String from, String to);

    /**
     * Search by any field from "from" to "to" arguments
     * at least one of "from" or "to" arguments must be specified
     * @param from converts to MIN if null
     * @param to converts to MAX if null
     * @param field field name. mandatory
     * @param frIncl include 'from' value to search or not
     * @param toIncl include 'to' value to search or not
     */
    FTSQuery range(QName field, String from, boolean frIncl, String to, boolean toIncl);

    /**
     * Add path term
     */
    OperatorExpected path(String path);

    /**
     * Field is set
     */
    OperatorExpected isSet(QName field);

    /**
     * Field is unset
     */
    OperatorExpected isUnset(QName field);

    /**
     * Field is null
     */
    OperatorExpected isNull(QName field);

    /**
     * Field is not null
     */
    OperatorExpected isNotNull(QName field);

    /**
     * Field is null or unset
     * @param field field to check
     */
    OperatorExpected empty(QName field);

    /**
     * Field value is null on unset or empty string
     */
    OperatorExpected emptyString(QName field);

    /**
     * Search within parent
     */
    OperatorExpected parent(NodeRef parent);

    /**
     * Search by type
     */
    OperatorExpected type(QName typeName);

    /**
     * Search by aspect
     */
    OperatorExpected aspect(QName aspectName);

    /**
     * Operator NOT
     */
    OperandExpected not();

    /**
     * Operator TRUE
     */
    OperatorExpected alwaysTrue();

    /**
     * Operator FALSE
     */
    OperatorExpected alwaysFalse();

    /**
     * Open bracket
     */
    OperandExpected open();

    /**
     * Search in database instead of SOLR
     */
    OperandExpected transactional();

    /**
     * Search in database or in SOLR if query not supported by database
     */
    OperandExpected transactionalIfPossible();

    /**
     * Search in SOLR without database
     */
    OperandExpected eventual();

    /**
     * Set query consistency
     */
    OperandExpected consistency(QueryConsistency consistency);

    /**
     * Get copy of query
     */
    OperandExpected copy();

    /**
     * Set bulk fetch
     */
    OperandExpected bulkFetch(boolean value);

    /**
     * Set max items
     */
    OperandExpected maxItems(int value);

    /**
     * Set skip count
     */
    OperandExpected skipCount(int value);

    /**
     * Add sorting
     */
    OperandExpected addSort(QName field, boolean ascending);
    OperandExpected addSort(String field, boolean ascending);
    OperandExpected addSort(SearchParameters.SortDefinition sortDefinition);

    /**
     * Set permissions mode
     */
    OperandExpected permissionsMode(PermissionEvaluationMode mode);

    /**
     * Search all nodes without limits
     */
    OperandExpected unlimited();
}
