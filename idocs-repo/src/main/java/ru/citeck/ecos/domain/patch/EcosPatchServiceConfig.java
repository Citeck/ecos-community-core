package ru.citeck.ecos.domain.patch;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.commands.CommandsService;
import ru.citeck.ecos.webapp.api.EcosWebAppApi;
import ru.citeck.ecos.webapp.lib.patch.EcosPatchCommandExecutor;
import ru.citeck.ecos.webapp.lib.patch.EcosPatchService;
import ru.citeck.ecos.webapp.lib.patch.EcosPatchServiceImpl;
import ru.citeck.ecos.webapp.lib.patch.annotaion.PatchBeansArtifactsProvider;
import ru.citeck.ecos.webapp.lib.patch.executor.EcosPatchExecutor;
import ru.citeck.ecos.webapp.lib.patch.executor.bean.BeanPatchExecutor;

import java.util.Collections;
import java.util.List;

@Configuration
public class EcosPatchServiceConfig implements ApplicationContextAware {

    private ApplicationContext applicationContext;

    @Autowired
    private EcosWebAppApi ecosWebAppApi;
    @Autowired
    private CommandsService commandsService;
    @Autowired(required = false)
    private List<EcosPatchExecutor<?, ?>> patchExecutors = Collections.emptyList();

    @Bean
    public EcosPatchService ecosPatchService() {
        EcosPatchServiceImpl service = new EcosPatchServiceImpl();
        for (EcosPatchExecutor<?, ?> executor : patchExecutors) {
            service.register(executor);
        }
        service.register(new BeanPatchExecutor(id -> applicationContext.getBean(id)));
        commandsService.addExecutor(new EcosPatchCommandExecutor(service));
        return service;
    }

    @Bean
    public PatchBeansArtifactsProvider patchBeansArtifactsProvider() {
        return new PatchBeansArtifactsProvider(ecosWebAppApi.getProperties().getAppName(), ecosWebAppApi);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}

