package ru.citeck.ecos.records.script;

import org.alfresco.repo.jscript.ScriptNode;
import org.alfresco.repo.template.TemplateNode;
import org.springframework.beans.factory.annotation.Autowired;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records3.RecordsService;
import ru.citeck.ecos.records3.record.op.atts.dto.RecordAtts;
import ru.citeck.ecos.records3.record.op.query.dto.RecsQueryRes;
import ru.citeck.ecos.records3.record.op.query.dto.query.RecordsQuery;
import ru.citeck.ecos.utils.AlfrescoScopableProcessorExtension;
import ru.citeck.ecos.utils.JsUtils;

import java.util.*;
import java.util.stream.Collectors;

public class RepoScriptRecordsService extends AlfrescoScopableProcessorExtension {

    @Autowired
    private RecordsService recordsService;
    @Autowired
    private JsUtils jsUtils;

    public Object getAtts(Object record, Object attributes) {

        Object javaRecord = jsUtils.toJava(record);

        if (javaRecord instanceof Collection<?>) {
            return jsUtils.toScript(((Collection<?>) javaRecord).stream()
                .map(rec -> get(rec).load(attributes))
                .collect(Collectors.toList()));
        }
        return get(javaRecord).load(attributes);
    }

    public RepoScriptAttValueCtx get(Object record) {
        if (record == null) {
            return EmptyRecord.INSTANCE;
        }
        if (record instanceof RepoScriptAttValueCtx) {
            return (RepoScriptAttValueCtx) record;
        }
        Object javaRecord = jsUtils.toJava(record);
        RecordRef recordRef;
        if (javaRecord instanceof RecordRef) {
            recordRef = (RecordRef) javaRecord;
        } else if (javaRecord instanceof String) {
            recordRef = RecordRef.valueOf((String) javaRecord);
        } else if (javaRecord instanceof ScriptNode) {
            recordRef = RecordRef.valueOf(((ScriptNode) javaRecord).getNodeRef().toString());
        } else if (javaRecord instanceof TemplateNode) {
            recordRef = RecordRef.valueOf(((TemplateNode) javaRecord).getNodeRef().toString());
        } else {
            throw new RuntimeException("Incorrect record: " + javaRecord);
        }
        return new Record(recordRef);
    }

    private Map<String, Object> getEmptyRes() {

        Map<String, Object> emptyRes = new LinkedHashMap<>();
        emptyRes.put("hasMore", false);
        emptyRes.put("totalCount", 0);
        emptyRes.put("records", new Object[0]);
        emptyRes.put("messages", new Object[0]);
        return emptyRes;
    }

    public Object query(Object query) {

        if (query == null) {
            return getEmptyRes();
        }

        Object javaQuery = jsUtils.toJava(query);
        RecordsQuery recordsQuery = Json.getMapper().convert(javaQuery, RecordsQuery.class);

        if (recordsQuery == null) {
            return getEmptyRes();
        }

        RecsQueryRes<RecordRef> result = recordsService.query(recordsQuery);

        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("hasMore", result.getHasMore());
        resultMap.put("totalCount", result.getTotalCount());
        resultMap.put("messages", new Object[0]);
        resultMap.put("records", result.getRecords()
            .stream()
            .map(Object::toString)
            .toArray(String[]::new));

        return jsUtils.toScript(resultMap);
    }

    public Object query(Object query, Object attributes) {

        if (query == null) {
            return getEmptyRes();
        }

        Object javaQuery = jsUtils.toJava(query);
        RecordsQuery recordsQuery = Json.getMapper().convert(javaQuery, RecordsQuery.class);

        if (recordsQuery == null) {
            return getEmptyRes();
        }

        Map<String, Object> attributesToLoad = toRecordAttsMap(attributes);
        if (attributesToLoad == null) {
            attributesToLoad = Collections.emptyMap();
        }

        RecsQueryRes<RecordAtts> result = recordsService.query(recordsQuery, attributesToLoad);

        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("hasMore", result.getHasMore());
        resultMap.put("totalCount", result.getTotalCount());
        resultMap.put("messages", new Object[0]);
        resultMap.put("records", result.getRecords()
            .stream()
            .map(rec -> {
                DataValue resultData = rec.getAttributes().getData().copy();
                resultData.set("id", rec.getId().toString());
                return resultData.asMap(String.class, Object.class);
            })
            .toArray(Map<?, ?>[]::new));

        return jsUtils.toScript(resultMap);
    }

    private static Map<String, Object> toRecordAttsMap(Object attributes) {

        if (attributes == null) {
            return null;
        }
        Map<String, Object> attsMap = new HashMap<>();
        if (attributes instanceof String) {
            attsMap.put((String) attributes, attributes);
        } else if (attributes instanceof List<?>) {
            for (Object attribute : (List<?>) attributes) {
                if (attribute instanceof String) {
                    attsMap.put((String) attribute, attribute);
                }
            }
        } else if (attributes instanceof Map<?, ?>) {
            ((Map<?, ?>) attributes).forEach((k, v) -> attsMap.put(String.valueOf(k), v));
        } else {
            throw new RuntimeException("Incorrect attributes object: "
                + attributes + " of type " + attributes.getClass());
        }

        return attsMap;
    }

    private static class EmptyRecord implements RepoScriptAttValueCtx {

        public static EmptyRecord INSTANCE = new EmptyRecord();

        @Override
        public String getId() {
            return "";
        }

        @Override
        public RecordRef getRef() {
            return RecordRef.EMPTY;
        }

        @Override
        public String getLocalId() {
            return "";
        }

        @Override
        public Object load(Object attributes) {
            if (attributes instanceof String) {
                return null;
            }
            Map<String, Object> attsMap = toRecordAttsMap(attributes);
            Map<String, Object> result = new LinkedHashMap<>();
            attsMap.forEach((k, v) -> result.put(k, null));
            return result;
        }
    }

    private class Record implements RepoScriptAttValueCtx {

        private final RecordRef recordRef;

        public Record(RecordRef recordRef) {
            this.recordRef = recordRef;
        }

        @Override
        public String getId() {
            return "";
        }

        public RecordRef getRef() {
            return recordRef;
        }

        public String getLocalId() {
            return recordRef.getId();
        }

        @Override
        public Object load(Object attributes) {

            if (attributes == null) {
                return null;
            }

            Map<String, Object> attributesMap = toRecordAttsMap(jsUtils.toJava(attributes));
            RecordAtts result = recordsService.getAtts(recordRef, attributesMap);

            if (attributes instanceof String) {
                return jsUtils.toScript(result.getAtt((String) attributes).asJavaObj());
            } else {
                return jsUtils.toScript(result.getAtts().asJavaObj());
            }
        }
    }
}
