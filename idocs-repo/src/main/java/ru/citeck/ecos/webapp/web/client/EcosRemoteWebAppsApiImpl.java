package ru.citeck.ecos.webapp.web.client;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.eureka.EcosAlfServiceDiscovery;
import ru.citeck.ecos.webapp.api.apps.EcosRemoteWebAppsApi;

@Component
public class EcosRemoteWebAppsApiImpl implements EcosRemoteWebAppsApi {

    private EcosAlfServiceDiscovery ecosAlfServiceDiscovery;

    @Override
    public boolean isAppAvailable(@NotNull String appName) {
        if (ecosAlfServiceDiscovery == null) {
            return false;
        }
        try {
            return ecosAlfServiceDiscovery.getInstanceInfo(appName) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public void setEcosAlfServiceDiscovery(EcosAlfServiceDiscovery ecosAlfServiceDiscovery) {
        this.ecosAlfServiceDiscovery = ecosAlfServiceDiscovery;
    }
}
