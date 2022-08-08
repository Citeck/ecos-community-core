package ru.citeck.ecos.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.zookeeper.EcosZooKeeper;
import ru.citeck.ecos.zookeeper.EcosZooKeeperProperties;

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

    @Bean
    public EcosZooKeeper ecosZookeeper() {
        EcosZooKeeperProperties props = new EcosZooKeeperProperties(host, port);
        return new EcosZooKeeper(props).withNamespace(namespace);
    }

    @PreDestroy
    public void destroy() {
        log.info("Destroy...");
        ecosZookeeper().dispose();
    }
}
