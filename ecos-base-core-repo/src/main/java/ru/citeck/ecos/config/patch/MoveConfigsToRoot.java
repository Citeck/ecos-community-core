package ru.citeck.ecos.config.patch;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.module.AbstractModuleComponent;
import org.alfresco.repo.transaction.AlfrescoTransactionSupport;
import org.alfresco.repo.transaction.RetryingTransactionHelper;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.transaction.TransactionService;
import org.alfresco.util.transaction.TransactionListenerAdapter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import ru.citeck.ecos.action.group.GroupActionConfig;
import ru.citeck.ecos.action.group.GroupActionService;
import ru.citeck.ecos.config.EcosConfigService;
import ru.citeck.ecos.model.ConfigModel;
import ru.citeck.ecos.records.RecordsUtils;
import ru.citeck.ecos.records2.IterableRecords;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records2.request.query.QueryConsistency;
import ru.citeck.ecos.records2.request.query.RecordsQuery;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class MoveConfigsToRoot extends AbstractModuleComponent {

    private static final Log logger = LogFactory.getLog(MoveConfigsToRoot.class);

    private GroupActionService groupActionService;
    private TransactionService transactionService;
    private EcosConfigService ecosConfigService;
    private RecordsService recordsService;
    private NodeService nodeService;

    @Override
    protected void executeInternal() {

        RetryingTransactionHelper txnHelper = transactionService.getRetryingTransactionHelper();

        AlfrescoTransactionSupport.bindListener(new TransactionListenerAdapter() {
            @Override
            public void afterCommit() {
                txnHelper.doInTransaction(() -> updateConfigs());
            }
        });
    }

    private boolean updateConfigs() {

        RecordsQuery query = new RecordsQuery();
        query.setLanguage(SearchService.LANGUAGE_CMIS_STRICT);
        query.setConsistency(QueryConsistency.TRANSACTIONAL);
        query.setQuery("select * from config:ecosConfig");
        Iterable<RecordRef> ecosConfigs = new IterableRecords(recordsService, query);

        GroupActionConfig config = new GroupActionConfig();
        config.setBatchSize(10);
        config.setAsync(false);
        config.setMaxResults(0);
        config.setErrorsLimit(0);

        logger.info("Start " + getClass());

        AtomicInteger fixed = new AtomicInteger(0);
        AtomicInteger skipped = new AtomicInteger(0);
        AtomicInteger total = new AtomicInteger(0);

        NodeRef configsRoot = ecosConfigService.getConfigRoot();

        groupActionService.execute(ecosConfigs, recordRef -> {

            total.incrementAndGet();

            NodeRef configRef = RecordsUtils.toNodeRef(recordRef);
            if (!nodeService.exists(configRef)) {
                logger.error("Config node doesn't exists: " + recordRef);
                return;
            }

            ChildAssociationRef parentAssoc = nodeService.getPrimaryParent(configRef);
            if (configsRoot.equals(parentAssoc.getParentRef())) {
                skipped.incrementAndGet();
            } else {

                String key = (String) nodeService.getProperty(configRef, ConfigModel.PROP_KEY);

                Optional<NodeRef> configInRoot = findConfig(configsRoot, key);

                if (configInRoot.isPresent()) {

                    mergeConfigs(configInRoot.get(), configRef);

                } else {

                    String baseName = (String) nodeService.getProperty(configRef, ContentModel.PROP_NAME);
                    String name = baseName;

                    NodeRef configWithSameName = nodeService.getChildByName(configsRoot, ContentModel.ASSOC_CONTAINS, name);
                    int idx = 1;
                    while (configWithSameName != null) {
                        name = baseName + " (" + idx + ")";
                        configWithSameName = nodeService.getChildByName(configsRoot, ContentModel.ASSOC_CONTAINS, name);
                        idx++;
                    }

                    if (!baseName.equals(name)) {
                        nodeService.setProperty(configRef, ContentModel.PROP_NAME, name);
                    }

                    QName assocQName = QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, QName.createValidLocalName(key));
                    nodeService.moveNode(configRef, configsRoot, ContentModel.ASSOC_CONTAINS, assocQName);
                }
                fixed.incrementAndGet();
            }
        }, config);

        logger.info("Stop " + getClass() +
                ". Fixed: " + fixed.get() +
                " Skipped: " + skipped.get() +
                " Total: " + total);

        return true;
    }

    private void mergeConfigs(NodeRef to, NodeRef from) {

        if (to.equals(from)) {
            return;
        }

        Serializable toValue = nodeService.getProperty(to, ConfigModel.PROP_VALUE);
        Serializable fromValue = nodeService.getProperty(from, ConfigModel.PROP_VALUE);

        if (!Objects.equals(toValue, fromValue)) {
            logger.info("Change config value of " + to +
                    " from " + fromValue + " to " + toValue);

            nodeService.setProperty(to, ConfigModel.PROP_VALUE, fromValue);
        }

        Serializable key = nodeService.getProperty(from, ConfigModel.PROP_KEY);

        logger.info("Delete unnecessary config node: " + from +
                " with key " + key + " and value " + fromValue);
        nodeService.deleteNode(from);
    }

    private Optional<NodeRef> findConfig(NodeRef root, String key) {
        List<ChildAssociationRef> configs;
        configs = nodeService.getChildAssocsByPropertyValue(root, ConfigModel.PROP_KEY, key);
        return configs.stream().map(ChildAssociationRef::getChildRef).findFirst();
    }

    @Autowired
    @Qualifier("ecosConfigService")
    public void setEcosConfigService(EcosConfigService ecosConfigService) {
        this.ecosConfigService = ecosConfigService;
    }

    @Autowired
    public void setGroupActionService(GroupActionService groupActionService) {
        this.groupActionService = groupActionService;
    }

    @Autowired
    public void setRecordsService(RecordsService recordsService) {
        this.recordsService = recordsService;
    }

    @Autowired
    public void setTransactionService(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @Autowired
    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        super.setServiceRegistry(serviceRegistry);
        nodeService = serviceRegistry.getNodeService();
    }
}
