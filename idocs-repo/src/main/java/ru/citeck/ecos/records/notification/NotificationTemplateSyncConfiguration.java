package ru.citeck.ecos.records.notification;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.records2.source.dao.local.RemoteSyncRecordsDao;

@Configuration
public class NotificationTemplateSyncConfiguration {

    @Bean(name = "remoteSyncTemplateModelRecordsDao")
    public RemoteSyncRecordsDao<TemplateModelDto> remoteSyncTemplateModelRecordsDao() {
        return new RemoteSyncRecordsDao<>("notifications/template", TemplateModelDto.class);
    }

}
