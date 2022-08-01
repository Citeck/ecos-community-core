package ru.citeck.ecos.webapp.registry;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.apps.app.service.LocalAppService;
import ru.citeck.ecos.model.lib.ModelServiceFactory;
import ru.citeck.ecos.model.lib.num.dto.NumTemplateDef;
import ru.citeck.ecos.model.lib.type.dto.TypePermsDef;
import ru.citeck.ecos.webapp.api.context.EcosWebAppContext;
import ru.citeck.ecos.webapp.lib.model.num.registry.NumTemplatesRegistry;
import ru.citeck.ecos.webapp.lib.model.perms.registry.TypePermissionsRegistry;
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef;
import ru.citeck.ecos.webapp.lib.model.type.records.TypeRecordsDao;
import ru.citeck.ecos.webapp.lib.model.type.registry.DefaultTypesInitializer;
import ru.citeck.ecos.webapp.lib.model.type.registry.EcosTypesRegistry;
import ru.citeck.ecos.webapp.lib.model.type.registry.TypeArtifactsInitializer;
import ru.citeck.ecos.webapp.lib.registry.init.EcosRegistryInitializer;
import ru.citeck.ecos.webapp.lib.registry.init.ZkRegistryInitializer;
import ru.citeck.ecos.zookeeper.EcosZooKeeper;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
public class EcosRegistriesConfig {

    @Bean
    public TypeRecordsDao ecosTypeRecordsDao(
        ModelServiceFactory modelServiceFactory,
        EcosTypesRegistry registry
    ) {
        return new TypeRecordsDao(registry, modelServiceFactory);
    }

    @Bean
    public EcosTypesRegistry ecosTypesRegistry(
        EcosZooKeeper zookeeper,
        EcosWebAppContext webAppContext,
        LocalAppService localAppService
    ) {
        List<EcosRegistryInitializer<TypeDef>> initializers = new ArrayList<>();
        initializers.add(new DefaultTypesInitializer());
        initializers.add(new TypeArtifactsInitializer(localAppService));
        initializers.add(new ZkRegistryInitializer<>(
            webAppContext.getAppLockService(),
            zookeeper,
            true,
            webAppContext.getTasksApi().getMainScheduler()
        ));
        return new EcosTypesRegistry(initializers);
    }

    @Bean
    public TypePermissionsRegistry ecosPermsRegistry(
        EcosZooKeeper zookeeper,
        EcosWebAppContext webAppContext
    ) {
        List<EcosRegistryInitializer<TypePermsDef>> initializers = new ArrayList<>();
        initializers.add(new ZkRegistryInitializer<>(
            webAppContext.getAppLockService(),
            zookeeper,
            true,
            webAppContext.getTasksApi().getMainScheduler()
        ));
        return new TypePermissionsRegistry(initializers);
    }

    @Bean
    public NumTemplatesRegistry numTemplatesRegistry(
        EcosZooKeeper zookeeper,
        EcosWebAppContext webAppContext
    ) {
        List<EcosRegistryInitializer<NumTemplateDef>> initializers = new ArrayList<>();
        initializers.add(new ZkRegistryInitializer<>(
            webAppContext.getAppLockService(),
            zookeeper,
            true,
            webAppContext.getTasksApi().getMainScheduler()
        ));
        return new NumTemplatesRegistry(initializers);
    }
}
