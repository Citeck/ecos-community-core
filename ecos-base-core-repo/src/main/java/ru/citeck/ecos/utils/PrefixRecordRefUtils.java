package ru.citeck.ecos.utils;

import org.alfresco.service.cmr.repository.NodeRef;
import org.jetbrains.annotations.NotNull;
import ru.citeck.ecos.records2.RecordRef;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PrefixRecordRefUtils {
    public static final String PREFIX_EMODEL_PERSON = "emodel/person@";
    public static final String PREFIX_EMODEL_GROUP = "emodel/authority-group@";
    public static final String PREFIX_ALFRESCO = "alfresco/@";
    public static final String PREFIX_GROUP = "GROUP_";
    public static final String AUTHORITY_REGEX = "emodel/(person|authority-group)@[-\\w]+";

    /**
     * Remove or replace first prefix in RecordRef string
     * @param refValue RecordRef string
     * @return modified RecordRef string
     */
    public static String replaceFirstPrefix(String refValue) {
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

    public static boolean isAuthority(String refValue){
        return refValue.startsWith(PrefixRecordRefUtils.PREFIX_EMODEL_GROUP)
            || refValue.startsWith(PrefixRecordRefUtils.PREFIX_EMODEL_PERSON);
    }

    /**
     * Replace user ID and group ID entries by its nodeRef value
     * @return string with nodeRef replacements
     */
    public static String replaceAuthorityNodes(String queryString, @NotNull AuthorityUtils authorityUtils) {
        HashMap<String,String> replacements = new HashMap<>();
        Pattern pattern = Pattern.compile(AUTHORITY_REGEX);
        Matcher matcher = pattern.matcher(queryString);
        while (matcher.find()) {
            String currentValue = matcher.group();
            if (!replacements.containsKey(currentValue)) {
                NodeRef replacement = authorityUtils.getNodeRef(currentValue);
                replacements.put(currentValue, replacement.toString());
            }
        }
        String result = queryString;
        for (Map.Entry<String,String> entry: replacements.entrySet()) {
            result = result.replaceAll(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /**
     * Add prefix to ID if necessary
     * @return record ID
     */
    public static String getId(String recordRef) {
        String recordId = RecordRef.valueOf(recordRef).getId();
        if (recordRef.startsWith(PREFIX_EMODEL_GROUP)) {
            recordId = PREFIX_GROUP + recordId;
        }
        return recordId;
    }
}
