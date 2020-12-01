package ru.citeck.ecos.domain.model.config;

import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.model.lib.ModelServiceFactory;
import ru.citeck.ecos.model.lib.permissions.repo.PermissionsRepo;
import ru.citeck.ecos.model.lib.permissions.service.PermsEvaluator;
import ru.citeck.ecos.model.lib.permissions.service.RecordPermsService;
import ru.citeck.ecos.model.lib.role.service.RoleService;
import ru.citeck.ecos.model.lib.type.dto.TypeDef;
import ru.citeck.ecos.model.lib.type.dto.TypePermsDef;
import ru.citeck.ecos.model.lib.type.repo.TypesRepo;
import ru.citeck.ecos.model.lib.type.service.TypeDefService;
import ru.citeck.ecos.node.EcosTypeService;
import ru.citeck.ecos.records.type.TypeDto;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records3.RecordsServiceFactory;
import ru.citeck.ecos.records2.source.dao.local.RemoteSyncRecordsDao;

import java.util.List;

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
    protected TypeDefService createTypeDefService() {
        return super.createTypeDefService();
    }

    @Bean
    @NotNull
    @Override
    protected RoleService createRoleService() {
        return super.createRoleService();
    }

    @Bean
    @NotNull
    @Override
    protected TypesRepo createTypesRepo() {
        return new TypesRepo() {
            @Nullable
            @Override
            public TypeDef getTypeDef(@NotNull RecordRef recordRef) {
                TypeDto typeDto = typeRecords.getRecord(recordRef.getId()).orElse(null);
                if (typeDto == null || typeDto.getId() == null) {
                    return null;
                }
                return TypeDef.create(b -> {
                    b.withId(typeDto.getId());
                    b.withName(typeDto.getName());
                    b.withParentRef(typeDto.getParentRef());
                    b.withModel(typeDto.getModel());
                    b.withDocLib(typeDto.getDocLib());
                    b.withNumTemplateRef(typeDto.getNumTemplateRef());
                    b.withInheritNumTemplate(typeDto.isInheritNumTemplate());
                    return Unit.INSTANCE;
                });
            }

            @NotNull
            @Override
            public List<RecordRef> getChildren(@NotNull RecordRef recordRef) {
                return typeService.getChildren(recordRef);
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
