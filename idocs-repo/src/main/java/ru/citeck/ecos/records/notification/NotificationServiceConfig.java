package ru.citeck.ecos.records.notification;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.commands.CommandsService;
import ru.citeck.ecos.notifications.lib.NotificationsProperties;
import ru.citeck.ecos.notifications.lib.service.NotificationService;
import ru.citeck.ecos.notifications.lib.service.NotificationServiceImpl;
import ru.citeck.ecos.notifications.lib.service.NotificationTemplateService;
import ru.citeck.ecos.records3.RecordsService;
import ru.citeck.ecos.records3.RecordsServiceFactory;

@Configuration
public class NotificationServiceConfig {

    @Autowired
    private NotificationsProperties properties;

    @Bean
    public NotificationService ecosNotificationService(CommandsService commandsService,
                                                       RecordsServiceFactory recordsServiceFactory,
                                                       NotificationTemplateService notificationTemplateService) {

        return new NotificationServiceImpl(
            commandsService,
            recordsServiceFactory,
            notificationTemplateService,
            properties
        );
    }

    @Bean
    public NotificationTemplateService notificationTemplateService(RecordsService recordsService) {
        return new NotificationTemplateService(recordsService);
    }
}
