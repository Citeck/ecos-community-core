package ru.citeck.ecos.records.type;

import ecos.com.google.common.cache.CacheBuilder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.commands.CommandsService;
import ru.citeck.ecos.commands.dto.CommandResult;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.model.lib.type.dto.TypePermsDef;
import ecos.com.google.common.cache.Cache;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.predicate.PredicateService;
import ru.citeck.ecos.records2.predicate.model.Predicates;
import ru.citeck.ecos.records2.source.dao.local.RemoteSyncRecordsDao;
import ru.citeck.ecos.records3.RecordsService;
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery;
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes;

import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class TypesSyncConfiguration {

    @Autowired
    private CommandsService commandsService;

    @Autowired
    private RecordsService recordsService;

    @Bean(name = "remoteTypesSyncRecordsDao")
    public RemoteSyncRecordsDao<TypeDto> createRemoteTypesSyncRecordsDao() {
        return new RemoteSyncRecordsDao<>("emodel/type", TypeDto.class);
    }

    @Bean(name = "remoteTypePermsSyncRecordsDao")
    public RemoteSyncRecordsDao<TypePermsDef> createRemoteTypePermsSyncRecordsDao() {
        return new RemoteSyncRecordsDao<>("emodel/perms", TypePermsDef.class);
    }

    @Bean(name = "remoteNumTemplatesSyncRecordsDao")
    public RemoteSyncRecordsDao<NumTemplateDto> createRemoteNumTemplatesSyncRecordsDao() {
        return new RemoteSyncRecordsDao<>("emodel/num-template", NumTemplateDto.class);
    }

    @Bean
    public TypesManager createInfoProvider() {

        RemoteSyncRecordsDao<TypeDto> typesDao = createRemoteTypesSyncRecordsDao();
        RemoteSyncRecordsDao<NumTemplateDto> numTemplatesDao = createRemoteNumTemplatesSyncRecordsDao();

        return new TypesManager() {

            private Cache<String, String> typesCache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .build();

            @Override
            public TypeDto getType(RecordRef typeRef) {
                return typesDao.getRecord(typeRef.getId()).orElse(null);
            }

            @Override
            public NumTemplateDto getNumTemplate(RecordRef templateRef) {
                return numTemplatesDao.getRecord(templateRef.getId()).orElse(null);
            }

            @Override
            public Long getNextNumber(RecordRef templateRef, ObjectData model) {

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

            @Override
            public RecordRef getEcosType(String alfType) {
                if (StringUtils.isEmpty(alfType)) {
                    return null;
                }
                String ecosType = typesCache.getIfPresent(alfType);
                if (ecosType == null) {
                    RecordsQuery query = RecordsQuery.create()
                        .withSourceId("emodel/type")
                        .withLanguage(PredicateService.LANGUAGE_PREDICATE)
                        .withQuery(Predicates.not(Predicates.empty("properties")))
                        .build();
                    RecsQueryRes<TypeDef> result = recordsService.query(query, TypeDef.class);
                    for (TypeDef typeDef : result.getRecords()) {
                        if (alfType.equals(typeDef.getProperties().get("alfType").asText())) {
                            ecosType = typeDef.id;
                            typesCache.put(alfType, ecosType);
                            break;
                        }
                    }
                }
                if (ecosType != null) {
                    return RecordRef.create("emodel", "type", ecosType);
                }
                return null;
            }
        };
    }

    @Data
    static class TypeDef {
        private String id;
        private MLText name;
        private ObjectData properties;
    }
}
