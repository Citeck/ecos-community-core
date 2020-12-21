package ru.citeck.ecos.behavior.status;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.repo.admin.RepositoryState;
import org.alfresco.repo.batch.BatchProcessWorkProvider;
import org.alfresco.repo.batch.BatchProcessor;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.RetryingTransactionHelper;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.search.ResultSet;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.transaction.TransactionService;
import org.apache.commons.logging.LogFactory;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.apache.commons.logging.Log;
import ru.citeck.ecos.icase.CaseStatusService;
import ru.citeck.ecos.job.AbstractLockedJob;
import ru.citeck.ecos.service.AlfrescoServices;
import ru.citeck.ecos.service.EcosCoreServices;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Job to find nodes in specified status and change it if current date more or equals to data stored in specified field
 *
 * @author Pavel Simonov
 */
public class ChangeStatusByDate extends AbstractLockedJob {

    private static final int WORKER_THREADS = 1;
    private static final int BATCH_SIZE = 10;
    private static final int LOGGING_INTERVAL = 50;

    private static final Log logger = LogFactory.getLog(ChangeStatusByDate.class);

    private static final String SEARCH_QUERY = "TYPE:\"%s\" AND @%s:[MIN TO NOW] AND " +
        "(@icase\\:caseStatusAssoc_added:\"%s\" OR =icase\\:caseStatusAssoc-prop:\"%s\")";

    @Override
    public void executeJob(JobExecutionContext context) throws JobExecutionException {
        final JobDataMap data = context.getJobDetail().getJobDataMap();
        AuthenticationUtil.runAsSystem((AuthenticationUtil.RunAsWork<Void>) () -> {
            final ServiceRegistry serviceRegistry = (ServiceRegistry) data.get("serviceRegistry");
            RepositoryState repositoryState = (RepositoryState) serviceRegistry.getService(AlfrescoServices.REPOSITORY_STATE);
            if (repositoryState.isBootstrapping()) {
                return null;
            }
            @SuppressWarnings("unchecked")
            final List<String> transitions = (List) data.get("transitions");
            for (String transition : transitions) {
                makeTransition(serviceRegistry, transition);
            }
            return null;
        });
    }

    private void makeTransition(ServiceRegistry serviceRegistry, String transitionData) {
        SearchService searchService = serviceRegistry.getSearchService();
        NamespaceService namespaceService = serviceRegistry.getNamespaceService();
        CaseStatusService caseStatusService = EcosCoreServices.getCaseStatusService(serviceRegistry);

        Transition transition = new Transition(transitionData, namespaceService);
        List<NodeRef> nodeRefs = findNodes(caseStatusService, searchService, transition);

        logger.info("Transition '" + transitionData + "' nodes to change: " + nodeRefs.size());
        if (!nodeRefs.isEmpty()) {

            TransactionService transactionService = serviceRegistry.getTransactionService();
            RetryingTransactionHelper retryingTransactionHelper = transactionService.getRetryingTransactionHelper();

            String processName = "Change status '" + transitionData + "'";
            BatchProcessor<NodeRef> batchProcessor = new BatchProcessor<>(
                    processName,
                    retryingTransactionHelper,
                    new ChangeStatusWorkProvider(nodeRefs),
                    WORKER_THREADS, BATCH_SIZE,
                    null, logger, LOGGING_INTERVAL
            );

            batchProcessor.process(new ChangeStatusWorker(caseStatusService, transition.toStatus), true);
        }
    }

    private List<NodeRef> findNodes(CaseStatusService caseStatusService, SearchService searchService, Transition transition) {
        NodeRef statusRef = getStatus(caseStatusService, transition.fromStatus);
        String query = String.format(SEARCH_QUERY, transition.className, transition.fieldName, statusRef, transition.fromStatus);
        ResultSet results = searchService.query(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE,
                                                SearchService.LANGUAGE_FTS_ALFRESCO, query);

        if (results != null) {
            return filterByStatus(results.getNodeRefs(), statusRef, caseStatusService);
        } else {
            return Collections.emptyList();
        }
    }

    private List<NodeRef> filterByStatus(List<NodeRef> nodeRefs, NodeRef statusRef, CaseStatusService caseStatusService) {
        List<NodeRef> result = new ArrayList<>(nodeRefs.size());
        for (NodeRef nodeRef : nodeRefs) {
            NodeRef caseStatusRef = caseStatusService.getStatusRef(nodeRef);
            if (statusRef.equals(caseStatusRef)) {
                result.add(nodeRef);
            }
        }
        return result;
    }

    private NodeRef getStatus(CaseStatusService caseStatusService, String name) {
        NodeRef statusRef = caseStatusService.getStatusByName(name);
        if (statusRef == null) {
            throw new AlfrescoRuntimeException("Status not found: " + name);
        }
        return statusRef;
    }

    private static class ChangeStatusWorker extends BatchProcessor.BatchProcessWorkerAdaptor<NodeRef> {

        private CaseStatusService caseStatusService;
        private String toStatus;

        ChangeStatusWorker(CaseStatusService caseStatusService, String toStatus) {
            this.caseStatusService = caseStatusService;
            this.toStatus = toStatus;
        }

        @Override
        public void process(NodeRef entry) throws Throwable {
            caseStatusService.setStatus(entry, toStatus);
        }
    }

    private static class ChangeStatusWorkProvider implements BatchProcessWorkProvider<NodeRef> {

        private Collection<NodeRef> nodeRefs;
        private boolean hasMore = true;

        ChangeStatusWorkProvider(Collection<NodeRef> nodeRefs) {
            this.nodeRefs = nodeRefs;
        }

        @Override
        public int getTotalEstimatedWorkSize() {
            return nodeRefs.size();
        }

        @Override
        public Collection<NodeRef> getNextWork() {
            if (hasMore) {
                hasMore = false;
                return nodeRefs;
            } else {
                return Collections.emptyList();
            }
        }
    }

    private static class Transition {
        final QName className;
        final QName fieldName;
        final String fromStatus;
        final String toStatus;

        Transition(String data, NamespaceService namespaceService) {
            String[] tokens = data.split(",");
            if (tokens.length != 4) {
                throw new IllegalArgumentException("Transition description must contains 4 tokens " +
                                                   "delimited with ','. Actual description: '" + data + "'");
            }
            className = QName.resolveToQName(namespaceService, tokens[0]);
            fieldName = QName.resolveToQName(namespaceService, tokens[1]);
            fromStatus = tokens[2];
            toStatus = tokens[3];
        }
    }
}
