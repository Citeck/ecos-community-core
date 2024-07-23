package ru.citeck.ecos.records;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.alfresco.service.cmr.repository.NodeRef;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.action.group.ActionResult;
import ru.citeck.ecos.action.group.ActionStatus;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.request.result.RecordsResult;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class RecordsUtils {

    private static final Log logger = LogFactory.getLog(RecordsUtils.class);

    public static <T> List<ActionResult<EntityRef>> processList(List<EntityRef> records,
                                                                Function<EntityRef, T> toNode,
                                                                Function<List<T>, Map<T, ActionStatus>> process) {

        List<ActionResult<EntityRef>> results = new ArrayList<>();

        Map<T, EntityRef> mapping = new HashMap<>();

        List<T> nodes = new ArrayList<>();
        for (EntityRef recordRef : records) {
            T node = toNode.apply(recordRef);
            if (node == null) {
                ActionStatus status = new ActionStatus(ActionStatus.STATUS_SKIPPED);
                status.setMessage(recordRef + " is not a valid node");
                results.add(new ActionResult<>(recordRef, status));
                continue;
            }
            mapping.put(node, recordRef);
            nodes.add(node);
        }

        Map<T, ActionStatus> statuses = process.apply(nodes);
        statuses.forEach((node, status) ->
            results.add(new ActionResult<>(mapping.get(node), status))
        );

        return results;
    }

    public static EntityRef getRecordId(ObjectNode recordMeta) {
        JsonNode idNode = recordMeta.get("id");
        String id = idNode != null && idNode.isTextual() ? idNode.asText() : null;
        return EntityRef.valueOf(id);
    }

    @Nullable
    public static NodeRef toNodeRef(EntityRef recordRef) {
        String nodeRefStr = recordRef.getLocalId();
        int sourceDelimIdx = nodeRefStr.lastIndexOf(EntityRef.SOURCE_ID_DELIMITER);
        if (sourceDelimIdx > -1) {
            nodeRefStr = nodeRefStr.substring(sourceDelimIdx + 1);
        }
        if (NodeRef.isNodeRef(nodeRefStr)) {
            return new NodeRef(nodeRefStr);
        }
        return null;
    }

    public static List<EntityRef> toLocalRecords(Collection<EntityRef> records) {
        return records.stream()
                      .map(r -> EntityRef.valueOf(r.getLocalId()))
                      .collect(Collectors.toList());
    }

    public static List<NodeRef> toNodeRefs(List<EntityRef> records) {
        return records.stream()
                      .map(r -> {
                          String id = r.getLocalId();
                          int lastDelim = id.lastIndexOf(EntityRef.SOURCE_ID_DELIMITER);
                          if (lastDelim > -1) {
                              id = id.substring(lastDelim + 1);
                          }
                          return new NodeRef(id);
                      })
                      .collect(Collectors.toList());
    }

    public String getMetaValueId(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof MetaValue) {
            return ((MetaValue) value).getId();
        }
        try {
            Object propValue = PropertyUtils.getProperty(value, "id");
            return propValue != null ? propValue.toString() : null;
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            logger.error(e);
        }
        return null;
    }

    public static List<RecordMeta> toScopedRecordsMeta(String sourceId, List<RecordMeta> records) {
        if (StringUtils.isBlank(sourceId)) {
            return records;
        }
        return records.stream()
                      .map(n -> new RecordMeta(EntityRef.create(sourceId, n.getId().toString()), n.getAttributes()))
                      .collect(Collectors.toList());
    }

    public static RecordsResult<EntityRef> toScoped(String sourceId, RecordsResult<EntityRef> result) {
        return new RecordsResult<>(result, r -> EntityRef.create(sourceId, r.toString()));
    }

    public static RecordsQueryResult<EntityRef> toScoped(String sourceId, RecordsQueryResult<EntityRef> result) {
        return new RecordsQueryResult<>(result, r -> EntityRef.create(sourceId, r.toString()));
    }

    public static List<EntityRef> toScopedRecords(String sourceId, List<EntityRef> records) {
        return records.stream()
                      .map(r -> EntityRef.create(sourceId, r.toString()))
                      .collect(Collectors.toList());
    }

    public static List<EntityRef> strToRecords(String sourceId, List<String> records) {
        return records.stream()
                      .map(r -> EntityRef.create(sourceId, r))
                      .collect(Collectors.toList());
    }

    public static List<EntityRef> nodeRefsToRecords(String sourceId, List<NodeRef> records) {
        return records.stream()
                      .map(r -> EntityRef.create(sourceId, r.toString()))
                      .collect(Collectors.toList());
    }

    public static Map<String, List<EntityRef>> groupRefBySource(Collection<EntityRef> records) {
        return groupBySource(records, r -> r, (r, d) -> r);
    }

    public static <T> Map<String, List<RecordInfo<T>>> groupInfoBySource(Collection<RecordInfo<T>> records) {
        return groupBySource(records, RecordInfo::getRef, (r, d) -> d);
    }

    public static Map<String, List<RecordMeta>> groupMetaBySource(Collection<RecordMeta> records) {
        return groupBySource(records, RecordMeta::getId, (r, d) -> d);
    }

    public static <V> Map<EntityRef, V> convertToRefs(Map<String, V> data) {
        Map<EntityRef, V> result = new HashMap<>();
        data.forEach((id, recMeta) -> result.put(EntityRef.valueOf(id), recMeta));
        return result;
    }

    public static <V> Map<EntityRef, V> convertToRefs(String sourceId, Map<String, V> data) {
        Map<EntityRef, V> result = new HashMap<>();
        data.forEach((id, recMeta) -> result.put(EntityRef.create(sourceId, id), recMeta));
        return result;
    }

    public static  List<RecordMeta> convertToRefs(String sourceId, List<RecordMeta> data) {
        return toScopedRecordsMeta(sourceId, data);
    }

    private static <I, O> Map<String, List<O>> groupBySource(Collection<I> records,
                                                            Function<I, EntityRef> getRecordRef,
                                                            BiFunction<EntityRef, I, O> toOutput) {
        Map<String, List<O>> result = new HashMap<>();
        for (I recordData : records) {
            EntityRef record = getRecordRef.apply(recordData);
            String sourceId = record.getSourceId();
            List<O> outList = result.computeIfAbsent(sourceId, key -> new ArrayList<>());
            outList.add(toOutput.apply(record, recordData));
        }
        return result;
    }

    public static List<EntityRef> toRecords(Collection<String> strRecords) {
        return strRecords.stream().map(EntityRef::valueOf).collect(Collectors.toList());
    }
}
