package ru.citeck.ecos.webapp.discovery;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import ru.citeck.ecos.commons.data.Version;
import ru.citeck.ecos.eureka.EurekaAlfInstanceConfig;
import ru.citeck.ecos.webapp.api.EcosWebAppApi;
import ru.citeck.ecos.webapp.lib.discovery.WebAppDiscoveryService;
import ru.citeck.ecos.webapp.lib.discovery.WebAppMainInfo;
import ru.citeck.ecos.webapp.lib.discovery.instance.PortInfo;
import ru.citeck.ecos.webapp.lib.discovery.instance.PortType;
import ru.citeck.ecos.webapp.lib.discovery.zookeeper.WebAppZkDiscoveryService;
import ru.citeck.ecos.webapp.lib.env.EcosWebAppEnvironment;
import ru.citeck.ecos.zookeeper.EcosZooKeeper;

import java.time.Instant;
import java.util.Collections;

@Configuration
public class WebAppDiscoveryConfig {

    @Autowired
    private EurekaAlfInstanceConfig eurekaInstanceConfig;

    private EcosWebAppApi webAppApi;

    @Bean
    public WebAppDiscoveryService createDiscoveryService(
        EcosZooKeeper zookeeper,
        EcosWebAppEnvironment env
    ) {

        long priority = 0L;
        if (env.acceptsProfiles("dev_local")) {
            priority = 1L;
        }

        String ipAddress = "127.0.0.1";
        int port = 8080;
        PortType portType = PortType.HTTP;
        if (eurekaInstanceConfig != null) {
            String cfgIp = eurekaInstanceConfig.getIpAddress();
            if (StringUtils.isNotBlank(cfgIp)) {
                ipAddress = cfgIp;
            }
            port = eurekaInstanceConfig.getNonSecurePort();
        }

        Version version = Version.valueOf("1");

        return new WebAppZkDiscoveryService(
            zookeeper, webAppApi,
            new WebAppMainInfo(
                priority,
                version,
                Instant.now(),
                ipAddress,
                Collections.singletonList(new PortInfo(port, portType)),
                ipAddress
            )
        );
    }

    @Autowired(required = false)
    public void setEurekaInstanceConfig(EurekaAlfInstanceConfig eurekaInstanceConfig) {
        this.eurekaInstanceConfig = eurekaInstanceConfig;
    }

    @Autowired
    public void setWebAppApi(EcosWebAppApi webAppApi) {
        this.webAppApi = webAppApi;
    }
}
