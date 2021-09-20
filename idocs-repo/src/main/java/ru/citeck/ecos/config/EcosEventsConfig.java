package ru.citeck.ecos.config;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.events2.EventsService;
import ru.citeck.ecos.events2.EventsServiceFactory;
import ru.citeck.ecos.events2.rabbitmq.RabbitMqEventsService;
import ru.citeck.ecos.events2.remote.RemoteEventsService;
import ru.citeck.ecos.rabbitmq.RabbitMqConn;
import ru.citeck.ecos.rabbitmq.RabbitMqConnProvider;
import ru.citeck.ecos.records3.RecordsServiceFactory;
import ru.citeck.ecos.zookeeper.EcosZooKeeper;

import javax.annotation.PostConstruct;

@Slf4j
@Configuration
public class EcosEventsConfig extends EventsServiceFactory {

    @Autowired
    private EcosZooKeeper ecosZooKeeper;

    @Autowired
    private RabbitMqConnProvider rabbitMqConnProvider;

    @PostConstruct
    public void init() {
        super.init();
    }

    @Bean
    @NotNull
    @Override
    public EventsService createEventsService() {
        log.info("EventService init");
        return super.createEventsService();
    }

    @Nullable
    @Override
    public RemoteEventsService createRemoteEvents() {
        RabbitMqConn connection = rabbitMqConnProvider.getConnection();
        if (connection == null) {
            throw new IllegalStateException("RabbitMQ connection is null");
        }
        return new RabbitMqEventsService(connection, this, ecosZooKeeper);
    }

    @Autowired
    public void setRecordsServiceFactory(RecordsServiceFactory recordsServiceFactory) {
        super.recordsServices = recordsServiceFactory;
    }
}
