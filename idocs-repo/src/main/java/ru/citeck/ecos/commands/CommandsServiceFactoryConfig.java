package ru.citeck.ecos.commands;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.RetryingTransactionHelper;
import org.alfresco.service.ServiceRegistry;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.extensions.surf.util.AbstractLifecycleBean;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commands.context.CommandCtxController;
import ru.citeck.ecos.commands.context.CommandCtxManager;
import ru.citeck.ecos.commands.rabbit.RabbitCommandsService;
import ru.citeck.ecos.commands.remote.RemoteCommandsService;
import ru.citeck.ecos.commands.transaction.TransactionManager;
import ru.citeck.ecos.eureka.EurekaAlfInstanceConfig;
import ru.citeck.ecos.rabbitmq.RabbitMqConn;
import ru.citeck.ecos.rabbitmq.RabbitMqConnFactory;
import ru.citeck.ecos.rabbitmq.RabbitMqConnProps;
import ru.citeck.ecos.rabbitmq.RabbitMqConnProvider;

import java.util.Properties;
import java.util.concurrent.Callable;

@Slf4j
@Configuration
@DependsOn({"moduleStarter"})
public class CommandsServiceFactoryConfig extends CommandsServiceFactory {

    private static final String RABBIT_MQ_HOST = "rabbitmq.server.host";
    private static final String RABBIT_MQ_PORT = "rabbitmq.server.port";
    private static final String RABBIT_MQ_USERNAME = "rabbitmq.server.username";
    private static final String RABBIT_MQ_PASSWORD = "rabbitmq.server.password";
    private static final String RABBIT_MQ_THREAD_POOL_SIZE = "rabbitmq.threadPoolSize";
    private static final String CONCURRENT_TIMEOUT_MS = "commands.timeoutMs";
    private static final String CONCURRENT_COMMAND_CONSUMERS = "commands.concurrentCommandConsumers";

    @Autowired
    @Qualifier("global-properties")
    private Properties properties;

    private RetryingTransactionHelper retryHelper;

    @Autowired
    private EurekaAlfInstanceConfig instanceConfig;

    @Bean
    @Override
    public CommandsService createCommandsService() {
        return super.createCommandsService();
    }

    @Bean
    @Override
    public CommandsProperties createProperties() {

        CommandsProperties props = new CommandsProperties();
        props.setAppInstanceId(instanceConfig.getInstanceId());
        props.setAppName(instanceConfig.getAppname());

        String threadPoolSizeStr = properties.getProperty(RABBIT_MQ_THREAD_POOL_SIZE);
        if (StringUtils.isNotBlank(threadPoolSizeStr)) {
            props.setThreadPoolSize(Integer.parseInt(threadPoolSizeStr));
        }

        props.setConcurrentCommandConsumers(Integer.parseInt(properties.getProperty(CONCURRENT_COMMAND_CONSUMERS, "4")));
        String commandTimeoutMsStr = properties.getProperty(CONCURRENT_TIMEOUT_MS);
        if (StringUtils.isNotBlank(commandTimeoutMsStr)) {
            props.setCommandTimeoutMs(Integer.parseInt(commandTimeoutMsStr));
        }

        return props;
    }

    @Bean
    @NotNull
    @Override
    public RemoteCommandsService createRemoteCommandsService() {

        RabbitMqConn connection = rabbitMqConnProvider().getConnection();
        if (connection == null) {
            log.warn("Rabbit mq host is null. Remote commands won't be available");
            return super.createRemoteCommandsService();
        }
        return new RabbitCommandsService(this, connection);
    }

    @Bean
    public RabbitMqConnProvider rabbitMqConnProvider() {

        String host = properties.getProperty(RABBIT_MQ_HOST);
        if (StringUtils.isBlank(host)) {
            log.warn("Rabbit mq host is null. Remote commands won't be available");
            return () -> null;
        }

        RabbitMqConnProps props = new RabbitMqConnProps();
        props.setHost(host);
        props.setUsername(properties.getProperty(RABBIT_MQ_USERNAME));
        props.setPassword(properties.getProperty(RABBIT_MQ_PASSWORD));
        props.setPort(Integer.valueOf(properties.getProperty(RABBIT_MQ_PORT)));

        RabbitMqConn connection = new RabbitMqConnFactory().createConnection(props);

        return () -> connection;
    }

    @NotNull
    @Override
    protected TransactionManager createTransactionManager() {
        return new TransactionManager() {
            @Override
            public <T> T doInTransaction(@NotNull Callable<T> callable) {
                return retryHelper.doInTransaction(() -> {
                    CommandCtxManager commandCtxManager = getCommandCtxManager();
                    String currentUser = commandCtxManager.getCurrentUser();
                    return AuthenticationUtil.runAs(callable::call, currentUser);
                }, false, false);
            }
        };
    }

    @NotNull
    @Override
    protected CommandCtxController createCommandCtxController() {
        return new CommandCtxController() {
            @NotNull
            @Override
            public String setCurrentUser(@NotNull String username) {
                if (StringUtils.isEmpty(username)) {
                    username = AuthenticationUtil.getSystemUserName();
                }
                AuthenticationUtil.setRunAsUser(username);
                return AuthenticationUtil.getRunAsUser();
            }

            @NotNull
            @Override
            public String getCurrentUser() {
                String user = AuthenticationUtil.getRunAsUser();
                return user == null ? AuthenticationUtil.getSystemUserName() : user;
            }

            @NotNull
            @Override
            public String setCurrentTenant(@NotNull String tenant) {
                return tenant;
            }

            @NotNull
            @Override
            public String getCurrentTenant() {
                return "";
            }
        };
    }

    @Autowired
    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        retryHelper = serviceRegistry.getRetryingTransactionHelper();
    }

    @Component
    public static class RemoteInitializer extends AbstractLifecycleBean {

        @Autowired
        private CommandsServiceFactoryConfig config;

        @Override
        protected void onBootstrap(ApplicationEvent event) {

            log.info("==================== Initialize Commands Rabbit Service ====================");
            try {
                config.createRemoteCommandsService();
            } catch (Exception e) {
                log.error("Commands remote service initialization failed", e);
            }
        }

        @Override
        protected void onShutdown(ApplicationEvent applicationEvent) {
        }
    }
}
