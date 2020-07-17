package ru.citeck.ecos.records.notification.command;

import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.utils.AlfrescoScopableProcessorExtension;

import java.util.List;

public class NotificationCommandServiceJS extends AlfrescoScopableProcessorExtension {

    private NotificationCommandService notificationCommandService;

    public void send(String templateRef, String type, List<String> recipients) {
        notificationCommandService.send(RecordRef.valueOf(templateRef), NotificationType.valueOf(type), recipients);
    }

    public void setNotificationCommandService(ru.citeck.ecos.records.notification.command.NotificationCommandService
                                                  notificationCommandService) {
        this.notificationCommandService = notificationCommandService;
    }
}
