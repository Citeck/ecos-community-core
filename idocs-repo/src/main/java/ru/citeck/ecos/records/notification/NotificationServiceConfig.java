package ru.citeck.ecos.records.notification;

import org.apache.commons.lang3.LocaleUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.commands.CommandsService;
import ru.citeck.ecos.notifications.lib.service.NotificationService;
import ru.citeck.ecos.notifications.lib.service.NotificationServiceImpl;
import ru.citeck.ecos.notifications.lib.service.NotificationTemplateService;
import ru.citeck.ecos.records3.RecordsService;
import ru.citeck.ecos.records3.RecordsServiceFactory;

@Configuration
public class NotificationServiceConfig {

    @Value("${notifications.default.locale}")
    private String defaultAppNotificationLocale;

    @Value("${notifications.default.from}")
    private String defaultAppNotificationFrom;

    @Bean
    public NotificationService ecosNotificationService(CommandsService commandsService,
                                                       RecordsServiceFactory recordsServiceFactory,
                                                       NotificationTemplateService notificationTemplateService) {
        NotificationServiceImpl service = new NotificationServiceImpl(
            commandsService,
            recordsServiceFactory,
            notificationTemplateService);

        service.setDefaultLocale(LocaleUtils.toLocale(defaultAppNotificationLocale));
        service.setDefaultFrom(defaultAppNotificationFrom);
        return service;
    }

    @Bean
    public NotificationTemplateService notificationTemplateService(RecordsService recordsService) {
        return new NotificationTemplateService(recordsService);
    }

}
