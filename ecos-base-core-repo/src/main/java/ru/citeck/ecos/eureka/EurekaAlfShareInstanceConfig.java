package ru.citeck.ecos.eureka;

import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.EurekaInstanceConfig;
import org.alfresco.util.GUID;
import org.apache.commons.lang.StringUtils;
import ru.citeck.ecos.utils.InetUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class EurekaAlfShareInstanceConfig extends EurekaAlfInstanceConfig implements EurekaInstanceConfig {

    private InetUtils.HostInfo hostInfo;

    public EurekaAlfShareInstanceConfig(Properties globalProperties, InetUtils inetUtils) {
        super(globalProperties, inetUtils);
        try {
            String host = getGlobalStrParam("share.host", () -> "localhost");
            InetAddress inetAddress = InetAddress.getByName(host);
            hostInfo = inetUtils.convertAddress(inetAddress);
        } catch (final UnknownHostException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public String getAppname() {
        //todo
//        return getStrParam("instance.appname", () -> "alfshare");
        return "alfshare";
    }

    //todo
    @Override
    public String getAppGroupName() {
        return "alfshare";
    }

    //todo
    @Override
    public int getNonSecurePort() {
        return getGlobalIntParam("share.port", () -> 8080);
    }

    //todo
    @Override
    public String getHostName(boolean refresh) {
        String host = getGlobalStrParam("share.host", () -> "localhost");
        if ("localhost".equals(host) || "127.0.0.1".equals(host)) {
            host = getIpAddress();
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
        Boolean isDev = getGlobalBoolParam("ecos.environment.dev", () -> false);
        if (isDev) {
            String osName = StringUtils.defaultString(System.getProperty("os.name"));
            if (osName.contains("Windows")) {
                return "host.docker.internal";
            }
        }

        return hostInfo.getIpAddress();
    }

    //todo
    @Override
    public String getHomePageUrlPath() {
        return "/alfshare/";
    }

    //todo
    @Override
    public String getHomePageUrl() {
        return "/alfshare/";
    }

    //todo what is it?
    @Override
    public String getNamespace() {
        return getAppname();
    }
}
