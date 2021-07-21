package ru.citeck.ecos.eapps;

import org.jetbrains.annotations.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.apps.EcosAppsServiceFactory;
import ru.citeck.ecos.apps.app.EcosAppsService;
import ru.citeck.ecos.apps.app.domain.buildinfo.service.BuildInfoService;
import ru.citeck.ecos.apps.app.provider.EcosAppsProvider;
import ru.citeck.ecos.apps.module.controller.ModuleControllerService;
import ru.citeck.ecos.apps.module.handler.ModuleHandlersService;
import ru.citeck.ecos.apps.module.local.LocalModulesService;
import ru.citeck.ecos.apps.module.remote.RemoteModulesService;
import ru.citeck.ecos.apps.module.type.ModuleTypeService;
import ru.citeck.ecos.apps.module.type.provider.ModuleTypesProvider;
import ru.citeck.ecos.commands.CommandsServiceFactory;
import ru.citeck.ecos.commands.CommandsServiceFactoryConfig;
import ru.citeck.ecos.metarepo.MetaRepoServiceFactoryConfig;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
public class EcosAppsFactoryConfig extends EcosAppsServiceFactory {

    @Autowired
    private CommandsServiceFactoryConfig commandsConfig;
    @Autowired
    private MetaRepoServiceFactoryConfig ecosMetaConfig;
    @Autowired
    private EcosAppsModulesProviderImpl appsProvider;

    @PostConstruct
    public void init() {
        super.init();
    }

    @NotNull
    @Override
    public CommandsServiceFactory getCommandsServiceFactory() {
        return commandsConfig;
    }

    @NotNull
    public ModuleTypesProvider createModuleTypesProvider() {
        return appsProvider;
    }

    @NotNull
    public EcosAppsProvider createEcosAppsProvider() {
        return appsProvider;
    }

    @Bean
    @NotNull
    public ModuleTypeService createModuleTypeService() {
        return super.createModuleTypeService();
    }

    @Bean
    @NotNull
    public ModuleControllerService createModuleControllerService() {
        return super.createModuleControllerService();
    }

    @Bean
    @NotNull
    public LocalModulesService createLocalModulesService() {

        LocalModulesService localModulesService = super.createLocalModulesService();

        Map<String, String> mapping = new HashMap<>();
        mapping.put("ui/form", "ecos-forms");
        mapping.put("process/cmmn", "case/templates");

        localModulesService.setModuleLocations(mapping);
        return localModulesService;
    }

    @Bean
    @NotNull
    protected BuildInfoService createBuildInfoService() {
        return super.createBuildInfoService();
    }

    @Bean
    @NotNull
    public RemoteModulesService createRemoteModulesService() {
        return super.createRemoteModulesService();
    }

    @Bean
    @NotNull
    public EcosAppsService createEcosAppsService() {
        return super.createEcosAppsService();
    }

    @Bean
    @NotNull
    public ModuleHandlersService createModuleHandlers() {
        return super.createModuleHandlers();
    }
}
