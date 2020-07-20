package ru.citeck.ecos.records.notification.command;

import org.apache.commons.lang3.LocaleUtils;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.utils.AlfrescoScopableProcessorExtension;

import java.util.List;

public class NotificationCommandServiceJS extends AlfrescoScopableProcessorExtension {

    private NotificationCommandService notificationCommandService;

    public void send(String record, String templateRef, String type, List<String> recipients, String lang) {
        notificationCommandService.send(RecordRef.valueOf(record), RecordRef.valueOf(templateRef),
            NotificationType.valueOf(type), recipients, LocaleUtils.toLocale(lang));
    }

    public void setNotificationCommandService(ru.citeck.ecos.records.notification.command.NotificationCommandService
                                                  notificationCommandService) {
        this.notificationCommandService = notificationCommandService;
    }
}
