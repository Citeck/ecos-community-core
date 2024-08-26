package ru.citeck.ecos.webapp.discovery;

import kotlin.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import ru.citeck.ecos.webapp.api.EcosWebAppApi;
import ru.citeck.ecos.webapp.lib.discovery.WebAppDiscoveryService;
import ru.citeck.ecos.webapp.lib.discovery.WebAppMainInfo;
import ru.citeck.ecos.webapp.lib.discovery.instance.AppInstanceRef;
import ru.citeck.ecos.webapp.lib.discovery.zookeeper.WebAppZkDiscoveryService;
import ru.citeck.ecos.zookeeper.EcosZooKeeper;

@Configuration
public class WebAppDiscoveryConfig {

    private EcosWebAppApi webAppApi;

    @Bean
    public WebAppDiscoveryService createDiscoveryService(
        EcosZooKeeper zookeeper,
        DiscoveryInfoProvider infoProvider
    ) {
        WebAppZkDiscoveryService service = new WebAppZkDiscoveryService(
            zookeeper,
            webAppApi,
            infoProvider.getAlfrescoInfo(),
            infoProvider.isRegistrationEnabled()
        );
        Pair<AppInstanceRef, WebAppMainInfo> shareInfo = infoProvider.getShareInfo();
        service.register(shareInfo.getFirst(), shareInfo.getSecond());

        return service;
    }

    @Autowired
    public void setWebAppApi(EcosWebAppApi webAppApi) {
        this.webAppApi = webAppApi;
    }
}
