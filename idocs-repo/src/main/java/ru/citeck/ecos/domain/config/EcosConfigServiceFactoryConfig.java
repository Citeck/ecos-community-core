package ru.citeck.ecos.domain.config;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.apps.app.service.LocalAppService;
import ru.citeck.ecos.config.lib.artifact.provider.ArtifactsConfigProvider;
import ru.citeck.ecos.config.lib.consumer.bean.BeanConsumerService;
import ru.citeck.ecos.config.lib.dto.EcosConfigProperties;
import ru.citeck.ecos.config.lib.provider.EcosConfigProvider;
import ru.citeck.ecos.config.lib.service.EcosConfigService;
import ru.citeck.ecos.config.lib.service.EcosConfigServiceFactory;
import ru.citeck.ecos.config.lib.zookeeper.ZkConfigProvider;
import ru.citeck.ecos.webapp.api.properties.EcosWebAppProps;
import ru.citeck.ecos.zookeeper.EcosZooKeeper;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;

@Configuration
public class EcosConfigServiceFactoryConfig extends EcosConfigServiceFactory {

    @Autowired
    private LocalAppService localAppService;
    @Autowired
    private EcosWebAppProps webAppProperties;
    @Autowired
    private EcosZooKeeper ecosZooKeeper;

    @PostConstruct
    public void init() {
        // initialize all providers
        getEcosConfigProviders();
    }

    @Bean
    @NotNull
    @Override
    protected BeanConsumerService createBeanConsumersService() {
        return super.createBeanConsumersService();
    }

    @NotNull
    @Override
    protected List<EcosConfigProvider> createEcosConfigProviders() {
        return Arrays.asList(
            new ZkConfigProvider(ecosZooKeeper),
            new ArtifactsConfigProvider(this, localAppService)
        );
    }

    @Bean
    @NotNull
    @Override
    protected EcosConfigService createEcosConfigService() {
        return super.createEcosConfigService();
    }

    @NotNull
    @Override
    protected EcosConfigProperties createProperties() {
        EcosConfigProperties props = new EcosConfigProperties();
        String appName = webAppProperties.getAppName();
        props.setDefaultScope("app/" + appName);
        return props;
    }
}
