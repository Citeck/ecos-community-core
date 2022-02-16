package ru.citeck.ecos.config;

import ecos.org.apache.curator.RetryPolicy;
import ecos.org.apache.curator.framework.CuratorFramework;
import ecos.org.apache.curator.framework.CuratorFrameworkFactory;
import ecos.org.apache.curator.retry.ExponentialBackoffRetry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.zookeeper.EcosZooKeeper;

import javax.annotation.PreDestroy;

@Slf4j
@Configuration
public class ZookeeperConfig {

    @Value("${ecos.zookeeper.host}")
    private String host;

    @Value("${ecos.zookeeper.port}")
    private int port;

    @Value("${ecos.zookeeper.namespace}")
    private String namespace;

    @Value("${ecos.zookeeper.curator.retry-policy.base-sleep}")
    private int baseSleepTime;

    @Value("${ecos.zookeeper.curator.retry-policy.max-retries}")
    private int maxRetries;

    private CuratorFramework client;

    @Bean
    public EcosZooKeeper ecosZookeeper(CuratorFramework curatorFramework) {
        return new EcosZooKeeper(curatorFramework).withNamespace(namespace);
    }

    @Bean
    public CuratorFramework curatorFramework() {
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(baseSleepTime, maxRetries);
        String connectString = host + ":" + port;

        log.info("================Ecos Zookeeper Init======================");
        log.info("Connect to Zookeeper with params");
        log.info("URL: " + connectString);
        log.info("namespace: " + namespace);
        log.info("baseSleepTime: " + baseSleepTime);
        log.info("maxRetries: " + maxRetries);
        log.info("Startup will be stopped until Zookeeper will be available");
        log.info("=========================================================");

        client = CuratorFrameworkFactory.newClient(connectString, retryPolicy);
        client.start();

        return client;
    }

    @PreDestroy
    public void destroy() {
        log.info("Destroy...");
        client.close();
    }
}
