package ru.citeck.ecos.records;

import kotlin.jvm.functions.Function0;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.transaction.TransactionService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.graphql.AlfGqlContext;
import ru.citeck.ecos.records3.RecordsProperties;
import ru.citeck.ecos.records2.evaluator.RecordEvaluatorService;
import ru.citeck.ecos.records2.meta.RecordsTemplateService;
import ru.citeck.ecos.records2.predicate.PredicateService;
import ru.citeck.ecos.records2.querylang.QueryLangService;
import ru.citeck.ecos.records2.QueryContext;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records3.RecordsServiceFactory;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValuesConverter;
import ru.citeck.ecos.records2.request.rest.RestHandler;
import ru.citeck.ecos.records3.record.request.RequestContext;
import ru.citeck.ecos.records3.record.resolver.LocalRecordsResolver;
import ru.citeck.ecos.records2.source.dao.local.meta.MetaRecordsDaoAttsProvider;
import ru.citeck.ecos.records3.rest.RestHandlerAdapter;
import ru.citeck.ecos.records3.txn.RecordsTxnService;
import ru.citeck.ecos.webapp.api.context.EcosWebAppContext;

import javax.annotation.PostConstruct;
import java.util.function.Supplier;

@Configuration
public class RecordsConfiguration extends RecordsServiceFactory {

    @Autowired
    private ServiceRegistry serviceRegistry;
    @Autowired
    private RecordsProperties properties;
    @Autowired
    private TransactionService transactionService;

    @Autowired(required = false)
    private EcosWebAppContext webAppContext;

    @PostConstruct
    public void init() {
        RequestContext.Companion.setDefaultServices(this);
    }

    @Bean
    @Override
    protected RecordsService createRecordsService() {
        return super.createRecordsService();
    }

    @Bean
    @NotNull
    @Override
    protected ru.citeck.ecos.records3.RecordsService createRecordsServiceV1() {
        return super.createRecordsServiceV1();
    }

    @NotNull
    @Override
    protected RecordsTxnService createRecordsTxnService() {
        return new RecordsTxnService() {
            @Override
            public <T> T doInTransaction(boolean readOnly, @NotNull Function0<? extends T> action) {
                return transactionService.getRetryingTransactionHelper()
                    .doInTransaction(action::invoke, readOnly, true);
            }
        };
    }

    @Bean
    @Override
    protected LocalRecordsResolver createLocalRecordsResolver() {
        return super.createLocalRecordsResolver();
    }

    @Bean
    @Override
    protected RecordEvaluatorService createRecordEvaluatorService() {
        return super.createRecordEvaluatorService();
    }

    @Nullable
    @Override
    public EcosWebAppContext getEcosWebAppContext() {
        return webAppContext;
    }

    @Override
    protected RecordsProperties createProperties() {
        return properties;
    }

    @Bean
    @Override
    protected QueryLangService createQueryLangService() {
        return super.createQueryLangService();
    }

    @Bean
    @Override
    protected PredicateService createPredicateService() {
        return super.createPredicateService();
    }

    @Bean
    @Override
    protected RestHandler createRestHandler() {
        return new RestHandler(this);
    }

    @Bean
    @Override
    protected MetaValuesConverter createMetaValuesConverter() {
        return super.createMetaValuesConverter();
    }

    @NotNull
    @Override
    protected Supplier<? extends QueryContext> createQueryContextSupplier() {
        return () -> new AlfGqlContext(serviceRegistry);
    }

    @Bean
    @NotNull
    @Override
    protected MetaRecordsDaoAttsProvider createMetaRecordsDaoAttsProvider() {
        return super.createMetaRecordsDaoAttsProvider();
    }

    @Bean
    @NotNull
    @Override
    protected RecordsTemplateService createRecordsTemplateService() {
        return super.createRecordsTemplateService();
    }

    @Bean
    @NotNull
    @Override
    protected RestHandlerAdapter createRestHandlerAdapter() {
        return super.createRestHandlerAdapter();
    }
}
