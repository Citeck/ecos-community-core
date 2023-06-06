package ru.citeck.ecos.webapp.web.client;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.eureka.EcosEurekaClient;
import ru.citeck.ecos.webapp.api.apps.EcosRemoteWebAppsApi;

@Component
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class EcosRemoteWebAppsApiImpl implements EcosRemoteWebAppsApi {

    private final EcosEurekaClient eurekaClient;

    @Override
    public boolean isAppAvailable(@NotNull String appName) {
        try {
            return eurekaClient.getInstanceInfo(appName) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
