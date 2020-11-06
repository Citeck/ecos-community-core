package ru.citeck.ecos.domain.model.alf.dao;

import ecos.com.google.common.cache.CacheBuilder;
import ecos.com.google.common.cache.CacheLoader;
import ecos.com.google.common.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.dictionary.M2Model;
import org.alfresco.repo.lock.JobLockService;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.AlfrescoTransactionSupport;
import org.alfresco.service.cmr.repository.*;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.transaction.TransactionService;
import org.alfresco.util.transaction.TransactionListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.utils.ExceptionUtils;
import ru.citeck.ecos.commons.utils.NameUtils;
import ru.citeck.ecos.commons.utils.digest.Digest;
import ru.citeck.ecos.commons.utils.digest.DigestAlgorithm;
import ru.citeck.ecos.commons.utils.digest.DigestUtils;
import ru.citeck.ecos.model.EcosAutoModel;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.search.ftsquery.FTSQuery;
import ru.citeck.ecos.utils.NodeUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
public class AlfAutoModelsDao {

    private static final QName MODELS_COUNTER_LOCK_KEY = QName.createQName(
        "", AlfAutoModelsDao.class.getName() + "-models-counter");

    private static final String MODELS_ROOT = "/app:company_home/app:dictionary/cm:ecos-auto-models";
    private static final String MODEL_URI = "http://www.citeck.ru/automodel/%s/1.0";

    private final NodeUtils nodeUtils;
    private final NodeService nodeService;
    private final ContentService contentService;
    private final SearchService searchService;
    private final TransactionService transactionService;
    private final JobLockService jobLockService;

    private final LoadingCache<RecordRef, Optional<QName>> modelQNameByTypeRefCache;

    @Autowired
    public AlfAutoModelsDao(
        NodeUtils nodeUtils,
        NodeService nodeService,
        ContentService contentService,
        SearchService searchService,
        TransactionService transactionService,
        JobLockService jobLockService
    ) {
        this.nodeUtils = nodeUtils;
        this.nodeService = nodeService;
        this.searchService = searchService;
        this.contentService = contentService;
        this.jobLockService = jobLockService;
        this.transactionService = transactionService;

        modelQNameByTypeRefCache = CacheBuilder.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(10, TimeUnit.SECONDS)
            .build(CacheLoader.from(this::getModelQNameByTypeImpl));
    }

    @Nullable
    public QName getModelQNameByType(RecordRef typeRef) {
        return modelQNameByTypeRefCache.getUnchecked(typeRef).orElse(null);
    }

    private Optional<QName> getModelQNameByTypeImpl(RecordRef typeRef) {
        NodeRef nodeRef = getModelRefByTypeRef(typeRef);
        if (nodeRef == null) {
            return Optional.empty();
        }
        String modelPrefix = nodeUtils.getProperty(nodeRef, EcosAutoModel.PROP_MODEL_PREFIX);
        String modelUri = String.format(MODEL_URI, modelPrefix);
        return Optional.of(QName.createQName(modelUri, modelPrefix));
    }

    private synchronized long getModelsCounterValue() {

        String lock = jobLockService.getLock(MODELS_COUNTER_LOCK_KEY, 5_000, 1_000, 3);

        try {

            return transactionService.getRetryingTransactionHelper().doInTransaction(() -> {

                NodeRef modelsRoot = nodeUtils.getNodeRef(MODELS_ROOT);
                Long counterValue = nodeUtils.getProperty(modelsRoot, EcosAutoModel.PROP_MODELS_COUNTER);
                if (counterValue == null) {
                    counterValue = 0L;
                }
                nodeService.setProperty(modelsRoot, EcosAutoModel.PROP_MODELS_COUNTER, counterValue + 1);
                return counterValue;

            }, false, true);

        } finally {
            try {
                jobLockService.releaseLock(lock, MODELS_COUNTER_LOCK_KEY);
            } catch (Exception e) {
                log.debug("Lock release failed. Lock: " + lock + " Key: " + MODELS_COUNTER_LOCK_KEY, e);
            }
        }
    }

