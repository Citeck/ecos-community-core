package ru.citeck.ecos.eureka;

import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.EurekaInstanceConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import ru.citeck.ecos.utils.InetUtils;
import ru.citeck.ecos.webapp.api.properties.EcosWebAppProperties;
import ru.citeck.ecos.webapp.lib.utils.EcosEnvUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Slf4j
public class EurekaAlfInstanceConfig extends AbstractEurekaConfig implements EurekaInstanceConfig {

    private static final String ENV_PROP_PORT = "ECOS_EUREKA_INSTANCE_PORT";
    private static final String ENV_PROP_IP = "ECOS_EUREKA_INSTANCE_IP";
    private static final String ENV_PROP_HOST = "ECOS_EUREKA_INSTANCE_HOST";

    private static final String ENV_RECORDS_URL = "ECOS_EUREKA_RECORDS_URL";
    private static final String ENV_RECORDS_USER_URL = "ECOS_EUREKA_USER_RECORDS_URL";

    private static final DataCenterInfo DATA_CENTER_INFO = () -> DataCenterInfo.Name.MyOwn;

    private static final String HEALTH_URL = "/alfresco/service/citeck/ecos/eureka-status";

    private final InetUtils.HostInfo hostInfo;
    private final EcosWebAppProperties webAppProperties;

    public EurekaAlfInstanceConfig(Properties globalProperties,
                                   InetUtils inetUtils,
                                   EcosWebAppProperties webAppProperties) {
        super(globalProperties);
        hostInfo = inetUtils.findFirstNonLoopbackHostInfo();
        this.webAppProperties = webAppProperties;
    }

    @Override
    public String getInstanceId() {
        return getAppname() + ":" + webAppProperties.getAppInstanceId();
    }

    @Override
    public String getAppname() {
        return webAppProperties.getAppName();
    }

    @Override
    public String getAppGroupName() {
        return "alfresco";
    }

    @Override
    public boolean isInstanceEnabledOnit() {
        return getBoolParam("traffic.enabled", () -> false);
    }

    @Override
    public int getNonSecurePort() {
        String portFromEnv = System.getenv(ENV_PROP_PORT);
        if (portFromEnv != null) {
            try {
                return Integer.parseInt(portFromEnv);
            } catch (NumberFormatException e) {
                log.warn("Incorrect port in " + ENV_PROP_PORT + " param. Value: " + portFromEnv);
            }
        }
        return getIntParam("port", () -> getGlobalIntParam("alfresco.port", () -> 8080));
    }

    @Override
    public int getSecurePort() {
        return getIntParam("securePort", () -> 8443);
    }

    @Override
    public boolean isNonSecurePortEnabled() {
        return getBoolParam("port.enabled", () -> true);
    }

    @Override
    public boolean getSecurePortEnabled() {
        return getBoolParam("securePort.enabled", () -> false);
    }

    @Override
    public int getLeaseRenewalIntervalInSeconds() {
        return 30;
    }

    @Override
    public int getLeaseExpirationDurationInSeconds() {
        return 90;
    }

    @Override
    public String getVirtualHostName() {
        return getAppname();
    }

    @Override
    public String getSecureVirtualHostName() {
        return getAppname();
    }

    @Override
    public String getASGName() {
        return null;
    }

    @Override
    public String getHostName(boolean refresh) {
        String host = System.getenv(ENV_PROP_HOST);
        if (StringUtils.isBlank(host)) {
            host = getStrParam("host", () -> getGlobalStrParam("alfresco.host", () -> "localhost"));
            if ("localhost".equals(host) || "127.0.0.1".equals(host)) {
                host = getIpAddress();
            }
        }
        return host;
    }

    @Override
    public Map<String, String> getMetadataMap() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("zone", "primary");

        String recordsUrl = System.getenv(ENV_RECORDS_URL);
        if (StringUtils.isNotBlank(recordsUrl)) {
            metadata.put("records-base-url", recordsUrl);
        } else {
            metadata.put("records-base-url", "/alfresco/s/citeck/ecos/records/");
        }

        String userUrl = System.getenv(ENV_RECORDS_USER_URL);
        if (StringUtils.isNotBlank(userUrl)) {
            metadata.put("records-user-base-url", userUrl);
        } else {
            metadata.put("records-user-base-url", "/alfresco/s/citeck/ecos/records/");
        }

        return metadata;
    }

    @Override
    public DataCenterInfo getDataCenterInfo() {
        return DATA_CENTER_INFO;
    }

    @Override
    public String getIpAddress() {
        String envValue = System.getenv(ENV_PROP_IP);
        if (StringUtils.isNotEmpty(envValue)) {
            return envValue;
        }

        return getStrParam("instance.ip", () -> {

            Boolean isDev = getGlobalBoolParam("ecos.environment.dev", () -> false);
            if (isDev) {
                if (EcosEnvUtils.isOsMac() || EcosEnvUtils.isOsWindows()) {
                    return "host.docker.internal";
                }
            }

            return hostInfo.getIpAddress();
        });
    }

    @Override
    public String getStatusPageUrlPath() {
        return HEALTH_URL;
    }

    @Override
    public String getStatusPageUrl() {
        return HEALTH_URL;
    }

    @Override
    public String getHomePageUrlPath() {
        return "/alfresco/";
    }

    @Override
    public String getHomePageUrl() {
        return "/alfresco/";
    }

    @Override
    public String getHealthCheckUrlPath() {
        return HEALTH_URL;
    }

    @Override
    public String getHealthCheckUrl() {
        return HEALTH_URL;
    }

    @Override
    public String getSecureHealthCheckUrl() {
        return HEALTH_URL;
    }

    @Override
    public String[] getDefaultAddressResolutionOrder() {
        String result = getStrParam("defaultAddressResolutionOrder", () -> null);
        return result == null ? new String[0] : result.split(",");
    }

    @Override
    public String getNamespace() {
        return "alfresco";
    }
}
