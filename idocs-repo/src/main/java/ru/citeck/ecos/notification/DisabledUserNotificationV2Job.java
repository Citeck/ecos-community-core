package ru.citeck.ecos.notification;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.RetryingTransactionHelper;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.transaction.TransactionService;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import ru.citeck.ecos.icase.CaseStatusService;
import ru.citeck.ecos.job.AbstractLockedJob;
import ru.citeck.ecos.records.source.alf.AlfNodesRecordsDAO;
import ru.citeck.ecos.records2.IterableRecords;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records2.request.query.RecordsQuery;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Slf4j
public class DisabledUserNotificationV2Job extends AbstractLockedJob {

    private RecordsService recordsService;
    private CaseStatusService caseStatusService;
    private RetryingTransactionHelper transactionHelper;
    private ICaseDocumentNotificationSender sender;

    private static final String PARAM_RECORDS_SERVICE = "recordsService";
    private static final String PARAM_CASE_STATUS_SERVICE = "caseStatusService";
    private static final String PARAM_TRANSACTION_SERVICE = "transactionService";
    private static final String PARAM_NOTIFICATION_SENDER = "notificationSender";

    private static final String PARAM_DOCUMENT_TYPES = "documentTypes";
    private static final String PARAM_DOCUMENT_STATUSES = "documentStatuses";
    private static final String PARAM_SUBJECT_TEMPLATE = "subjectTemplate";
    private static final String PARAM_RECIPIENTS = "recipients";
    private static final String PARAM_ASPECT_CONDITION = "aspectCondition";
    private static final String PARAM_VERIFY_PERSON_ASSOC = "verifyPerson";
    private static final String PARAM_NOTIFICATION_TYPE = "notificationType";
    private static final String INCLUDE_KEY = "include";
    private static final String EXCLUDE_KEY = "exclude";

    private static final String QUERY_DISABLED_USERS_TEMPLATE =
            "TYPE:\"cm:person\" AND " +
                    "(ecos:isPersonDisabled:true OR ASPECT:\"cm:personDisabled\") AND " +
                    "cm:modified:[NOW-6MONTH TO NOW]";

    @Override
    public void executeJob(final JobExecutionContext context) throws JobExecutionException {
        final JobDataMap data = context.getJobDetail().getJobDataMap();

        recordsService = (RecordsService) data.get(PARAM_RECORDS_SERVICE);
        caseStatusService = (CaseStatusService) data.get(PARAM_CASE_STATUS_SERVICE);
        TransactionService transactionService = (TransactionService) data.get(PARAM_TRANSACTION_SERVICE);
        transactionHelper = transactionService.getRetryingTransactionHelper();
        sender = (ICaseDocumentNotificationSender) data.get(PARAM_NOTIFICATION_SENDER);

        AuthenticationUtil.runAsSystem(() -> {
            log.info("Start DisabledUserNotificationV2Job");
            doJob(data);
            log.info("Finished DisabledUserNotificationV2Job");
            return null;
        });
    }

