package ru.citeck.ecos.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.rabbitmq.RabbitMqConn;
import ru.citeck.ecos.rabbitmq.RabbitMqConnFactory;
import ru.citeck.ecos.rabbitmq.RabbitMqConnProps;
import ru.citeck.ecos.rabbitmq.RabbitMqConnProvider;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Messaging configuration
 */
@Slf4j
@Configuration
public class MessagingConfiguration {

    /**
     * Properties constants
     */
    private static final String RABBIT_MQ_HOST = "rabbitmq.server.host";
    private static final String RABBIT_MQ_PORT= "rabbitmq.server.port";
    private static final String RABBIT_MQ_USERNAME= "rabbitmq.server.username";
    private static final String RABBIT_MQ_PASSWORD = "rabbitmq.server.password";

    /**
     * Global properties
     */
    @Autowired
    @Qualifier("global-properties")
    private Properties properties;

    private final List<Runnable> closeActions = new ArrayList<>();

    /**
     * Connection factory bean
     * @return Connection factory or null (in case of absence "rabbitmq.server.host" global property)
     */
    @Bean(name = "historyRabbitConnectionFactory")
    public CachingConnectionFactory historyRabbitConnectionFactory() {
        if (properties.getProperty(RABBIT_MQ_HOST) != null) {
            CachingConnectionFactory connectionFactory = new CachingConnectionFactory();
            connectionFactory.setHost(properties.getProperty(RABBIT_MQ_HOST));
            connectionFactory.setPort(Integer.parseInt(properties.getProperty(RABBIT_MQ_PORT)));
            connectionFactory.setUsername(properties.getProperty(RABBIT_MQ_USERNAME));
            connectionFactory.setPassword(properties.getProperty(RABBIT_MQ_PASSWORD));
            closeActions.add(connectionFactory::destroy);
            return connectionFactory;
        } else {
            return null;
        }
    }

    /**
     * Rabbit template bean
     * @param connectionFactory Connection factory
     * @return Rabbit template or null (in case of absence connection factory)
     */
    @Bean(name = "historyRabbitTemplate")
    public RabbitTemplate historyRabbitTemplate(@Qualifier("historyRabbitConnectionFactory")
                                                CachingConnectionFactory connectionFactory) {
        if (connectionFactory != null) {
            return new RabbitTemplate(connectionFactory);
        } else {
            return null;
        }
    }

    @Bean
    public RabbitMqConnProvider getProvider(RabbitMqConnProps mqProps) {
        return new Provider(mqProps);
    }

    @Bean
    public RabbitMqConnProps getConnProperties() {

        return new RabbitMqConnProps(
            properties.getProperty(RABBIT_MQ_HOST),
            properties.getProperty(RABBIT_MQ_USERNAME),
            properties.getProperty(RABBIT_MQ_PASSWORD),
            "/",
            "",
            Integer.parseInt(properties.getProperty(RABBIT_MQ_PORT)),
            null
        );
    }

    @PreDestroy
    public void close() {
        log.info("Closing...");
        closeActions.forEach(Runnable::run);
    }

    private static class Provider implements RabbitMqConnProvider {

        private final RabbitMqConn connection;

        public Provider(RabbitMqConnProps mqProps) {
            connection = new RabbitMqConnFactory().createConnection(mqProps);
        }

        @Nullable
        @Override
        public RabbitMqConn getConnection() {
            return connection;
        }
    }
}
