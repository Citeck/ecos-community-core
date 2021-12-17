package ru.citeck.ecos.config;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.core.JmsTemplate;
import ru.citeck.ecos.rabbitmq.RabbitMqConn;
import ru.citeck.ecos.rabbitmq.RabbitMqConnFactory;
import ru.citeck.ecos.rabbitmq.RabbitMqConnProps;
import ru.citeck.ecos.rabbitmq.RabbitMqConnProvider;

import java.util.Properties;

/**
 * Messaging configuration
 */
@Configuration
public class MessagingConfiguration {

    /**
     * Properties constants
     */
    private static final String RABBIT_MQ_HOST = "rabbitmq.server.host";
    private static final String RABBIT_MQ_PORT= "rabbitmq.server.port";
    private static final String RABBIT_MQ_USERNAME= "rabbitmq.server.username";
    private static final String RABBIT_MQ_PASSWORD = "rabbitmq.server.password";
    private static final String RABBIT_MQ_THREAD_POOL_SIZE = "rabbitmq.threadPoolSize";
    private static final String BROKER_URL = "messaging.broker.url";

    /**
     * Global properties
     */
    @Autowired
    @Qualifier("global-properties")
    private Properties properties;

    /**
     * ActiveMQ connection factory
     * @return ActiveMQ connection factory
     */
    @Bean(name = "activeMQConnectionFactory")
    public ActiveMQConnectionFactory activeMQConnectionFactory() {
        if (properties.getProperty(BROKER_URL) != null) {
            ActiveMQConnectionFactory activeMQConnectionFactory = new ActiveMQConnectionFactory();
            activeMQConnectionFactory.setBrokerURL(properties.getProperty(BROKER_URL));
            return activeMQConnectionFactory;
        } else {
            return null;
        }
    }

    /**
     * JMS template
     * @param factory Connection factory
     * @return JMS template
     */
    @Bean(name = "jmsTemplate")
    public JmsTemplate jmsTemplate(ActiveMQConnectionFactory factory) {
        if (factory != null) {
            return new JmsTemplate(factory);
        } else {
            return null;
        }
    }

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

        RabbitMqConnProps props = new RabbitMqConnProps();
        props.setHost(properties.getProperty(RABBIT_MQ_HOST));
        props.setPort(Integer.parseInt(properties.getProperty(RABBIT_MQ_PORT)));
        props.setUsername(properties.getProperty(RABBIT_MQ_USERNAME));
        props.setPassword(properties.getProperty(RABBIT_MQ_PASSWORD));

        String threadPoolSizeStr = properties.getProperty(RABBIT_MQ_THREAD_POOL_SIZE);
        if (StringUtils.isNotBlank(threadPoolSizeStr)) {
            props.setThreadPoolSize(Integer.parseInt(threadPoolSizeStr));
        }

        return props;
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
