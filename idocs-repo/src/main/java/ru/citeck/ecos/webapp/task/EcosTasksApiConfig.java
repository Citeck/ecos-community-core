package ru.citeck.ecos.webapp.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.webapp.api.task.EcosTasksApi;
import ru.citeck.ecos.webapp.api.task.executor.EcosTaskExecutorApi;
import ru.citeck.ecos.webapp.api.task.scheduler.EcosTaskSchedulerApi;
import ru.citeck.ecos.webapp.lib.env.EcosWebAppEnvironment;
import ru.citeck.ecos.webapp.lib.task.EcosTaskComponentsFactory;
import ru.citeck.ecos.webapp.lib.task.EcosTasksManager;

@Configuration
public class EcosTasksApiConfig {

    @Autowired
    private EcosWebAppEnvironment env;
    @Autowired
    private EcosTaskComponentsFactory taskComponentsFactory;

    @Bean
    public EcosTasksManager tasksManager() {
        return new EcosTasksManager(env, taskComponentsFactory);
    }

    @Bean
    public EcosTaskSchedulerApi ecosTaskScheduler(EcosTasksApi tasksApi) {
        return tasksApi.getMainScheduler();
    }

    @Bean
    public EcosTaskExecutorApi ecosTaskExecutor(EcosTasksApi tasksApi) {
        return tasksApi.getMainExecutor();
    }
}
