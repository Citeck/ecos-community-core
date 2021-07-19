package ru.citeck.ecos.history;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.events.EventConnection;
import ru.citeck.ecos.events.data.dto.pasrse.EventDtoFactory;
import ru.citeck.ecos.events.data.dto.record.Attribute;
import ru.citeck.ecos.events.data.dto.record.RecordEventDto;
import ru.citeck.ecos.events.data.dto.record.RecordEventType;
import ru.citeck.ecos.records.source.alf.meta.AlfNodeRecord;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsService;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Roman Makarskiy
 */
@Slf4j
@Service
public class RecordEventService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String SPACES_STORE_PREFIX = "workspace://SpacesStore/";
    private static final String ALFRESCO_SOURCE = "alfresco@";
    private static final String ATTR_STR_DISPLAY_SCHEMA = "[]{.str,.disp}";
    private static final String ATTR_CASE_STATUS = "caseStatus";

    private static final List<String> commonAttr = Arrays.asList(
            AlfNodeRecord.ATTR_DOC_SUM,
            ATTR_CASE_STATUS,
            RecordConstants.ATT_TYPE
    );

    private final EventConnection eventConnection;
    private final RecordsService recordsService;

    @Value("${event.record.create.emit.enabled}")
    private boolean eventRecordCreateEnabled;

    @Value("${event.record.update.emit.enabled}")
    private boolean eventRecordUpdateEnabled;

    @Value("${ecos.server.tenant.id}")
    private String TENANT_ID;

    @Autowired
    public RecordEventService(EventConnection eventConnection, RecordsService recordsService) {
        this.eventConnection = eventConnection;
        this.recordsService = recordsService;
    }

    public void emitAttrChanged(RecordEventType eventType, String docId, Set<String> attrChanged) {
        if (CollectionUtils.isEmpty(attrChanged) || !emitEnabled(RecordEventType.UPDATE)) {
            return;
        }

        if (eventConnection == null) {
            throw new RuntimeException("Sending event of updating attributes is required," +
                " but connection to event server is not enabled. Check you configs.");
        }

        log.debug("Record event emit, docId:{}, atts:{}", docId, attrChanged);

        List<Attribute> attr = getAttributesFromRecord(docId, attrChanged);
        if (attr.isEmpty()) {
            return;
        }

        RecordEventDto dto = new RecordEventDto();
        dto.setVersion(1L);
        dto.setCreatedDate(new Date());
        dto.setType(eventType.toString());
        dto.setDocId(ALFRESCO_SOURCE + docId);
        dto.setCreatedDate(new Date());
        dto.setAttrChanges(attr);

        eventConnection.emit(EventDtoFactory.toEventDto(dto), TENANT_ID);
    }

    private List<Attribute> getAttributesFromRecord(String docId, Set<String> prefixStringAttributes) {
        if (CollectionUtils.isEmpty(prefixStringAttributes)) {
            return Collections.emptyList();
        }

        prefixStringAttributes.addAll(commonAttr);

        List<String> attributesToResolve = prefixStringAttributes.stream()
                .map(s -> s + ATTR_STR_DISPLAY_SCHEMA)
                .collect(Collectors.toList());

        RecordRef recordRef = RecordRef.create("", docId);
        RecordMeta attributes = recordsService.getAttributes(recordRef,
                attributesToResolve);

        List<Attribute> changes = new ArrayList<>();

        attributes.forEachJ((name, attrJsonNode) -> {
            if (attrJsonNode != null && !attrJsonNode.isNull()) {
                Attribute attr = new Attribute();

                String attrName = StringUtils.remove(name, ATTR_STR_DISPLAY_SCHEMA);
                attr.setName(attrName);

                List<Map<String, String>> values = new ArrayList<>();

                if (attrJsonNode.isArray()) {
                    attrJsonNode.forEach(n -> values.add(fromJsonNode(n)));
                } else {
                    values.add(fromJsonNode(attrJsonNode));
                }

                attr.setValues(values);

                changes.add(attr);
            }
        });

        return changes;
    }

    private Map<String, String> fromJsonNode(DataValue node) {
        Map<String, String> resMap = new HashMap<>();

        Map<String, String> map = OBJECT_MAPPER.convertValue(node, new TypeReference<Map<String, Object>>() {
        });
        map.forEach((k, v) -> {
            v = StringUtils.startsWith(v, SPACES_STORE_PREFIX) ? ALFRESCO_SOURCE + v : v;
            resMap.put(k, v);
        });

        return resMap;
    }

    private boolean emitEnabled(RecordEventType type) {
        boolean enabled = false;

        if (RecordEventType.UPDATE.equals(type)) {
            enabled = eventRecordUpdateEnabled;
        }

        if (RecordEventType.CREATE.equals(type)) {
            enabled = eventRecordCreateEnabled;
        }

        log.debug("Record event emit, type:{}, enabled:{}", type.toString(), enabled);

        return enabled;
    }

}
