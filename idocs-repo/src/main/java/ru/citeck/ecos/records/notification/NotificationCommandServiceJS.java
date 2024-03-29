package ru.citeck.ecos.records.notification;

import org.alfresco.service.cmr.repository.NodeRef;
import org.springframework.beans.factory.annotation.Autowired;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.notifications.lib.Notification;
import ru.citeck.ecos.notifications.lib.NotificationType;
import ru.citeck.ecos.notifications.lib.service.NotificationService;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.utils.AlfrescoScopableProcessorExtension;
import ru.citeck.ecos.utils.JsUtils;
import ru.citeck.ecos.utils.UrlUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotificationCommandServiceJS extends AlfrescoScopableProcessorExtension {

    private static final String RECORD_PROP = "record";

    private NotificationService notificationService;
    private UrlUtils urlUtils;
    private JsUtils jsUtils;

    public void send(Object data) {

        Object notificationObj = jsUtils.toJava(data);
        if (notificationObj instanceof Map) {
            Object record = ((Map<?, ?>) notificationObj).get(RECORD_PROP);
            if (record instanceof NodeRef) {
                @SuppressWarnings("unchecked")
                Map<String, Object> newObj = new HashMap<>((Map<String, Object>) notificationObj);
                newObj.put(RECORD_PROP, record.toString());
                notificationObj = newObj;
            }
        }
        DataValue notificationValue = Json.getMapper().convert(notificationObj, DataValue.class);

        if (notificationValue == null) {
            throw new IllegalArgumentException("Incorrect notification: " + data);
        }

        DataValue record = notificationValue.get(RECORD_PROP);
        Object notificationRecord;
        if (record.isTextual()) {
            notificationRecord = RecordRef.valueOf(record.asText());
        } else {
            notificationRecord = record;
        }

        Notification notification = new Notification.Builder()
            .record(notificationRecord)
            .templateRef(RecordRef.valueOf(notificationValue.get("templateRef").asText()))
            .notificationType(NotificationType.valueOf(notificationValue.get("notificationType").asText()))
            .recipients(notificationValue.get("recipients").asStrList())
            .from(asStringOrNull(notificationValue.get("from")))
            .lang(asStringOrNull(notificationValue.get("lang")))
            .additionalMeta(notificationValue.get("additionalMeta").asMap(String.class, Object.class))
            .build();

        notificationService.send(notification);
    }

    private String asStringOrNull(DataValue value) {
        if (value.isTextual()) {
            return value.asText();
        }
        return null;
    }

    public void send(String record, String templateRef, String type, List<String> recipients, String from, String lang,
                     Map<String, Object> additionalMeta) {

        Notification notification = new Notification.Builder()
            .record(RecordRef.valueOf(record))
            .templateRef(RecordRef.valueOf(templateRef))
            .notificationType(NotificationType.valueOf(type))
            .recipients(recipients)
            .from(from)
            .lang(lang)
            .additionalMeta(additionalMeta)
            .build();

        notificationService.send(notification);
    }

    public void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Autowired
    public void setUrlUtils(UrlUtils urlUtils) {
        this.urlUtils = urlUtils;
    }

    @Autowired
    public void setJsUtils(JsUtils jsUtils) {
        this.jsUtils = jsUtils;
    }
}
