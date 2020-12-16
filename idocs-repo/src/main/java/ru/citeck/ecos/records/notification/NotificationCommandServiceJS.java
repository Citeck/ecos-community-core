package ru.citeck.ecos.records.notification;

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

    private NotificationService notificationService;
    private UrlUtils urlUtils;
    private JsUtils jsUtils;

    public void send(Object data) {

        Object notificationObj = jsUtils.toJava(data);
        DataValue notificationValue = Json.getMapper().convert(notificationObj, DataValue.class);

        if (notificationValue == null) {
            throw new IllegalArgumentException("Incorrect notification: " + data);
        }

        DataValue record = notificationValue.get("record");
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
            .additionalMeta(
                getAdditionalMeta(notificationValue.get("additionalMeta").asMap(String.class, Object.class))
            ).build();

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
            .additionalMeta(getAdditionalMeta(additionalMeta))
            .build();

        notificationService.send(notification);
    }

    private Map<String, Object> getAdditionalMeta(Map<String, Object> baseMeta) {

        Map<String, Object> result = new HashMap<>();

        result.put("webUrl", urlUtils.getWebUrl());
        result.put("shareUrl", urlUtils.getShareUrl());

        if (baseMeta != null) {
            result.putAll(baseMeta);
        }

        return result;
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
