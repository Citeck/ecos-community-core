package ru.citeck.ecos.eureka;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import ru.citeck.ecos.utils.InetUtils;
import ru.citeck.ecos.webapp.api.properties.EcosWebAppProperties;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Slf4j
public class EurekaAlfShareInstanceConfig extends EurekaAlfInstanceConfig {

    public static final String APP_NAME_ALFSHARE = "alfshare";
    private static final String HOME_PAGE_URL = "/alfshare/";

    private static final String ENV_PROP_PORT = "ECOS_EUREKA_INSTANCE_SHARE_PORT";
    private static final String ENV_PROP_IP = "ECOS_EUREKA_INSTANCE_SHARE_IP";
    private static final String ENV_PROP_HOST = "ECOS_EUREKA_INSTANCE_SHARE_HOST";

    private final EcosWebAppProperties webAppProperties;

    public EurekaAlfShareInstanceConfig(Properties globalProperties,
                                        InetUtils inetUtils,
                                        EcosWebAppProperties webAppProperties) {
        super(globalProperties, inetUtils, webAppProperties);
        this.webAppProperties = webAppProperties;
    }

    @Override
    public String getInstanceId() {
        return getAppname() + ":" + webAppProperties.getAppInstanceId();
    }

    @Override
    public String getAppname() {
        return APP_NAME_ALFSHARE;
    }

    @Override
    public String getAppGroupName() {
        return getAppname();
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
        return super.getNonSecurePort();
    }

    @Override
    public String getHostName(boolean refresh) {
        String host = System.getenv(ENV_PROP_HOST);
        if (StringUtils.isBlank(host)) {
            host = super.getHostName(refresh);
        }
        return host;
    }

    @Override
    public Map<String, String> getMetadataMap() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("zone", "primary");

        return metadata;
    }

    @Override
    public String getIpAddress() {
        String envValue = System.getenv(ENV_PROP_IP);
        if (StringUtils.isNotEmpty(envValue)) {
            return envValue;
        }
        return super.getIpAddress();
    }

    @Override
    public String getHomePageUrlPath() {
        return HOME_PAGE_URL;
    }

    @Override
    public String getHomePageUrl() {
        return HOME_PAGE_URL;
    }

    @Override
    public String getNamespace() {
        return getAppname();
    }
}
