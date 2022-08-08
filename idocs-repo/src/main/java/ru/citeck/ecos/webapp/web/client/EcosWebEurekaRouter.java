package ru.citeck.ecos.webapp.web.client;

import com.netflix.appinfo.InstanceInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.eureka.EcosEurekaClient;
import ru.citeck.ecos.webapp.lib.txn.TxnContext;
import ru.citeck.ecos.webapp.lib.web.client.router.EcosWebRoute;
import ru.citeck.ecos.webapp.lib.web.client.router.EcosWebRouter;

import javax.annotation.PostConstruct;
import java.util.Properties;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class EcosWebEurekaRouter implements EcosWebRouter {

    private final EcosEurekaClient eurekaClient;

    @Autowired
    @Qualifier("global-properties")
    private Properties properties;

    private Boolean isDevEnv = null;

    @PostConstruct
    public void init() {
    }

    @NotNull
    @Override
    public EcosWebRoute getRoute(@NotNull String appName) {
        EcosWebRoute route = TxnContext.getRoute(appName);
        if (route != null) {
            return route;
        }
        route = getRoute(eurekaClient.getInstanceInfo(appName));

        TxnContext.addRoute(appName, route);
        return route;
    }

    private EcosWebRoute getRoute(InstanceInfo instance) {
        String host;
        if (isDevEnv()) {
            host = "localhost";
        } else {
            host = instance.getIPAddr();
        }
        return new EcosWebRoute(host, instance.getPort(), instance.isPortEnabled(InstanceInfo.PortType.SECURE));
    }

    private boolean isDevEnv() {
        if (isDevEnv != null) {
            return isDevEnv;
        }
        String isDevEnv = properties.getProperty("ecos.environment.dev", "false");
        if (Boolean.TRUE.toString().equals(isDevEnv)) {
            log.info("DEV ENV enabled");
            this.isDevEnv = true;
            return true;
        }
        log.info("PROD ENV enabled");
        this.isDevEnv = false;
        return false;
    }
}
