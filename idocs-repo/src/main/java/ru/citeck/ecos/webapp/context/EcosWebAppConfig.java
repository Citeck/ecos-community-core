package ru.citeck.ecos.webapp.context;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import ru.citeck.ecos.audit.lib.EcosAuditProperties;
import ru.citeck.ecos.audit.lib.EcosAuditService;
import ru.citeck.ecos.audit.lib.EcosAuditServiceImpl;
import ru.citeck.ecos.audit.lib.output.EcosAuditOutputsServiceImpl;
import ru.citeck.ecos.audit.lib.processor.EcosAuditProcessorsServiceImpl;
import ru.citeck.ecos.webapp.api.properties.EcosWebAppProps;
import ru.citeck.ecos.webapp.lib.lock.EcosAppLockService;
import ru.citeck.ecos.zookeeper.EcosZooKeeper;
import ru.citeck.ecos.zookeeper.lock.EcosZkLockService;

@Configuration
public class EcosWebAppConfig {

    @Autowired
    private EcosWebAppProps props;
    @Autowired
    private EcosAuditProperties auditProps;
    @Autowired
    private EcosZooKeeper ecosZooKeeper;

    @Bean
    @Primary
    public EcosAppLockService appLockService() {
        return new EcosAppLockService(new EcosZkLockService("app-" + props.getAppName(), ecosZooKeeper));
    }

    @Bean
    public EcosAuditService ecosAuditService() {
        return new EcosAuditServiceImpl(auditProps,
            new EcosAuditOutputsServiceImpl(),
            new EcosAuditProcessorsServiceImpl()
        );
    }
}
