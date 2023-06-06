package ru.citeck.ecos.commands;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.RetryingTransactionHelper;
import org.alfresco.service.ServiceRegistry;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.commands.context.CommandCtxController;
import ru.citeck.ecos.commands.context.CommandCtxManager;
import ru.citeck.ecos.commands.rabbit.RabbitCommandsService;
import ru.citeck.ecos.commands.remote.RemoteCommandsService;
import ru.citeck.ecos.commands.transaction.TransactionManager;
import ru.citeck.ecos.rabbitmq.RabbitMqConn;
import ru.citeck.ecos.rabbitmq.RabbitMqConnProvider;
import ru.citeck.ecos.webapp.api.EcosWebAppApi;

import java.util.Properties;
import java.util.concurrent.Callable;

@Slf4j
@Configuration
public class CommandsServiceFactoryConfig extends CommandsServiceFactory {

    private static final String CONCURRENT_TIMEOUT_MS = "commands.timeoutMs";
    private static final String CONCURRENT_CHANNEL_QOS = "commands.channelQos";
    private static final String CONCURRENT_COMMAND_CONSUMERS = "commands.concurrentCommandConsumers";

    @Autowired
    @Qualifier("global-properties")
    private Properties properties;

    private RetryingTransactionHelper retryHelper;

    @Autowired
    private RabbitMqConnProvider connProvider;

    @Autowired
    private EcosWebAppApi ecosWebAppContext;

    @Bean
    @Override
    public CommandsService createCommandsService() {
        return super.createCommandsService();
    }

    @Bean
    @Override
    public CommandsProperties createProperties() {

        CommandsProperties.Builder props = CommandsProperties.create();

        int concurrentCommandConsumers = Integer.parseInt(properties.getProperty(CONCURRENT_COMMAND_CONSUMERS, "4"));
        props.withConcurrentCommandConsumers(concurrentCommandConsumers);
        String commandTimeoutMsStr = properties.getProperty(CONCURRENT_TIMEOUT_MS);

        if (StringUtils.isNotBlank(commandTimeoutMsStr)) {
            props.withCommandTimeoutMs(Long.parseLong(commandTimeoutMsStr));
        }

        String commandChannelQos = properties.getProperty(CONCURRENT_CHANNEL_QOS);
        if (StringUtils.isNotBlank(commandChannelQos)) {
            props.withChannelsQos(Integer.parseInt(commandChannelQos));
        }

        return props.build();
    }

    @Nullable
    @Override
    public EcosWebAppApi getEcosWebAppApi() {
        return ecosWebAppContext;
    }

    @Bean
    @NotNull
    @Override
    public RemoteCommandsService createRemoteCommandsService() {

        RabbitMqConn connection = connProvider.getConnection();

        if (connection == null) {
            log.warn("Rabbit MQ connection is null. Remote commands won't be available");
            return super.createRemoteCommandsService();
        }

        return new RabbitCommandsService(this, connection);
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

    @Autowired
    public void setEcosWebAppApi(EcosWebAppApi ecosWebAppContext) {
        this.ecosWebAppContext = ecosWebAppContext;
    }
}
