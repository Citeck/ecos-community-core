package ru.citeck.ecos.records.type;

import ecos.com.google.common.cache.CacheBuilder;
import ecos.com.google.common.cache.CacheLoader;
import ecos.com.google.common.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.commands.CommandsService;
import ru.citeck.ecos.commands.dto.CommandResult;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.data.entity.EntityWithMeta;
import ru.citeck.ecos.model.lib.num.dto.NumTemplateDef;
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils;
import ru.citeck.ecos.node.etype.EcosTypeAlfTypeService;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.webapp.api.entity.EntityRef;
import ru.citeck.ecos.webapp.lib.model.num.registry.NumTemplatesRegistry;
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef;
import ru.citeck.ecos.webapp.lib.model.type.registry.EcosTypesRegistry;

import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class TypesSyncConfiguration {

    @Autowired
    private CommandsService commandsService;
    @Autowired
    private NamespaceService namespaceService;
    @Autowired
    private EcosTypesRegistry ecosTypesRegistry;
    @Autowired
    private NumTemplatesRegistry numTemplatesRegistry;

    @Bean
    public TypesManager createInfoProvider() {

        return new TypesManager() {

            private final LoadingCache<QName, EntityRef> ecosTypeByAlfTypeCache = CacheBuilder.newBuilder()
                .maximumSize(300)
                .expireAfterAccess(30, TimeUnit.SECONDS)
                .build(CacheLoader.from(this::getEcosTypeImpl));

            @Override
            public TypeDef getType(EntityRef typeRef) {
                return ecosTypesRegistry.getValue(typeRef.getLocalId());
            }

            @Override
            public NumTemplateDef getNumTemplate(EntityRef templateRef) {
                return numTemplatesRegistry.getValue(templateRef.getLocalId());
            }

            @Override
            public Long getNextNumber(EntityRef templateRef, ObjectData model) {

                Object command = new GetNextNumberCommand(templateRef, model);
                CommandResult numberRes = commandsService.executeSync(command, "emodel");

                Runnable printErrorMsg = () ->
                    log.error("Get next number failed. TemplateRef: " + templateRef + " model: " + model);

                numberRes.throwPrimaryErrorIfNotNull(printErrorMsg);

                if (numberRes.getErrors().size() > 0) {
                    printErrorMsg.run();
                    throw new RuntimeException("Error");
                }

                GetNextNumberResult result = numberRes.getResultAs(GetNextNumberResult.class);

                Long number = result != null ? result.getNumber() : null;
                if (number == null) {
                    throw new IllegalStateException("Number can't be generated");
                }
                return number;
            }

            @NotNull
            @Override
            public EntityRef getEcosTypeByAlfType(@Nullable QName alfType) {
                if (alfType == null) {
                    return EntityRef.EMPTY;
                }
                return ecosTypeByAlfTypeCache.getUnchecked(alfType);
            }

            @NotNull
            private EntityRef getEcosTypeImpl(@Nullable QName alfType) {

                if (alfType == null) {
                    return EntityRef.EMPTY;
                }

                String typeShortName = alfType.toPrefixString(namespaceService);

                for (EntityWithMeta<TypeDef> typeEntity : ecosTypesRegistry.getAllValues().values()) {
                    TypeDef typeDto = typeEntity.getEntity();
                    ObjectData properties = typeDto.getProperties();
                    String alfTypeProp = properties.get(EcosTypeAlfTypeService.PROP_ALF_TYPE).asText();
                    if (alfTypeProp.equals(typeShortName)) {
                        return TypeUtils.getTypeRef(typeDto.getId());
                    }
                }
                return EntityRef.EMPTY;
            }
        };
    }
}
