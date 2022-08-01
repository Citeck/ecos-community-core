package ru.citeck.ecos.webapp.context;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import ru.citeck.ecos.webapp.api.audit.EcosAuditService;
import ru.citeck.ecos.webapp.api.lock.EcosLockService;
import ru.citeck.ecos.webapp.api.properties.EcosWebAppProperties;
import ru.citeck.ecos.webapp.api.task.executor.EcosTaskExecutor;
import ru.citeck.ecos.webapp.api.task.scheduler.EcosTaskScheduler;
import ru.citeck.ecos.webapp.lib.audit.SimpleEcosAuditService;
import ru.citeck.ecos.zookeeper.EcosZooKeeper;
import ru.citeck.ecos.zookeeper.lock.EcosZkLockService;

@Configuration
public class EcosWebAppConfig {

    @Autowired
    private EcosWebAppProperties props;
    @Autowired
    private EcosZooKeeper ecosZooKeeper;

    @Bean
    @Primary
    public EcosLockService appLockService() {
        return new EcosZkLockService("app-" + props.getAppName(), ecosZooKeeper);
    }

    @Bean
    public EcosAuditService ecosAuditService() {
        return new SimpleEcosAuditService();
    }
}
