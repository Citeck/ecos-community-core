package ru.citeck.ecos.domain.model.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.commands.CommandsServiceFactory;
import ru.citeck.ecos.commons.data.entity.EntityWithMeta;
import ru.citeck.ecos.model.lib.ModelServiceFactory;
import ru.citeck.ecos.model.lib.api.EcosModelAppApi;
import ru.citeck.ecos.model.lib.api.commands.CommandsModelAppApi;
import ru.citeck.ecos.model.lib.attributes.computed.ComputedAttsService;
import ru.citeck.ecos.model.lib.num.repo.NumTemplatesRepo;
import ru.citeck.ecos.model.lib.permissions.repo.PermissionsRepo;
import ru.citeck.ecos.model.lib.permissions.service.PermsEvaluator;
import ru.citeck.ecos.model.lib.permissions.service.RecordPermsService;
import ru.citeck.ecos.model.lib.role.service.RoleService;
import ru.citeck.ecos.model.lib.status.service.StatusService;
import ru.citeck.ecos.model.lib.type.dto.TypeInfo;
import ru.citeck.ecos.model.lib.type.repo.TypesRepo;
import ru.citeck.ecos.model.lib.type.service.TypeRefService;
import ru.citeck.ecos.records3.RecordsServiceFactory;
import ru.citeck.ecos.service.CiteckServices;
import ru.citeck.ecos.webapp.api.context.EcosWebAppContext;
import ru.citeck.ecos.webapp.api.entity.EntityRef;
import ru.citeck.ecos.webapp.lib.model.num.registry.NumTemplatesRegistry;
import ru.citeck.ecos.webapp.lib.model.perms.registry.TypePermissionsRegistry;
import ru.citeck.ecos.webapp.lib.model.type.registry.EcosTypesRegistry;

import java.util.List;

@Configuration
public class ModelServiceFactoryConfig extends ModelServiceFactory {

    private EcosTypesRegistry typesRegistry;
    private EcosWebAppContext ecosWebAppContext;
    private NumTemplatesRegistry numTemplatesRegistry;
    private CommandsServiceFactory commandsServiceFactory;
    private TypePermissionsRegistry typePermissionsRegistry;

    @Bean
    @NotNull
    public ComputedAttsService computedAttsToStoreService() {
        return getComputedAttsService();
    }

    @NotNull
    @Override
    protected PermissionsRepo createPermissionsRepo() {

        return recordRef -> typePermissionsRegistry.getAllValues()
            .values()
            .stream()
            .filter(it -> recordRef.equals(it.getEntity().getTypeRef()))
            .findFirst()
            .map(EntityWithMeta::getEntity)
            .orElse(null);
    }

    @Bean
    @NotNull
    @Override
    protected NumTemplatesRepo createNumTemplatesRepo() {
        return recordRef -> numTemplatesRegistry.getValue(recordRef.getLocalId());
    }

    @Bean
    @NotNull
    public PermsEvaluator permsEvaluator() {
        return getPermsEvaluator();
    }

    @Bean
    @NotNull
    public TypeRefService typeRefService() {
        return getTypeRefService();
    }

    @Bean
    @NotNull
    public RoleService roleService() {
        return getRoleService();
    }

    @Bean(name = CiteckServices.STATUS_SERVICE_BEAN_NAME)
    @NotNull
    public StatusService statusService() {
        return getStatusService();
    }

    @NotNull
    @Override
    protected EcosModelAppApi createEcosModelAppApi() {
        return new CommandsModelAppApi(commandsServiceFactory);
    }

    @Nullable
    @Override
    public EcosWebAppContext getEcosWebAppContext() {
        return ecosWebAppContext;
    }

    @NotNull
    @Override
    protected TypesRepo createTypesRepo() {
        return new TypesRepo() {
            @Nullable
            @Override
            public TypeInfo getTypeInfo(@NotNull EntityRef recordRef) {
                return typesRegistry.getTypeInfo(recordRef);
            }
            @NotNull
            @Override
            public List<EntityRef> getChildren(@NotNull EntityRef recordRef) {
                return typesRegistry.getChildren(recordRef);
            }
        };
    }

    @Bean
    @NotNull
    public RecordPermsService recordPermsService() {
        return getRecordPermsService();
    }

    @Autowired
    public void setCommandsServiceFactory(CommandsServiceFactory commandsServiceFactory) {
        this.commandsServiceFactory = commandsServiceFactory;
    }

    @Autowired
    public void setTypesRegistry(EcosTypesRegistry typesRegistry) {
        this.typesRegistry = typesRegistry;
    }

    @Override
    @Autowired
    public void setRecordsServices(@NotNull RecordsServiceFactory services) {
        super.setRecordsServices(services);
    }

    @Autowired
    public void setEcosWebAppContext(EcosWebAppContext ecosWebAppContext) {
        this.ecosWebAppContext = ecosWebAppContext;
    }

    @Autowired
    public void setNumTemplatesRegistry(NumTemplatesRegistry numTemplatesRegistry) {
        this.numTemplatesRegistry = numTemplatesRegistry;
    }

    @Autowired
    public void setTypePermissionsRegistry(TypePermissionsRegistry typePermissionsRegistry) {
        this.typePermissionsRegistry = typePermissionsRegistry;
    }
}
