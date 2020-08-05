package ru.citeck.ecos.records.language.predicate.converters.impl.constants;

public class ValuePredicateToFtsAlfrescoConstants {
    // Attribute types
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

    // System journal search parameters
    public static final String SEARCH_PROPS = "search-value-properties-names";
    public static final String SEARCH_EXCLUDED_TYPES = "search-type-names-excluded";
    public static final String SEARCH_EXCLUDED_ASPECTS = "search-aspect-names-excluded";

    // User ids
    public static final String CURRENT_USER = "$CURRENT";
    public static final String SYSTEM = "system";

    // Exception templates
    public static final String UNKNOWN_VALUE_PREDICATE_TYPE = "Unknown value predicate type: %s";
    public static final String CANNOT_PARSE_TIME = "Cannot parse time";

    // TODO: Check attributes usage
    public static final String CM_MODIFIED_ATTRIBUTE = "cm:modified";
    public static final String CM_MODIFIER_ATTRIBUTE = "cm:modifier";
    public static final String WFM_ACTORS_ATTRIBUTE = "wfm:actors";

    // System constants
    public static final String COMMA_DELIMITER = ",";
    public static final String SLASH_DELIMITER = "/";
    public static final String WORKSPACE_PREFIX = "workspace://SpacesStore/";
    public static final int INNER_QUERY_MAX_ITEMS = 20;
}
