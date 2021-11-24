package ru.citeck.ecos.records;

import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Data;
import org.alfresco.util.ParameterCheck;
import org.apache.commons.collections4.IterableUtils;
import org.springframework.beans.factory.annotation.Autowired;
import ru.citeck.ecos.action.group.ActionResult;
import ru.citeck.ecos.action.group.ActionResults;
import ru.citeck.ecos.action.group.GroupActionConfig;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.records2.IterableRecords;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.rest.QueryBody;
import ru.citeck.ecos.records2.request.rest.RestHandler;
import ru.citeck.ecos.records2.request.result.RecordsResult;
import ru.citeck.ecos.utils.AlfrescoScopableProcessorExtension;
import ru.citeck.ecos.utils.JsUtils;

import java.util.*;

public class RecordsServiceJS extends AlfrescoScopableProcessorExtension {

    private static final String TMP_ATT_NAME = "a";

    @Autowired
    private RecordsServiceImpl recordsService;
    @Autowired
    private RestHandler restHandler;

    private JsUtils jsUtils;

    public ActionResult<RecordRef>[] executeAction(Object nodes, Object config) {

        List<RecordRef> records = jsUtils.getList(nodes, jsUtils::getRecordRef);
        GroupActionConfig actionConfig = jsUtils.toJava(config, GroupActionConfig.class);

        return toArray(recordsService.executeAction(records, actionConfig));
    }

    public String getAttribute(Object record, String attribute) {
        Map<String, String> attributesMap = new HashMap<>();
        attributesMap.put(TMP_ATT_NAME, attribute);
        RecordMeta meta = recordsService.getAttributes(jsUtils.getRecordRef(record), attributesMap);
        return meta.getAttribute(TMP_ATT_NAME, "");
    }

    public Object getAttributes(Object records, Object attributes) {

        ParameterCheck.mandatory("records", records);
        ParameterCheck.mandatory("attributes", attributes);

        Object javaRecords = jsUtils.toJava(records);
        Object javaAttributes = jsUtils.toJava(attributes);

        if (javaRecords instanceof Collection) {
            return getRecordsAttributes(jsUtils.getList(javaRecords, jsUtils::getRecordRef), javaAttributes);
        } else {
            return getRecordAttributes(jsUtils.getRecordRef(javaRecords), javaAttributes);
        }
    }

    private Object getRecordAttributes(RecordRef recordRef, Object attributes) {

        if (attributes instanceof Collection) {
            return recordsService.getAttributes(recordRef, (Collection<String>) attributes);
        } else if (attributes instanceof Map) {
            return recordsService.getAttributes(recordRef, (Map<String, String>) attributes);
        }

        throwIncorrectAttributesType(attributes);
        return null;
    }

    private Object getRecordsAttributes(Collection<RecordRef> records, Object attributes) {

        if (attributes instanceof Collection) {
            return recordsService.getAttributes(records, (Collection<String>) attributes);
        } else if (attributes instanceof Map) {
            return recordsService.getAttributes(records, (Map<String, String>) attributes);
        }

        throwIncorrectAttributesType(attributes);
        return null;
    }

    private void throwIncorrectAttributesType(Object attributes) throws RuntimeException {
        throw new IllegalArgumentException("Attributes type is not supported! " + attributes.getClass());
    }

    public Object getRecords(Object recordsQuery) {
        QueryBody request = jsUtils.toJava(recordsQuery, QueryBody.class);
        return restHandler.queryRecords(request);
    }

    public <T> RecordsResult<T> getRecords(Object recordsQuery, Class<T> schemaClass) {
        return queryRecords(recordsQuery, schemaClass);
    }

    public <T> RecordsResult<T> queryRecords(Object recordsQuery, Class<T> schemaClass) {
        RecordsQuery convertedQuery = jsUtils.toJava(recordsQuery, RecordsQuery.class);
        return recordsService.queryRecords(convertedQuery, schemaClass);
    }

    public Iterable<RecordRef> getIterableRecords(Object recordsQuery) {
        RecordsQuery convertedQuery = jsUtils.toJava(recordsQuery, RecordsQuery.class);
        return new IterableRecords(recordsService, convertedQuery);
    }

    public Iterable<RecordRef> getIterableRecords(Object recordsQuery, Object options) {
        Iterable<RecordRef> queryResult = getIterableRecords(recordsQuery);
        if (options == null) {
            return queryResult;
        }
        IterableRecordsOptions convertedOptions = Json.getMapper().convert(options, IterableRecordsOptions.class);
        if (convertedOptions == null || convertedOptions.getExcludedRecords().isEmpty()) {
            return queryResult;
        }
        return IterableUtils.filteredIterable(
            queryResult,
            recordRef -> !convertedOptions.getExcludedRecords().contains(recordRef));
    }

    private static <T> ActionResult<T>[] toArray(ActionResults<T> results) {
        @SuppressWarnings("unchecked")
        ActionResult<T>[] result = new ActionResult[results.getResults().size()];
        return results.getResults().toArray(result);
    }

    @Autowired
    public void setRestQueryHandler(RestHandler restHandler) {
        this.restHandler = restHandler;
    }

    @Autowired
    public void setJsUtils(JsUtils jsUtils) {
        this.jsUtils = jsUtils;
    }

    @Autowired
    public void setRecordsService(RecordsServiceImpl recordsService) {
        this.recordsService = recordsService;
    }

    @Data
    public static class IterableRecordsOptions {
        private List<RecordRef> excludedRecords = Collections.emptyList();

        public List<RecordRef> getExcludedRecords() {
            return excludedRecords;
        }

        @JsonSetter
        public void setExcludedRecords(Object excludedRecords) {
            List<RecordRef> result = new ArrayList<>();
            if (excludedRecords instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> listExcludedRecords = (List<String>) excludedRecords;
                for (String ref : listExcludedRecords) {
                    result.add(RecordRef.valueOf(ref));
                }
            }
            this.excludedRecords = result;
        }
    }
}
