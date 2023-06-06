package ru.citeck.ecos.webapp.task;

import org.jetbrains.annotations.NotNull;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.context.lib.auth.AuthContext;
import ru.citeck.ecos.webapp.lib.task.EcosTaskComponentsFactory;
import ru.citeck.ecos.webapp.lib.task.executor.TaskExecutorComponent;
import ru.citeck.ecos.webapp.lib.task.executor.TaskExecutorProperties;

@Component
public class EcosTaskComponentsFactoryImpl implements EcosTaskComponentsFactory {

    @NotNull
    @Override
    public TaskExecutorComponent createTaskExecutorComponent(
        @NotNull String key,
        @NotNull TaskExecutorProperties props
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor() {
            @Override
            public Thread createThread(Runnable runnable) {
                return super.createThread(() -> {
                    AuthContext.runAsSystemJ(runnable::run);
                });
            }
        };

        executor.setCorePoolSize(props.getCorePoolSize());
        executor.setMaxPoolSize(props.getMaxPoolSize());
        executor.setQueueCapacity(props.getQueueCapacity());

        executor.setThreadNamePrefix("ecos-task-executor-" + key + "-");
        executor.setAwaitTerminationSeconds(10);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();

        return new TaskExecutorComponentImpl(executor);
    }
}
