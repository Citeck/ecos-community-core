package ru.citeck.ecos.records;

import com.netflix.appinfo.InstanceInfo;
import kotlin.jvm.functions.Function0;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.transaction.TransactionService;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.extensions.surf.util.AbstractLifecycleBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import ru.citeck.ecos.eureka.EcosEurekaClient;
import ru.citeck.ecos.eureka.EurekaContextConfig;
import ru.citeck.ecos.graphql.AlfGqlContext;
import ru.citeck.ecos.records.type.TypesManager;
import ru.citeck.ecos.records3.RecordsProperties;
import ru.citeck.ecos.records2.evaluator.RecordEvaluatorService;
import ru.citeck.ecos.records2.meta.RecordsTemplateService;
import ru.citeck.ecos.records2.predicate.PredicateService;
import ru.citeck.ecos.records2.querylang.QueryLangService;
import ru.citeck.ecos.records2.QueryContext;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records3.RecordsServiceFactory;
import ru.citeck.ecos.records2.graphql.RecordsMetaGql;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValuesConverter;
import ru.citeck.ecos.records2.meta.RecordsMetaService;
import ru.citeck.ecos.records2.request.rest.RestHandler;
import ru.citeck.ecos.records3.record.resolver.LocalRecordsResolver;
import ru.citeck.ecos.records3.record.resolver.RemoteRecordsResolver;
import ru.citeck.ecos.records2.rest.*;
import ru.citeck.ecos.records2.source.dao.local.meta.MetaRecordsDaoAttsProvider;
import ru.citeck.ecos.records3.rest.RestHandlerAdapter;
import ru.citeck.ecos.records3.txn.RecordsTxnService;

import java.util.function.Supplier;

@Configuration
public class RecordsConfiguration extends RecordsServiceFactory {

    @Value("${records.configuration.app.name}")
    private String appName;

    @Value("${records.configuration.admin.actions.log.enabled}")
    private String isAdminActionsLogEnabled;

    @Autowired
    private ServiceRegistry serviceRegistry;
    @Autowired
    private EcosEurekaClient ecosEurekaClient;
    @Autowired
    private RecordsProperties properties;
    @Autowired(required = false)
    private RecordsResolverWrapper resolverWrapper;
    @Autowired(required = false)
    private TypesManager typeInfoProvider;
    @Autowired
    private TransactionService transactionService;

    @Autowired
    @Qualifier(EurekaContextConfig.REST_TEMPLATE_ID)
    private RestTemplate eurekaRestTemplate;

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
        if (Boolean.parseBoolean(isAdminActionsLogEnabled) && resolverWrapper != null) {
            resolverWrapper.setRecordsResolver(super.createLocalRecordsResolver());
            return resolverWrapper;
        } else {
            return super.createLocalRecordsResolver();
        }
    }

    @Bean
    @Override
    protected RecordEvaluatorService createRecordEvaluatorService() {
        return super.createRecordEvaluatorService();
    }

    @Override
    protected RemoteRecordsResolver createRemoteRecordsResolver() {
        RemoteRecordsRestApi restApi = new RemoteRecordsRestApiImpl(
            this::jsonPost,
            remoteAppInfoProvider(),
            properties
        );
        return new RemoteRecordsResolver(this, restApi);
    }

    private RestResponseEntity jsonPost(String url, RestRequestEntity request) {

        org.springframework.http.HttpHeaders headers = new HttpHeaders();
        request.getHeaders().forEach(headers::put);
        HttpEntity<byte[]> httpEntity = new HttpEntity<>(request.getBody(), headers);

        ResponseEntity<byte[]> result = eurekaRestTemplate.exchange(url, HttpMethod.POST, httpEntity, byte[].class);

        RestResponseEntity resultEntity = new RestResponseEntity();
        resultEntity.setBody(result.getBody());
        resultEntity.setStatus(result.getStatusCode().value());
        result.getHeaders().forEach((k, v) -> resultEntity.getHeaders().put(k, v));

        return resultEntity;
    }

    private RemoteAppInfoProvider remoteAppInfoProvider() {

        return appName -> {

            InstanceInfo instanceInfo = ecosEurekaClient.getInstanceInfo(appName);
            if (instanceInfo == null) {
                return null;
            }

            RemoteAppInfo info = new RemoteAppInfo();
            info.setIp(instanceInfo.getIPAddr());
            info.setHost(instanceInfo.getHostName());
            info.setPort(instanceInfo.getPort());

            info.setRecordsBaseUrl(instanceInfo.getMetadata().get(RestConstants.RECS_BASE_URL_META_KEY));
            info.setRecordsUserBaseUrl(instanceInfo.getMetadata().get(RestConstants.RECS_USER_BASE_URL_META_KEY));

            return info;
        };
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
    protected RecordsMetaService createRecordsMetaService() {
        return super.createRecordsMetaService();
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

    @NotNull
    @Override
    protected RecordsMetaGql createRecordsMetaGql() {
        return super.createRecordsMetaGql();
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

    @Component
    public static class JobsInitializer extends AbstractLifecycleBean {

        @Autowired
        private RecordsServiceFactory serviceFactory;

        @Override
        protected void onBootstrap(ApplicationEvent event) {
            try {
                serviceFactory.initJobs(null);
            } catch (Exception e) {
                log.error("JobsInitializer initialization failed", e);
            }
        }

        @Override
        protected void onShutdown(ApplicationEvent applicationEvent) {
        }
    }
}
