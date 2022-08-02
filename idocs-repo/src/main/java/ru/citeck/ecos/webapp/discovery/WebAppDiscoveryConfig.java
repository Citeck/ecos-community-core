package ru.citeck.ecos.webapp.discovery;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.commons.data.Version;
import ru.citeck.ecos.eureka.EurekaAlfInstanceConfig;
import ru.citeck.ecos.webapp.api.context.EcosWebAppContext;
import ru.citeck.ecos.webapp.lib.discovery.WebAppDiscoveryService;
import ru.citeck.ecos.webapp.lib.discovery.WebAppMainInfo;
import ru.citeck.ecos.webapp.lib.discovery.zookeeper.WebAppZkDiscoveryService;
import ru.citeck.ecos.webapp.lib.env.EcosWebAppEnvironment;
import ru.citeck.ecos.zookeeper.EcosZooKeeper;

import java.time.Instant;

@Configuration
public class WebAppDiscoveryConfig {

    @Autowired
    private EurekaAlfInstanceConfig eurekaInstanceConfig;

    @Bean
    public WebAppDiscoveryService createDiscoveryService(
        EcosZooKeeper zookeeper,
        EcosWebAppContext webAppContext,
        EcosWebAppEnvironment env
    ) {

        long priority = 0L;
        if (env.acceptsProfiles("dev_local")) {
            priority = 1L;
        }

        String ipAddress = "127.0.0.1";
        int port = 8080;
        if (eurekaInstanceConfig != null) {
            String cfgIp = eurekaInstanceConfig.getIpAddress();
            if (StringUtils.isNotBlank(cfgIp)) {
                ipAddress = cfgIp;
            }
            port = eurekaInstanceConfig.getNonSecurePort();
        }

        Version version = Version.valueOf("1");

        return new WebAppZkDiscoveryService(
            zookeeper, webAppContext,
            new WebAppMainInfo(
                priority,
                version,
                Instant.now(),
                ipAddress,
                port,
                ipAddress,
                "/alfresco/s/citeck/ecos/webapi"
            )
        );
    }

    @Autowired(required = false)
    public void setEurekaInstanceConfig(EurekaAlfInstanceConfig eurekaInstanceConfig) {
        this.eurekaInstanceConfig = eurekaInstanceConfig;
    }
}
