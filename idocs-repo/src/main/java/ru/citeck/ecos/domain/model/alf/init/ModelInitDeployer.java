package ru.citeck.ecos.domain.model.alf.init;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.repo.dictionary.DictionaryDAO;
import org.alfresco.repo.dictionary.DictionaryListener;
import org.alfresco.service.transaction.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.domain.model.alf.dao.AlfAutoModelsDao;
import ru.citeck.ecos.domain.model.alf.dao.TypeModelInfo;

import java.util.List;

@Slf4j
@Component
@DependsOn({
    "metadataQueryIndexesCheck",
    "metadataQueryIndexesCheck2"
})
public class ModelInitDeployer implements DictionaryListener, ApplicationListener<ContextRefreshedEvent> {

    private final AlfAutoModelsDao modelsDao;
    private final TransactionService transactionService;
    private final DictionaryDAO dictionaryDao;

    private boolean initialized = false;

    @Autowired
    public ModelInitDeployer(@Qualifier("dictionaryDAO")
                             DictionaryDAO dictionaryDao,
                             AlfAutoModelsDao modelsDao,
                             TransactionService transactionService) {

        this.modelsDao = modelsDao;
        this.dictionaryDao = dictionaryDao;
        this.transactionService = transactionService;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {

        if (initialized) {
            return;
        }
        initialized = true;

        dictionaryDao.registerListener(this);
    }

    @Override
    public void onDictionaryInit() {

        log.info("==== Auto models deployer ====");

        List<TypeModelInfo> modelsInfo =
            transactionService.getRetryingTransactionHelper().doInTransaction(modelsDao::getAllModels);

        modelsInfo.forEach(model -> {
            try {
                log.info("Deploy auto model for Type: " + model.getTypeRef()
                    + " from nodeRef: " + model.getNodeRef());
                dictionaryDao.putModel(model.getModel());
            } catch (Exception e) {
                log.error("Deploy failed. Type: " + model.getTypeRef(), e);
            }
        });

        log.info("---- Auto models deployer ----");
    }

    @Override
    public void afterDictionaryDestroy() {
    }

    @Override
    public void afterDictionaryInit() {
    }
}
