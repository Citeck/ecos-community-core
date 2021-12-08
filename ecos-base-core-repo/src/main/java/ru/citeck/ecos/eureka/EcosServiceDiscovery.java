package ru.citeck.ecos.eureka;

import com.netflix.appinfo.InstanceInfo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EcosServiceDiscovery {

    private static final String APP_INFO_PREFIX = "ecos.service.discovery.";
    private static final String APP_INFO_PORT = APP_INFO_PREFIX + "%s.port";
    private static final String APP_INFO_HOST = APP_INFO_PREFIX + "%s.host";
    private static final String APP_INFO_IP = APP_INFO_PREFIX + "%s.ip";

    private final EcosEurekaClient eurekaClient;
    private final Properties globalProps;

    private EcosServiceInstanceInfo infoForAll;

    private final Map<String, EcosServiceInstanceInfo> infoFromConfig = new ConcurrentHashMap<>();

    @Autowired
    public EcosServiceDiscovery(EcosEurekaClient client,
                                @Qualifier("global-properties") Properties globalProps) {

        this.eurekaClient = client;
        this.globalProps = globalProps;
    }

    @PostConstruct
    public void init() {
        infoForAll = getInfoFromParams("all");
    }

    public EcosServiceInstanceInfo getInstanceInfo(String appName) {
        return getInfoFromEureka(appName)
            .apply(infoForAll)
            .apply(infoFromConfig.computeIfAbsent(appName, this::getInfoFromParams));
    }

    private EcosServiceInstanceInfo getInfoFromEureka(String appName) {

        InstanceInfo eurekaInstanceInfo;
        try {
            eurekaInstanceInfo = eurekaClient.getInstanceInfo(appName);
        } catch (Exception e) {
            eurekaInstanceInfo = null;
        }

        if (eurekaInstanceInfo == null) {
            return new EcosServiceInstanceInfo(null, null, null, null, null);
        }

        return new EcosServiceInstanceInfo(
            eurekaInstanceInfo.getHostName(),
            eurekaInstanceInfo.getIPAddr(),
            eurekaInstanceInfo.getPort(),
            eurekaInstanceInfo.getMetadata(),
            eurekaInstanceInfo.isPortEnabled(InstanceInfo.PortType.SECURE)
        );
    }

    private EcosServiceInstanceInfo getInfoFromParams(String appName) {

        String ip = getStrParam(String.format(APP_INFO_IP, appName));
        String host = getStrParam(String.format(APP_INFO_HOST, appName));
        Integer port = getIntParam(String.format(APP_INFO_PORT, appName));

        return new EcosServiceInstanceInfo(host, ip, port, null, null);
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
}
