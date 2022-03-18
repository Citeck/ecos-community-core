package ru.citeck.ecos.utils;

public class PrefixRecordRefUtils {
    public static final String PREFIX_EMODEL_PERSON = "emodel/person@";
    public static final String PREFIX_EMODEL_GROUP = "emodel/authority-group@";
    public static final String PREFIX_ALFRESCO = "alfresco/@";
    public static final String PREFIX_GROUP = "GROUP_";

    /**
     * Remove or replace first prefix in RecordRef string
     * @param refValue RecordRef string
     * @return modified RecordRef string
     */
    public static String replaceFirstPrefix(String refValue){
        if (refValue.startsWith(PREFIX_ALFRESCO)) {
            return refValue.replaceFirst(PREFIX_ALFRESCO, "");
        }
        if (refValue.startsWith(PrefixRecordRefUtils.PREFIX_EMODEL_PERSON)) {
            return refValue.replaceFirst(PrefixRecordRefUtils.PREFIX_EMODEL_PERSON, "");
        }
        if (refValue.startsWith(PrefixRecordRefUtils.PREFIX_EMODEL_GROUP)) {
            return refValue.replaceFirst(PrefixRecordRefUtils.PREFIX_EMODEL_GROUP, PrefixRecordRefUtils.PREFIX_GROUP);
        }
        return refValue;
    }

    /**
     * Remove or replace prefixes in string
     * @return modified string
     */
    public static String replacePrefix(String stringWithPrefixes){
        String result = stringWithPrefixes.replace(PREFIX_ALFRESCO, "");
        result = result.replace(PrefixRecordRefUtils.PREFIX_EMODEL_PERSON, "");
        result = result.replace(PrefixRecordRefUtils.PREFIX_EMODEL_GROUP, PrefixRecordRefUtils.PREFIX_GROUP);
        return result;
    }
}
