package ru.citeck.ecos.records.notification;

import ru.citeck.ecos.notifications.lib.Notification;
import ru.citeck.ecos.notifications.lib.NotificationType;
import ru.citeck.ecos.notifications.lib.service.NotificationService;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.utils.AlfrescoScopableProcessorExtension;

import java.util.List;
import java.util.Map;

public class NotificationCommandServiceJS extends AlfrescoScopableProcessorExtension {

    private NotificationService notificationService;

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
}