    public List<TypeModelInfo> getAllModels() {
        return AuthenticationUtil.runAsSystem(() -> FTSQuery.create()
            .type(EcosAutoModel.TYPE_MODEL_DEF)
            .transactional()
            .unlimited()
            .query(searchService)
            .stream()
            .map(this::readModel)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList()));
    }

    private Optional<TypeModelInfo> readModel(NodeRef nodeRef) {

        if (nodeRef == null) {
            return Optional.empty();
        }

        return AuthenticationUtil.runAsSystem(() -> {

            if (!nodeService.exists(nodeRef)) {
                return Optional.empty();
            }

            ContentReader reader = contentService.getReader(nodeRef, ContentModel.PROP_CONTENT);
            if (reader == null) {
                return Optional.empty();
            }
            try (InputStream in = reader.getContentInputStream()) {

                M2Model model = M2Model.createModel(in);
                String modelPrefix = (String) nodeService.getProperty(nodeRef, EcosAutoModel.PROP_MODEL_PREFIX);

                RecordRef typeRef = RecordRef.valueOf(
                    (String) nodeService.getProperty(nodeRef, EcosAutoModel.PROP_ECOS_TYPE_REF));

                if (model != null) {
                    return Optional.of(new TypeModelInfo(nodeRef, typeRef, modelPrefix, model));
                }
            } catch (Exception e) {
                log.error("Model reading failed. ModelRef: " + nodeRef);
            }
            return Optional.empty();
        });
    }

    @NotNull
    public TypeModelInfo getOrCreateModelByTypeRef(@NotNull RecordRef typeRef) {

        NodeRef modelRef = getModelRefByTypeRef(typeRef);

        if (modelRef == null) {

            String typeRefStr = typeRef.toString();
            Digest digest = DigestUtils.getDigest(typeRefStr.getBytes(StandardCharsets.UTF_8), DigestAlgorithm.MD5);
            String modelPrefix = "p-" + digest.getHash().substring(0, 4).toLowerCase() + "-" + getModelsCounterValue();

            NodeRef parentRef = nodeUtils.getNodeRef(MODELS_ROOT);
            Map<QName, Serializable> props = new HashMap<>();

            String validNodeName = NameUtils.escape(typeRefStr);
            props.put(ContentModel.PROP_NAME, validNodeName + ".xml");
            props.put(EcosAutoModel.PROP_ECOS_TYPE_REF, typeRefStr);
            props.put(EcosAutoModel.PROP_MODEL_PREFIX, modelPrefix);

            QName assocName = QName.createQNameWithValidLocalName(
                NamespaceService.CONTENT_MODEL_1_0_URI, validNodeName);

            modelRef = nodeService.createNode(
                parentRef,
                ContentModel.ASSOC_CONTAINS,
                assocName,
                EcosAutoModel.TYPE_MODEL_DEF,
                props
            ).getChildRef();

            String modelUri = String.format(MODEL_URI, modelPrefix);
            M2Model model = M2Model.createModel(modelPrefix + ":model");
            model.createNamespace(modelUri, modelPrefix);
            model.createImport(NamespaceService.DICTIONARY_MODEL_1_0_URI, "d");
            saveModelInfo(modelRef, model);

            AlfrescoTransactionSupport.bindListener(new TransactionListenerAdapter() {
                @Override
                public void afterCommit() {
                    modelQNameByTypeRefCache.invalidate(typeRef);
                }
            });

            modelQNameByTypeRefCache.invalidate(typeRef);
        }

        NodeRef finalModelRef = modelRef;

        return readModel(modelRef)
            .orElseThrow(() -> new IllegalStateException(
                "Model read failed for type " + typeRef + " ModelRef: " + finalModelRef));
    }

    @Nullable
    private NodeRef getModelRefByTypeRef(RecordRef typeRef) {
        return AuthenticationUtil.runAsSystem(() -> FTSQuery.create()
            .type(EcosAutoModel.TYPE_MODEL_DEF).and()
            .exact(EcosAutoModel.PROP_ECOS_TYPE_REF, typeRef.toString())
            .transactional()
            .queryOne(searchService)
            .orElse(null));
    }

    public void save(TypeModelInfo model) {
        AuthenticationUtil.runAsSystem(() -> {
            saveModelInfo(model.getNodeRef(), model.getModel());
            return null;
        });
    }

    private void saveModelInfo(NodeRef nodeRef, M2Model model) {
        ContentWriter writer = contentService.getWriter(nodeRef, ContentModel.PROP_CONTENT, true);
        try (OutputStream out = writer.getContentOutputStream()) {
            model.toXML(out);
        } catch (Exception e) {
            ExceptionUtils.throwException(e);
        }
    }
}
