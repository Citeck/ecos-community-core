package ru.citeck.ecos.eureka;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.DiscoveryManager;
import com.netflix.discovery.EurekaClient;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class EcosEurekaClient {

    private static final Long INFO_CACHE_AGE = TimeUnit.SECONDS.toMillis(30L);
    private static final String ERROR_MSG = "Cannot get an instance of '%s' service from eureka";

    private Map<String, ServerInfo> serversInfo = new ConcurrentHashMap<>();
    private InstanceInfo.InstanceStatus status = InstanceInfo.InstanceStatus.STARTING;

    @Autowired
    @Qualifier(EcosEurekaConfiguration.ALF_INSTANCE_CONFIG)
    private EurekaAlfInstanceConfig instanceConfig;
    @Autowired
    @Qualifier(EcosEurekaConfiguration.SHARE_INSTANCE_CONFIG)
    private EurekaAlfShareInstanceConfig shareInstanceConfig;
    @Autowired
    private EurekaAlfClientConfig clientConfig;

    @Getter(lazy = true) private final DiscoveryManager manager = initManager();
    @Getter(lazy = true) private final DiscoveryManager shareManager = initShareManager();
    @Getter(lazy = true) private final EurekaClient client = initClient();
    @Getter(lazy = true) private final EurekaClient shareClient = initShareClient();

    @PostConstruct
    public void init() {
        try {
            getClient();
            getShareClient();
        } catch (EurekaDisabled e) {
            log.info("Eureka disabled");
        } catch (Exception e) {
            log.error("Eureka client init failed", e);
        }
    }

    public InstanceInfo getInstanceInfo(String instanceName) {
        ServerInfo info = serversInfo.computeIfAbsent(instanceName, this::getServerInfo);
        if (System.currentTimeMillis() - info.resolvedTimeMs > INFO_CACHE_AGE) {
            serversInfo.remove(instanceName);
            return getInstanceInfo(instanceName);
        }
        return info.getInfo();
    }

    private ServerInfo getServerInfo(String serverName) {
        InstanceInfo info;
        try {
            info = getClient().getNextServerFromEureka(serverName, false);
            if (info == null) {
                info = getShareClient().getNextServerFromEureka(serverName, false);
            }
        } catch (Exception e) {
            throw new RuntimeException(String.format(ERROR_MSG, serverName), e);
        }
        if (info == null) {
            throw new RuntimeException(String.format(ERROR_MSG, serverName));
        }
        return new ServerInfo(info, System.currentTimeMillis());
    }

    private DiscoveryManager initManager() {
        return getDiscoveryManager(instanceConfig);
    }

    private DiscoveryManager getDiscoveryManager(EurekaInstanceConfig instanceConfig) {
        DiscoveryManager manager = DiscoveryManager.getInstance();

        if (clientConfig == null || !clientConfig.isEurekaEnabled()) {
            throw new EurekaDisabled();
        }

        if (!clientConfig.shouldRegisterWithEureka()) {
            log.info("===============================================");
            log.info("Eureka enabled, but instance won't be registered");
            log.info("===============================================");
        } else {
            log.info("===================================");
            log.info("Register in eureka with params:");
            log.info("Host: " + instanceConfig.getHostName(false) + ":" + instanceConfig.getNonSecurePort());
            log.info("IP:   " + instanceConfig.getIpAddress() + ":" + instanceConfig.getNonSecurePort());
            log.info("Application name: " + instanceConfig.getAppname());
            log.info("===================================");
        }

        ApplicationInfoManager.getInstance().initComponent(instanceConfig);
        manager.initComponent(instanceConfig, clientConfig);
        manager.getEurekaClient().registerHealthCheck(instanceStatus -> status);

        status = InstanceInfo.InstanceStatus.UP;

        return manager;
    }

    private DiscoveryManager initShareManager() {
        return getDiscoveryManager(shareInstanceConfig);
    }

    private EurekaClient initClient() {
        return getManager().getEurekaClient();
    }

    private EurekaClient initShareClient() {
        return getShareManager().getEurekaClient();
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private class ServerInfo {
        private InstanceInfo info;
        private Long resolvedTimeMs;
    }

    private static class EurekaDisabled extends RuntimeException {
    }
}
