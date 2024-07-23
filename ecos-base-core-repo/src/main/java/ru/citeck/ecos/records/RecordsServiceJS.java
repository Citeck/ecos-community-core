package ru.citeck.ecos.records;

import kotlin.Unit;
import lombok.Data;
import org.alfresco.util.ParameterCheck;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.IteratorUtils;
import org.springframework.beans.factory.annotation.Autowired;
import ru.citeck.ecos.action.group.ActionResult;
import ru.citeck.ecos.action.group.ActionResults;
import ru.citeck.ecos.action.group.GroupActionConfig;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.records2.request.rest.QueryBody;
import ru.citeck.ecos.records2.request.rest.RestHandler;
import ru.citeck.ecos.records3.RecordsService;
import ru.citeck.ecos.records3.iter.IterableRecordRefs;
import ru.citeck.ecos.records3.iter.IterableRecordsConfig;
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts;
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery;
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes;
import ru.citeck.ecos.utils.AlfrescoScopableProcessorExtension;
import ru.citeck.ecos.utils.JsUtils;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import java.util.*;

public class RecordsServiceJS extends AlfrescoScopableProcessorExtension {

    private static final String TMP_ATT_NAME = "a";

    private RecordGroupActionsService groupActionsService;
    private RecordsService recordsServiceV1;
    private ru.citeck.ecos.records2.RecordsService recordsServiceV0;

    @Autowired
    private RestHandler restHandler;

    private JsUtils jsUtils;

    public ActionResult<EntityRef>[] executeAction(Object nodes, Object config) {

        List<EntityRef> records = jsUtils.getList(nodes, jsUtils::getRecordRef);
        GroupActionConfig actionConfig = jsUtils.toJava(config, GroupActionConfig.class);

        return toArray(groupActionsService.executeAction(records, actionConfig));
    }

    public String getAttribute(Object record, String attribute) {
        Map<String, String> attributesMap = new HashMap<>();
        attributesMap.put(TMP_ATT_NAME, attribute);
        RecordAtts meta = recordsServiceV1.getAtts(jsUtils.getRecordRef(record), attributesMap);
        return meta.getAtt(TMP_ATT_NAME, "");
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

    private Object getRecordAttributes(EntityRef recordRef, Object attributes) {

        if (attributes instanceof Collection) {
            return recordsServiceV1.getAtts(recordRef, (Collection<String>) attributes);
        } else if (attributes instanceof Map) {
            return recordsServiceV1.getAtts(recordRef, (Map<String, String>) attributes);
        }

        throwIncorrectAttributesType(attributes);
        return null;
    }

    private Object getRecordsAttributes(Collection<EntityRef> records, Object attributes) {

        if (attributes instanceof Collection) {
            return recordsServiceV1.getAtts(records, (Collection<String>) attributes);
        } else if (attributes instanceof Map) {
            return recordsServiceV1.getAtts(records, (Map<String, String>) attributes);
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

    public <T> RecsQueryRes<T> getRecords(Object recordsQuery, Class<T> schemaClass) {
        return queryRecords(recordsQuery, schemaClass);
    }

    public <T> RecsQueryRes<T> queryRecords(Object recordsQuery, Class<T> schemaClass) {
        RecordsQuery convertedQuery = jsUtils.toJava(recordsQuery, RecordsQuery.class);
        return recordsServiceV1.query(convertedQuery, schemaClass);
    }

    public Iterable<EntityRef> getIterableRecords(Object recordsQuery) {
        RecordsQuery query = jsUtils.toJava(recordsQuery, RecordsQuery.class);
        return new IterableRecordRefs(query, IterableRecordsConfig.create(b -> Unit.INSTANCE), recordsServiceV1);
    }

    public Iterable<EntityRef> getIterableRecordsForGroupAction(Object recordsQuery, Object groupActionConfig) {

        GroupActionConfig config = jsUtils.toJava(groupActionConfig, GroupActionConfig.class);

        IterableRecordsConfig iterRecsConfig = IterableRecordsConfig.create(b -> {
            String pageSizeParamStr = config.getStrParam("pageSize");
            if (StringUtils.isNotBlank(pageSizeParamStr)) {
                b.withPageSize(Integer.parseInt(pageSizeParamStr));
            }
            return Unit.INSTANCE;
        });

        RecordsQuery query = jsUtils.toJava(recordsQuery, RecordsQuery.class);
        return new IterableRecordRefs(query, iterRecsConfig, recordsServiceV1);
    }

    public Iterable<EntityRef> getIterableRecordsForGroupAction(Object recordsQuery,
                                                                Object groupActionConfig,
                                                                Object options) {
        Iterable<EntityRef> recordRefs = getIterableRecordsForGroupAction(recordsQuery, groupActionConfig);
        return filteredOptions(recordRefs, options);
    }

    public Iterable<EntityRef> getIterableRecords(Object recordsQuery, Object options) {
        Iterable<EntityRef> recordRefs = getIterableRecords(recordsQuery);
        return filteredOptions(recordRefs, options);
    }

    @SuppressWarnings("unchecked")
    private Iterable<EntityRef> filteredOptions(Iterable<EntityRef> recordRefs, Object options) {
        if (options == null) {
            return recordRefs;
        }
        IterableRecordsOptions convertedOptions = Json.getMapper().convert(jsUtils.toJava(options), IterableRecordsOptions.class);
        if (convertedOptions == null || CollectionUtils.isEmpty(convertedOptions.getExcludedRecords())) {
            return recordRefs;
        }
        return () -> IteratorUtils.filteredIterator(
            recordRefs.iterator(),
            recordRef -> !convertedOptions.getExcludedRecords().contains((EntityRef) recordRef));
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
    public void setGroupActionsService(RecordGroupActionsService groupActionsService) {
        this.groupActionsService = groupActionsService;
    }

    @Autowired
    public void setRecordsServiceV1(RecordsService recordsServiceV1) {
        this.recordsServiceV1 = recordsServiceV1;
    }

    @Autowired
    public void setRecordsServiceV0(ru.citeck.ecos.records2.RecordsService recordsServiceV0) {
        this.recordsServiceV0 = recordsServiceV0;
    }

    @Data
    public static class IterableRecordsOptions {
        private Set<EntityRef> excludedRecords;
    }
}
