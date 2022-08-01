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
    public RecordsService recordsService() {
        return getRecordsService();
    }

    @Bean
    @NotNull
    public ru.citeck.ecos.records3.RecordsService recordsServiceV1() {
        return getRecordsServiceV1();
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
    public LocalRecordsResolver localRecordsResolver() {
        return getLocalRecordsResolver();
    }

    @Bean
    public RecordEvaluatorService recordEvaluatorService() {
        return getRecordEvaluatorService();
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
    public QueryLangService queryLangService() {
        return getQueryLangService();
    }

    @Bean
    public PredicateService predicateService() {
        return getPredicateService();
    }

    @Bean
    @Override
    protected RestHandler createRestHandler() {
        return new RestHandler(this);
    }

    @Bean
    public MetaValuesConverter metaValuesConverter() {
        return getMetaValuesConverter();
    }

    @NotNull
    @Override
    protected Supplier<? extends QueryContext> createQueryContextSupplier() {
        return () -> new AlfGqlContext(serviceRegistry);
    }

    @Bean
    @NotNull
    public MetaRecordsDaoAttsProvider metaRecordsDaoAttsProvider() {
        return getMetaRecordsDaoAttsProvider();
    }

    @Bean
    @NotNull
    public RecordsTemplateService recordsTemplateService() {
        return getRecordsTemplateService();
    }

    @Bean
    @NotNull
    public RestHandlerAdapter restHandlerAdapter() {
        return getRestHandlerAdapter();
    }
}
