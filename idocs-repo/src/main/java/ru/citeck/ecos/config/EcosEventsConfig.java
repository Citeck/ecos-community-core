package ru.citeck.ecos.config;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.eureka.EurekaAlfInstanceConfig;
import ru.citeck.ecos.events2.EventProperties;
import ru.citeck.ecos.events2.EventService;
import ru.citeck.ecos.events2.EventServiceFactory;
import ru.citeck.ecos.events2.rabbitmq.RabbitMqEvents;
import ru.citeck.ecos.events2.remote.RemoteEvents;
import ru.citeck.ecos.rabbitmq.RabbitMqConn;
import ru.citeck.ecos.rabbitmq.RabbitMqConnProvider;
import ru.citeck.ecos.records3.RecordsServiceFactory;
import ru.citeck.ecos.zookeeper.EcosZooKeeper;

import javax.annotation.PostConstruct;

@Slf4j
@Configuration
public class EcosEventsConfig extends EventServiceFactory {

    @Autowired
    private EcosZooKeeper ecosZooKeeper;

    @Autowired
    private RabbitMqConnProvider rabbitMqConnProvider;

    @Autowired
    private EurekaAlfInstanceConfig instanceConfig;

    @Autowired
    private RecordsServiceFactory recordsServiceFactory;

    @PostConstruct
    public void init() {
        setRecordsServiceFactory(recordsServiceFactory);
    }

    @Bean
    @NotNull
    @Override
    public EventService createEventService() {
        log.info("EventService init");
        return super.createEventService();
    }

    @NotNull
    @Override
    public EventProperties createProperties() {

        EventProperties props = new EventProperties();

        props.setAppInstanceId(instanceConfig.getInstanceId());
        props.setAppName(instanceConfig.getAppname());

        return props;
    }

    @Nullable
    @Override
    public RemoteEvents createRemoteEvents() {
        RabbitMqConn connection = rabbitMqConnProvider.getConnection();
        if (connection == null) {
            throw new IllegalStateException("RabbitMQ connection is null");
        }
        return new RabbitMqEvents(connection, this, ecosZooKeeper);
    }
}
