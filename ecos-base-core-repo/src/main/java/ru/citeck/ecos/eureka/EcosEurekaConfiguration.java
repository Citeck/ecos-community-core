package ru.citeck.ecos.eureka;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.utils.InetUtils;
import ru.citeck.ecos.webapp.api.properties.EcosWebAppProperties;

import java.util.Properties;

@Configuration
public class EcosEurekaConfiguration {
    public static final String ALF_INSTANCE_CONFIG = "eurekaAlfInstanceConfig";
    public static final String SHARE_INSTANCE_CONFIG = "eurekaAlfShareInstanceConfig";

    @Autowired
    @Qualifier("global-properties")
    private Properties properties;

    @Autowired
    private InetUtils inetUtils;
    @Autowired
    private EcosWebAppProperties ecosWebAppProperties;

    @Bean(name = ALF_INSTANCE_CONFIG)
    public EurekaAlfInstanceConfig getEurekaInstanceConfig() {
        return new EurekaAlfInstanceConfig(properties, inetUtils, ecosWebAppProperties);
    }

    @Bean(name = SHARE_INSTANCE_CONFIG)
    public EurekaAlfShareInstanceConfig getEurekaShareInstanceConfig() {
        return new EurekaAlfShareInstanceConfig(properties, inetUtils, ecosWebAppProperties);
    }

    @Bean
    public EurekaAlfClientConfig getEurekaAlfClientConfig() {
        return new EurekaAlfClientConfig(properties);
    }
}
