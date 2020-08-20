package ru.citeck.ecos.records.notification;

import org.apache.commons.lang3.LocaleUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.commands.CommandsService;
import ru.citeck.ecos.notifications.lib.dto.TemplateMultiModelAttributesDto;
import ru.citeck.ecos.notifications.lib.service.NotificationService;
import ru.citeck.ecos.notifications.lib.service.NotificationTemplateService;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records2.meta.RecordsMetaService;
import ru.citeck.ecos.records2.source.dao.local.RemoteSyncRecordsDao;

@Configuration
public class NotificationServiceConfig {

    @Value("${notifications.default.locale}")
    private String defaultAppNotificationLocale;

    @Value("${notifications.default.from}")
    private String defaultAppNotificationFrom;

    @Bean
    public NotificationService ecosNotificationService(CommandsService commandsService,
                                                       RecordsService recordsService,
                                                       RecordsMetaService recordsMetaService,
                                                       NotificationTemplateService notificationTemplateService) {
        NotificationService service = new NotificationService(commandsService, recordsService, recordsMetaService,
            notificationTemplateService);
        service.setDefaultLocale(LocaleUtils.toLocale(defaultAppNotificationLocale));
        service.setDefaultFrom(defaultAppNotificationFrom);
        return service;
    }

    @Bean
    public NotificationTemplateService notificationTemplateService(
        @Qualifier("remoteSyncTemplateModelRecordsDao") RemoteSyncRecordsDao<TemplateMultiModelAttributesDto> syncRecordsDao
    ) {
        return new NotificationTemplateService(syncRecordsDao);
    }

    @Bean()
    public RemoteSyncRecordsDao<TemplateMultiModelAttributesDto> remoteSyncTemplateModelRecordsDao() {
        return new RemoteSyncRecordsDao<>("notifications/template", TemplateMultiModelAttributesDto.class);
    }

}
