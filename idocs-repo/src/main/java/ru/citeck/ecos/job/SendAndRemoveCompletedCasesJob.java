package ru.citeck.ecos.job;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.search.ResultSet;
import org.alfresco.service.cmr.search.SearchParameters;
import org.alfresco.service.cmr.search.SearchService;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.springframework.util.StopWatch;
import ru.citeck.ecos.cases.RemoteCaseModelService;
import ru.citeck.ecos.config.EcosConfigService;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.ForkJoinPool;

/**
 * Send and remove completed cases job
 */
@Slf4j
public class SendAndRemoveCompletedCasesJob extends AbstractLockedJob {

    /**
     * Constants
     */
    private static final Integer MAX_ITEMS_COUNT = 50;
    private static final String MAX_ITEMS_COUNT_PROPERTY = "citeck.remote.case.service.max.items";
    private static final String THREADS_COUNT_PROPERTY = "completed-cases-job-threads";

    /**
     * Store reference
     */
    private StoreRef storeRef;

    /**
     * Search service
     */
    private SearchService searchService;

    /**
     * Node service
     */
    private RemoteCaseModelService remoteCaseModelService;

    /**
     * Ecos Config Service
     */
    private EcosConfigService ecosConfigService;

    /**
     * Global properties
     */
    private Properties globalProperties;

    /**
     * Init job service
     *
     * @param jobDataMap Job data map
     */
    private void init(JobDataMap jobDataMap) {
        searchService = (SearchService) jobDataMap.get("searchService");
        remoteCaseModelService = (RemoteCaseModelService) jobDataMap.get("remoteCaseModelService");
        globalProperties = (Properties) jobDataMap.get("global-properties");
        ecosConfigService = (EcosConfigService) jobDataMap.get("ecosConfigService");
        storeRef = StoreRef.STORE_REF_WORKSPACE_SPACESSTORE;
    }

    /**
     * This is the method that should be implemented by any extension of the
     * abstract class. It won't need to worry about any lockings of the job and
     * can focus only on its specific task.
     *
     * @param jobContext context of the execution for retrieving services, etc
     */
    @Override
    public void executeJob(JobExecutionContext jobContext) {
        init(jobContext.getJobDetail().getJobDataMap());
        AuthenticationUtil.runAsSystem(() -> {
            log.info("SendToRemoteCompletedCasesJob started");

            StopWatch watch = new StopWatch(SendAndRemoveCompletedCasesJob.class.getName());

            runWatch(watch, "SendToRemote candidates search");
            ResultSet resultSet = getDocumentsResultSet();
            watch.stop();

            List<NodeRef> documents = resultSet.getNodeRefs();

            runWatch(watch, "Documents processing in parallel stream");
            String threadsCountStr = (String) ecosConfigService.getParamValue(THREADS_COUNT_PROPERTY);
            int threadsCount = Integer.parseInt(threadsCountStr);
            ForkJoinPool customThreadPool = new ForkJoinPool(threadsCount);
            // Use .get() to force wait for threads completion
            customThreadPool.submit(() ->
                documents.parallelStream()
                    .forEach(documentRef -> remoteCaseModelService.sendAndRemoveCaseModelsByDocument(documentRef))
            ).get();
            watch.stop();

            log.debug(watch.prettyPrint());
            log.info("SendToRemoteCompletedCasesJob stopped");

            return null;
        });
    }

    /**
     * Get documents by offset
     *
     * @return Set of documents
     */
    private ResultSet getDocumentsResultSet() {
        SearchParameters parameters = new SearchParameters();
        parameters.addStore(storeRef);
        parameters.setLanguage(SearchService.LANGUAGE_LUCENE);
        parameters.setQuery("TYPE:\"idocs:doc\" " +
            "AND NOT ASPECT:\"icaseEproc:hasProcessId\" " +
            "AND @idocs\\:caseCompleted:true " +
            "AND -@idocs\\:caseModelsSent:true");
        parameters.addSort("@cm:created", true);
        parameters.setMaxItems(getMaxItemsCount());
        return searchService.query(parameters);
    }

    /**
     * Get max items count
     *
     * @return Max items count
     */
    private Integer getMaxItemsCount() {
        if (globalProperties == null) {
            return MAX_ITEMS_COUNT;
        }
        String maxItemsRawValue = globalProperties.getProperty(MAX_ITEMS_COUNT_PROPERTY);
        if (maxItemsRawValue == null) {
            return MAX_ITEMS_COUNT;
        } else {
            return Integer.valueOf(maxItemsRawValue);
        }
    }

    private void runWatch(StopWatch stopWatch, String taskName) {
        if (!stopWatch.isRunning()) {
            stopWatch.start(taskName);
        }
    }

}
