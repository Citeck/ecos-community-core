package ru.citeck.ecos.eureka;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.webapp.lib.discovery.WebAppDiscoveryService;
import ru.citeck.ecos.webapp.lib.discovery.instance.AppInstanceInfo;
import ru.citeck.ecos.webapp.lib.discovery.instance.AppInstanceRef;
import ru.citeck.ecos.webapp.lib.discovery.instance.PortInfo;
import ru.citeck.ecos.webapp.lib.discovery.instance.PortType;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class EcosAlfServiceDiscovery {

    private static final String APP_INFO_PREFIX = "ecos.service.discovery.";
    private static final String APP_INFO_PORT = APP_INFO_PREFIX + "%s.port";
    private static final String APP_INFO_HOST = APP_INFO_PREFIX + "%s.host";
    private static final String APP_INFO_IP = APP_INFO_PREFIX + "%s.ip";

    private WebAppDiscoveryService webAppDiscoveryService;
    private final Properties globalProps;

    private EcosServiceInstanceInfo infoForAll;

    private final AtomicLong getInstanceCounter = new AtomicLong();
    private final Map<String, EcosServiceInstanceInfo> infoFromConfig = new ConcurrentHashMap<>();

    @Autowired
    public EcosAlfServiceDiscovery(@Qualifier("global-properties") Properties globalProps) {
        this.globalProps = globalProps;
    }

    @PostConstruct
    public void init() {
        infoForAll = getInfoFromParams("all");
    }

    public EcosServiceInstanceInfo getInstanceInfo(String appName) {
        String appNameWithoutInstance = appName;
        if (appName.contains(":")) {
            appNameWithoutInstance = appName.substring(0, appName.indexOf(':'));
        }
        return getInfoFromRegistry(appNameWithoutInstance)
            .apply(infoForAll)
            .apply(infoFromConfig.computeIfAbsent(appNameWithoutInstance, this::getInfoFromParams));
    }

    private EcosServiceInstanceInfo getInfoFromRegistry(String appName) {

        AppInstanceInfo instanceInfo = getInstanceInfoFromRegistry(appName);

        PortInfo port = instanceInfo.getPort(PortType.HTTPS, PortType.HTTP);
        if (port == null) {
            throw new RuntimeException("Application " + appName + " doesn't provide http(s) port");
        }
        return new EcosServiceInstanceInfo(
            instanceInfo.getRef().getName(),
            instanceInfo.getRef().getInstanceId(),
            instanceInfo.getHost(),
            instanceInfo.getIpAddress(),
            port.getValue(),
            Collections.emptyMap(),
            PortType.HTTPS.equals(port.getType())
        );
    }

    public AppInstanceInfo getInstanceInfoFromRegistry(String instanceName) {
        AppInstanceInfo info = null;
        if (instanceName.contains(AppInstanceRef.INSTANCE_ID_DELIM)) {
            info = webAppDiscoveryService.getInstance(AppInstanceRef.valueOf(instanceName));
        } else {
            List<AppInstanceInfo> instances = webAppDiscoveryService.getInstances(instanceName);
            if (!instances.isEmpty()) {
                info = instances.get((int)(getInstanceCounter.incrementAndGet() % instances.size()));
            }
        }
        if (info == null) {
            throw new RuntimeException("Application doesn't found: '" + instanceName + "'");
        }
        return info;
    }

    private EcosServiceInstanceInfo getInfoFromParams(String appName) {

        String ip = getStrParam(String.format(APP_INFO_IP, appName));
        String host = getStrParam(String.format(APP_INFO_HOST, appName));
        Integer port = getIntParam(String.format(APP_INFO_PORT, appName));

        return new EcosServiceInstanceInfo(appName, "", host, ip, port, null, null);
    }

    private String getStrParam(String key) {

        String envKey = key.replace("-", "")
            .replace('.', '_').toUpperCase();

        String value = System.getenv(envKey);

        if (StringUtils.isBlank(value)) {
            value = (String) globalProps.get(key);
        }

        return StringUtils.isBlank(value) ? null : value;
    }

    private Integer getIntParam(String key) {

        String strParam = getStrParam(key);

        if (StringUtils.isBlank(strParam)) {
            return null;
        }
        try {
            return Integer.parseInt(strParam);
        } catch (Exception e) {
            return null;
        }
    }

    @Autowired
    public void setWebAppDiscoveryService(WebAppDiscoveryService service) {
        this.webAppDiscoveryService = service;
    }
}
