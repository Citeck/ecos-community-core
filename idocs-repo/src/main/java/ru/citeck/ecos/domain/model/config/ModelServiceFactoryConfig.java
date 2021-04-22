package ru.citeck.ecos.domain.model.config;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.model.lib.ModelServiceFactory;
import ru.citeck.ecos.model.lib.permissions.repo.PermissionsRepo;
import ru.citeck.ecos.model.lib.permissions.service.PermsEvaluator;
import ru.citeck.ecos.model.lib.permissions.service.RecordPermsService;
import ru.citeck.ecos.model.lib.role.service.RoleService;
import ru.citeck.ecos.model.lib.status.service.StatusService;
import ru.citeck.ecos.model.lib.type.dto.TypeModelDef;
import ru.citeck.ecos.model.lib.type.dto.TypePermsDef;
import ru.citeck.ecos.model.lib.type.repo.TypesRepo;
import ru.citeck.ecos.model.lib.type.service.TypeRefService;
import ru.citeck.ecos.node.EcosTypeService;
import ru.citeck.ecos.records.type.TypeDto;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.source.dao.local.RemoteSyncRecordsDao;
import ru.citeck.ecos.records3.RecordsServiceFactory;
import ru.citeck.ecos.service.CiteckServices;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

@Configuration
public class ModelServiceFactoryConfig extends ModelServiceFactory {

    private RemoteSyncRecordsDao<TypePermsDef> typePermsRecords;
    private EcosTypeService typeService;
    private RemoteSyncRecordsDao<TypeDto> typeRecords;

    @NotNull
    @Override
    protected PermissionsRepo createPermissionsRepo() {

        return recordRef -> typePermsRecords.getRecords()
            .values()
            .stream()
            .filter(it -> recordRef.equals(it.getTypeRef()))
            .findFirst()
            .orElse(null);
    }

    @Bean
    @NotNull
    @Override
    protected PermsEvaluator createPermsEvaluator() {
        return super.createPermsEvaluator();
    }

    @Bean
    @NotNull
    @Override
    protected TypeRefService createTypeRefService() {
        return super.createTypeRefService();
    }

    @Bean
    @NotNull
    @Override
    protected RoleService createRoleService() {
        return super.createRoleService();
    }

    @Bean(name = CiteckServices.STATUS_SERVICE_BEAN_NAME)
    @NotNull
    @Override
    protected StatusService createStatusService() {
        return super.createStatusService();
    }

    @Bean
    @NotNull
    @Override
    protected TypesRepo createTypesRepo() {
        return new TypesRepo() {
            @NotNull
            @Override
            public TypeModelDef getModel(@NotNull RecordRef typeRef) {
                return getFromDto(typeRef, TypeDto::getResolvedModel, () -> TypeModelDef.EMPTY);
            }

            @NotNull
            @Override
            public RecordRef getParent(@NotNull RecordRef typeRef) {
                return getFromDto(typeRef, TypeDto::getParentRef, () -> RecordRef.EMPTY);
            }

            @NotNull
            @Override
            public List<RecordRef> getChildren(@NotNull RecordRef typeRef) {
                return typeService.getChildren(typeRef);
            }

            @NotNull
            private <T> T getFromDto(RecordRef typeRef, Function<TypeDto, T> action, Supplier<T> orElse) {

                if (typeRef.getId().isEmpty()) {
                    return orElse.get();
                }
                TypeDto typeDto = typeRecords.getRecord(typeRef.getId()).orElse(null);
                if (typeDto == null || StringUtils.isBlank(typeDto.getId())) {
                    return orElse.get();
                }
                T result = action.apply(typeDto);
                return result != null ? result : orElse.get();
            }
        };
    }

    @Bean
    @NotNull
    @Override
    protected RecordPermsService createRecordPermsService() {
        return super.createRecordPermsService();
    }

    @Autowired
    @Qualifier("remoteTypesSyncRecordsDao")
    public void setTypeRecords(RemoteSyncRecordsDao<TypeDto> typeRecords) {
        this.typeRecords = typeRecords;
    }

    @Autowired
    public void setTypeService(EcosTypeService typeService) {
        this.typeService = typeService;
    }

    @Autowired
    @Qualifier("remoteTypePermsSyncRecordsDao")
    public void setTypePermsRecords(RemoteSyncRecordsDao<TypePermsDef> typePermsRecords) {
        this.typePermsRecords = typePermsRecords;
    }

    @Override
    @Autowired
    public void setRecordsServices(@NotNull RecordsServiceFactory services) {
        super.setRecordsServices(services);
    }
}