    private void doJob(JobDataMap data) {
        final String verifyPerson = (String) data.get(PARAM_VERIFY_PERSON_ASSOC);
        final String notificationType = (String) data.get(PARAM_NOTIFICATION_TYPE);
        final String subjectTemplate = (String) data.get(PARAM_SUBJECT_TEMPLATE);

        final List<String> documentTypes = (List<String>) data.get(PARAM_DOCUMENT_TYPES);

        final Map<String, List<String>> recipients = (Map<String, List<String>>) data.get(PARAM_RECIPIENTS);
        final Map<String, List<String>> aspectCondition = (Map<String, List<String>>) data.get(PARAM_ASPECT_CONDITION);
        final Map<String, List<String>> documentStatuses = (Map<String, List<String>>) data.get(PARAM_DOCUMENT_STATUSES);

        if (verifyPerson == null || verifyPerson.equals("") ||
                CollectionUtils.isEmpty(documentTypes) || MapUtils.isEmpty(recipients)) {
            log.error("Cannot start job, mandatory params is empty. " +
                    PARAM_VERIFY_PERSON_ASSOC + " = " + verifyPerson + ", " +
                    PARAM_DOCUMENT_TYPES + " = " + documentTypes + ", " +
                    PARAM_RECIPIENTS + " = " + recipients + ".");
            return;
        }

        Iterable<RecordRef> disabledUsers = searchNodesByQuery(QUERY_DISABLED_USERS_TEMPLATE);

        if (!disabledUsers.iterator().hasNext()) {
            log.info("Disabled users not found.");
            return;
        }

        if (log.isDebugEnabled()) {
            Iterator<RecordRef> iterator = disabledUsers.iterator();
            int countDisabledUsers = 0;
            while (iterator.hasNext()) {
                iterator.next();
                countDisabledUsers++;
            }
            log.debug("Found " + countDisabledUsers + " disabled users.");
        }

        List<String> includeStatuses = null;
        List<String> excludeStatuses = null;
        if (MapUtils.isNotEmpty(documentStatuses)) {
            includeStatuses = documentStatuses.get(INCLUDE_KEY);
            excludeStatuses = documentStatuses.get(EXCLUDE_KEY);
        }
        boolean shouldCheckStatus =
                (CollectionUtils.isNotEmpty(includeStatuses) || CollectionUtils.isNotEmpty(excludeStatuses));

        int usersCount = 0;
        int allDocsCount = 0;

        for (RecordRef disabledUserRecordRef : disabledUsers) {
            NodeRef disabledUserRef = new NodeRef(disabledUserRecordRef.getId());

            for (String documentType : documentTypes) {
                String query = generateQueryByDocumentTypeAndDisabledUser(
                        documentType,
                        verifyPerson,
                        disabledUserRef,
                        aspectCondition
                );

                Iterable<RecordRef> documents = searchNodesByQuery(query);
                int userAndTypeDocsCount = 0;

                for (RecordRef documentRecordRef : documents) {
                    NodeRef documentRef = new NodeRef(documentRecordRef.getId());
                    if (shouldCheckStatus &&
                            !checkStatusInDocument(documentRef, includeStatuses, excludeStatuses, caseStatusService)) {
                        continue;
                    }

                    userAndTypeDocsCount++;
                    allDocsCount++;

                    sendNotificationInTransaction(
                            documentRef,
                            disabledUserRef,
                            recipients,
                            notificationType,
                            subjectTemplate,
                            sender
                    );

                    if (log.isDebugEnabled()) {
                        log.debug("Processed " + userAndTypeDocsCount +
                                " of type " + documentType +
                                " and user " + disabledUserRef + "."
                        );
                    }
                }
            }
            usersCount++;
        }

        log.info("Processed " + allDocsCount + " for " + usersCount + " users.");
    }

    private Iterable<RecordRef> searchNodesByQuery(String query) {

        RecordsQuery recordsQuery = new RecordsQuery();
        recordsQuery.setMaxItems(0);
        recordsQuery.setLanguage(SearchService.LANGUAGE_FTS_ALFRESCO);
        recordsQuery.setQuery(query);
        recordsQuery.setSourceId(AlfNodesRecordsDAO.ID);

        return new IterableRecords(recordsService, recordsQuery);
    }

    private String generateQueryByDocumentTypeAndDisabledUser(
            String documentType,
            String verifyPerson,
            NodeRef disabledUser,
            Map<String, List<String>> aspectCondition
    ) {

        StringBuilder query = new StringBuilder();
        query.append("TYPE:\"").append(documentType).append("\"");
        query.append(" AND ").append(verifyPerson).append("_added:\"").append(disabledUser.toString()).append("\"");
        fillAspectCondition(aspectCondition, query);

        return query.toString();
    }

    private void fillAspectCondition(Map<String, List<String>> aspectCondition, StringBuilder query) {
        if (MapUtils.isEmpty(aspectCondition)) {
            return;
        }

        List<String> includeAspects = aspectCondition.get(INCLUDE_KEY);
        List<String> excludeAspects = aspectCondition.get(EXCLUDE_KEY);

        if (CollectionUtils.isNotEmpty(includeAspects)) {
            for (String aspect : includeAspects) {
                query.append(" AND ASPECT:\"").append(aspect).append("\"");
            }
        }

        if (CollectionUtils.isNotEmpty(excludeAspects)) {
            for (String aspect : excludeAspects) {
                query.append(" AND NOT ASPECT:\"").append(aspect).append("\"");
            }
        }

    }

    private boolean checkStatusInDocument(NodeRef documentRef,
                                          List<String> includeStatuses,
                                          List<String> excludeStatuses,
                                          CaseStatusService caseStatusService
    ) {

        if (CollectionUtils.isNotEmpty(includeStatuses)) {
            return includeStatuses.contains(caseStatusService.getStatus(documentRef));
        }

        if (CollectionUtils.isNotEmpty(excludeStatuses)) {
            return !excludeStatuses.contains(caseStatusService.getStatus(documentRef));
        }

        return true;
    }

    private void sendNotificationInTransaction(
            NodeRef documentRef,
            NodeRef verifyUserRef,
            Map<String, List<String>> recipients,
            String notificationType,
            String subjectTemplate,
            ICaseDocumentNotificationSender sender
    ) {
        transactionHelper.doInTransaction(() -> {
            sender.sendNotification(
                    documentRef,
                    verifyUserRef,
                    recipients,
                    notificationType,
                    subjectTemplate);
            return null;
        }, true, true);
    }
}
