package ru.citeck.ecos.records.type;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.commands.CommandsService;
import ru.citeck.ecos.commands.dto.CommandResult;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.model.lib.type.dto.TypePermsDef;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.source.dao.local.RemoteSyncRecordsDao;

@Slf4j
@Configuration
public class TypesSyncConfiguration {

    @Autowired
    private CommandsService commandsService;

    @Bean(name = "remoteTypesSyncRecordsDao")
    public RemoteSyncRecordsDao<TypeDto> createRemoteTypesSyncRecordsDao() {
        return new RemoteSyncRecordsDao<>("emodel/type", TypeDto.class);
    }

    @Bean(name = "remoteTypePermsSyncRecordsDao")
    public RemoteSyncRecordsDao<TypePermsDef.Mutable> createRemoteTypePermsSyncRecordsDao() {
        return new RemoteSyncRecordsDao<>("emodel/perms", TypePermsDef.Mutable.class);
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
        };
    }
}
