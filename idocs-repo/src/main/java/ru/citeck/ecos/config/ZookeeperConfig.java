package ru.citeck.ecos.config;

import ecos.org.apache.curator.RetryPolicy;
import ecos.org.apache.curator.framework.CuratorFramework;
import ecos.org.apache.curator.framework.CuratorFrameworkFactory;
import ecos.org.apache.curator.retry.ExponentialBackoffRetry;
import ecos.org.apache.curator.retry.RetryForever;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.props.EcosPropertiesService;
import ru.citeck.ecos.zookeeper.EcosZooKeeper;

@Slf4j
@Configuration
public class ZookeeperConfig {

    public static final String PROP_HOST_KEY = "ecos.zookeeper.host";
    public static final String PROP_PORT_KEY = "ecos.zookeeper.port";
    public static final String PROP_USERNAME_KEY = "ecos.zookeeper.username";
    public static final String PROP_PASSWORD_KEY = "ecos.zookeeper.password";

    @Autowired
    private EcosPropertiesService properties;

    @Bean
    public EcosZooKeeper ecosZookeeper() {

        log.info("EcosZookeeper init");

        /*String host = properties.getProperty(PROP_HOST_KEY, String.class);
        if (StringUtils.isBlank(host)) {
            throw new IllegalStateException("'" + PROP_HOST_KEY + "' is undefined");
        }*/
        //Integer port = properties.getProperty(PROP_PORT_KEY, Integer.class, () -> 2181);

        RetryPolicy retryPolicy = new ExponentialBackoffRetry(5_000, 10);
        String connectString = "localhost" + ":" + "2181";

        log.info("=========================================================");
        log.info("Connect to Zookeeper with URL: " + connectString);
        log.info("Zookeeper is a mandatory dependency for Alfresco!");
        log.info("Startup will be stopped until Zookeeper will be available");
        log.info("=========================================================");

        CuratorFramework client = CuratorFrameworkFactory
            .newClient(connectString, retryPolicy);

        client.start();

        return new EcosZooKeeper(client).withNamespace("ecos");
    }
}
