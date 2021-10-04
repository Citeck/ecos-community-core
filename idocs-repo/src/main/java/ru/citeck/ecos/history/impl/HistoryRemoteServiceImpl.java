package ru.citeck.ecos.history.impl;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.repo.policy.BehaviourFilter;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.AlfrescoTransactionSupport.TxnReadState;
import org.alfresco.repo.transaction.RetryingTransactionHelper;
import org.alfresco.service.cmr.repository.AssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.transaction.TransactionService;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import ru.citeck.ecos.constants.DocumentHistoryConstants;
import ru.citeck.ecos.history.HistoryRemoteService;
import ru.citeck.ecos.model.ActivityModel;
import ru.citeck.ecos.model.HistoryModel;
import ru.citeck.ecos.model.IdocsModel;
import ru.citeck.ecos.utils.NodeUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

import static org.alfresco.repo.transaction.AlfrescoTransactionSupport.getTransactionReadState;

/**
 * History remote service
 */
@Slf4j
public class HistoryRemoteServiceImpl implements HistoryRemoteService {

    /**
     * JSON constants
     */
    private static final String USERNAME = "username";
    private static final String USER_ID = "userId";
    private static final String INITIATOR = "initiator";
    private static final String WORKFLOW_INSTANCE_ID = "workflowInstanceId";
    private static final String WORKFLOW_DESCRIPTION = "workflowDescription";
    private static final String TASK_EVENT_INSTANCE_ID = "taskEventInstanceId";
    private static final String PROPERTY_NAME = "propertyName";
    private static final String FULL_TASK_TYPE = "fullTaskType";
    private static final String EXPECTED_PERFORM_TIME = "expectedPerformTime";

    /**
     * Constants
     */
    private static final String[] KEYS = {
        "historyEventId", "documentId", "eventType", "comments", "version", "creationTime", "username", "userId",
        "taskRole", "taskOutcome", "taskType", "fullTaskType", "initiator", "workflowInstanceId", "workflowDescription",
        "taskEventInstanceId", "documentVersion", "propertyName", "expectedPerformTime", "taskTitle", "taskDefinitionKey"
    };
    private static final String HISTORY_RECORD_FILE_NAME = "history_record";
    private static final String DELIMITER = "||";
    private static final FastDateFormat DATE_FORMAT = FastDateFormat.getInstance("yyyy_MM_dd_HH_mm_ss");
    private static final FastDateFormat IMPORT_DATE_FORMAT = FastDateFormat.getInstance("dd.MM.yyyy HH:mm:ss");
    private static final String SEND_NEW_RECORD_QUEUE = "send_new_record_queue";
    private static final String SEND_NEW_RECORDS_QUEUE = "send_new_records_queue";
    private static final String DELETE_RECORDS_BY_DOCUMENT_QUEUE = "delete_records_by_document_queue";
    private static final String DEFAULT_RESULT_CSV_FOLDER = "/citeck/ecos/history_record_csv/";

    /**
     * Use active mq
     */
    private static final String USE_ACTIVE_MQ = "ecos.citeck.history.service.use.activemq";
    private static final String CSV_RESULT_FOLDER = "ecos.citeck.history.service.csv.folder";
    private static final String HISTORY_SERVICE_HOST = "ecos.citeck.history.service.host";

    /**
     * Path constants
     */
    private static final String GET_ALL_RECORDS = "/history_records/all_records/page/%d/limit/%d";
    private static final String GET_BY_DOCUMENT_ID_PATH = "/history_records/get_document_history/";
    private static final String DELETE_BY_DOCUMENT_ID_PATH = "/history_records/delete_document_history/";
    private static final String INSERT_RECORD_PATH = "/history_records/insert_record";
    private static final String INSERT_RECORDS_PATH = "/history_records/insert_records";

    private static final String GET_BY_USERNAME = "/history_records/by_username/%s/limit/%d";
    private static final String GET_BY_USERNAME_START_DATE = "/history_records/by_username/%s/start_date/%s/limit/%d";
    private static final String GET_BY_USERNAME_START_END_DATE = "/history_records/by_username/%s/start_date/%s/end_date/%s/limit/%d";


    /**
     * Global properties
     */
    @Autowired
    @Qualifier("global-properties")
    private Properties properties;

    /**
     * Services
     */
    private RestTemplate restTemplate;
    private TransactionService transactionService;
    private NodeService nodeService;
    private PersonService personService;
    private BehaviourFilter behaviourFilter;
    private NodeUtils nodeUtils;

    @Autowired(required = false)
    @Qualifier("historyRabbitTemplate")
    private RabbitTemplate rabbitTemplate;

    @Autowired(required = false)
    private JmsTemplate jmsTemplate;

    /**
     * Get all history records
     *
     * @param page  - Page to show
     * @param limit - Results limit
     * @return List of maps
     */
    @Override
    public List<Map> getAllHistoryRecords(int page, int limit) {
        String url = String.format(GET_ALL_RECORDS, page, limit);
        return restTemplate.getForObject(properties.getProperty(HISTORY_SERVICE_HOST) + url, List.class);
    }

    /**
     * Get history records
     *
     * @param documentUuid Document uuid
     * @return List of maps
     */
    @Override
    public List<Map> getHistoryRecords(String documentUuid) {
        return restTemplate.postForObject(
            properties.getProperty(HISTORY_SERVICE_HOST) + GET_BY_DOCUMENT_ID_PATH,
            documentUuid,
            List.class);
    }

    /**
     * Get history records by username
     *
     * @param username Username
     * @return List of maps
     */
    public List<Map> getHistoryRecordsByUsernameWithDateLimit(String username, Integer limit) {
        return getHistoryRecordsByUsernameWithDateLimit(username, null, null, limit);
    }

    /**
     * Get history records by username. Supports filtering by start date
     *
     * @param username  Username
     * @param startDate Start date. May be null or empty string
     * @return List of maps
     */
    public List<Map> getHistoryRecordsByUsernameWithDateLimit(String username, Date startDate, Integer limit) {
        return getHistoryRecordsByUsernameWithDateLimit(username, startDate, null, limit);
    }

    /**
     * Get history records by username. Supports filtering by start and end date
     *
     * @param username  Username
     * @param startDate Start date. May be null or empty string
     * @param endDate   End date. May be null or empty string
     * @return List of maps
     */
    public List<Map> getHistoryRecordsByUsernameWithDateLimit(String username, Date startDate, Date endDate, Integer limit) {
        if (StringUtils.isBlank(username)) {
            return Collections.emptyList();
        }

        String url = String.format(GET_BY_USERNAME, username, limit);
        if (startDate != null) {
            url = String.format(GET_BY_USERNAME_START_DATE, username, startDate.getTime(), limit);
        }

        if (startDate != null && endDate != null) {
            url = String.format(GET_BY_USERNAME_START_END_DATE, username, startDate.getTime(), endDate.getTime(), limit);
        }

        return restTemplate.getForObject(properties.getProperty(HISTORY_SERVICE_HOST) + url, List.class);
    }

    /**
     * Send history event to remote service
     *
     * @param requestParams Request params
     */
    @Override
    public void sendHistoryEventToRemoteService(Map<String, Object> requestParams) {
        try {
            if (useActiveMq()) {
                if (rabbitTemplate != null) {
                    rabbitTemplate.convertAndSend(SEND_NEW_RECORD_QUEUE, convertMapToJsonString(requestParams));
                }
                if (jmsTemplate != null) {
                    jmsTemplate.convertAndSend(SEND_NEW_RECORD_QUEUE, convertMapToJsonString(requestParams));
                }
                if (rabbitTemplate == null && jmsTemplate == null) {
                    saveHistoryRecordAsCsv(requestParams);
                }
            } else {
                MultiValueMap<String, Object> paramsMap = new LinkedMultiValueMap();
                paramsMap.setAll(requestParams);
                restTemplate.postForObject(properties.getProperty(HISTORY_SERVICE_HOST) + INSERT_RECORD_PATH, paramsMap, Boolean.class);
            }

        } catch (Exception exception) {
            log.error(exception.getMessage(), exception);
            saveHistoryRecordAsCsv(requestParams);
        }
    }

    /**
     * Send history events to remote service by document reference
     *
     * @param documentRef Document reference
     */
    @Override
    public void sendHistoryEventsByDocumentToRemoteService(NodeRef documentRef) {
        /* Loaf associations */
        List<AssociationRef> associations = nodeService.getSourceAssocs(documentRef, HistoryModel.ASSOC_DOCUMENT);
        List<Map<String, Object>> result = new ArrayList<>(associations.size());
        /* Create entries */
        for (AssociationRef associationRef : associations) {
            NodeRef eventRef = associationRef.getSourceRef();
            Map<String, Object> entryMap = getEventMap(eventRef, documentRef);
            result.add(entryMap);
        }
        /* Send data */
        try {
            if (useActiveMq()) {
                if (rabbitTemplate != null) {
                    rabbitTemplate.convertAndSend(SEND_NEW_RECORDS_QUEUE, convertListOfMapsToJsonString(result));
                }
                if (jmsTemplate != null) {
                    jmsTemplate.convertAndSend(SEND_NEW_RECORDS_QUEUE, convertListOfMapsToJsonString(result));
                }
            } else {
                MultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
                map.add("records", convertListOfMapsToJsonString(result));
                restTemplate.postForObject(properties.getProperty(HISTORY_SERVICE_HOST) + INSERT_RECORDS_PATH, map, Boolean.class);
            }
            /* Update document status */
            updateDocumentHistoryStatus(documentRef, true);
        } catch (Exception exception) {
            log.error(exception.getMessage(), exception);
        }
    }

    /**
     * Send history event to remote service by event reference
     *
     * @param eventRef Event reference
     */
    public void sendHistoryEventToRemoteService(NodeRef eventRef) {
        List<AssociationRef> associations = nodeService.getTargetAssocs(eventRef, HistoryModel.ASSOC_DOCUMENT);
        NodeRef documentRef = null;
        if (!associations.isEmpty()) {
            documentRef = associations.get(0).getTargetRef();
        }
        if (documentRef == null) {
            return;
        }
        Map<String, Object> requestParams = getEventMap(eventRef, documentRef);
        sendHistoryEventToRemoteService(requestParams);
    }

    /**
     * Get event request params
     *
     * @param eventRef    Event reference
     * @param documentRef Document reference
     * @return Event request params
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getEventMap(NodeRef eventRef, NodeRef documentRef) {
        Map<String, Object> entryMap = new HashMap<>();
        entryMap.put(DocumentHistoryConstants.DOCUMENT_ID.getValue(), documentRef.getId());
        entryMap.put(DocumentHistoryConstants.NODE_REF.getValue(), eventRef.getId());
        entryMap.put(DocumentHistoryConstants.EVENT_TYPE.getValue(),
            nodeService.getProperty(eventRef, HistoryModel.PROP_NAME));
        entryMap.put(DocumentHistoryConstants.DOCUMENT_VERSION.getValue(),
            nodeService.getProperty(eventRef, HistoryModel.PROP_DOCUMENT_VERSION));
        entryMap.put(DocumentHistoryConstants.COMMENTS.getValue(),
            nodeService.getProperty(eventRef, HistoryModel.PROP_TASK_COMMENT));
        entryMap.put(DocumentHistoryConstants.DOCUMENT_DATE.getValue(),
            IMPORT_DATE_FORMAT.format((Date) nodeService.getProperty(eventRef, HistoryModel.PROP_DATE)));
        entryMap.put(DocumentHistoryConstants.TASK_ROLE.getValue(),
            nodeService.getProperty(eventRef, HistoryModel.PROP_TASK_ROLE));
        entryMap.put(DocumentHistoryConstants.TASK_OUTCOME.getValue(),
            nodeService.getProperty(eventRef, HistoryModel.PROP_TASK_OUTCOME));
        entryMap.put(DocumentHistoryConstants.TASK_INSTANCE_ID.getValue(),
            nodeService.getProperty(eventRef, HistoryModel.PROP_TASK_INSTANCE_ID));
        QName taskType = (QName) nodeService.getProperty(eventRef, HistoryModel.PROP_TASK_TYPE);
        entryMap.put(DocumentHistoryConstants.TASK_TYPE.getValue(), taskType != null ? taskType.getLocalName() : "");
        entryMap.put(FULL_TASK_TYPE, taskType != null ? taskType.toString() : "");

        ArrayList<NodeRef> attachments = (ArrayList<NodeRef>) nodeService.getProperty(eventRef,
            HistoryModel.PROP_TASK_ATTACHMENTS);
        entryMap.put(DocumentHistoryConstants.TASK_ATTACHMENTS.getValue(),
            Optional.ofNullable(attachments).orElse(new ArrayList<>()));

        ArrayList<NodeRef> pooledActors = (ArrayList<NodeRef>) nodeService.getProperty(eventRef,
            HistoryModel.PROP_TASK_POOLED_ACTORS);
        entryMap.put(DocumentHistoryConstants.TASK_POOLED_ACTORS.getValue(),
            Optional.ofNullable(pooledActors).orElse(new ArrayList<>()));

        /* Workflow */
        entryMap.put(WORKFLOW_INSTANCE_ID, nodeService.getProperty(eventRef, HistoryModel.PROP_WORKFLOW_INSTANCE_ID));
        entryMap.put(WORKFLOW_DESCRIPTION, nodeService.getProperty(eventRef, HistoryModel.PROP_WORKFLOW_DESCRIPTION));
        entryMap.put(TASK_EVENT_INSTANCE_ID, nodeService.getProperty(eventRef, HistoryModel.PROP_TASK_INSTANCE_ID));
        entryMap.put(INITIATOR, nodeService.getProperty(eventRef, HistoryModel.ASSOC_INITIATOR));
        QName propertyName = (QName) properties.get(HistoryModel.PROP_PROPERTY_NAME);
        entryMap.put(PROPERTY_NAME, propertyName != null ? propertyName.getLocalName() : null);
        /* Event task */
        NodeRef taskNodeRef = (NodeRef) nodeService.getProperty(eventRef, HistoryModel.PROP_CASE_TASK);
        if (taskNodeRef != null) {
            Integer expectedPerformTime = (Integer) nodeService.getProperty(taskNodeRef,
                ActivityModel.PROP_EXPECTED_PERFORM_TIME);
            entryMap.put(EXPECTED_PERFORM_TIME, expectedPerformTime != null ? expectedPerformTime.toString() : null);
        }
        /* Username and user id */
        String username = (String) nodeService.getProperty(eventRef, HistoryModel.MODIFIER_PROPERTY);
        NodeRef userNodeRef = personService.getPerson(username);
        entryMap.put(USER_ID, userNodeRef != null ? userNodeRef.getId() : null);
        entryMap.put(USERNAME, username);
        return entryMap;
    }

    /**
     * Convert list of maps to json string
     *
     * @param records History records
     * @return Json string
     */
    private String convertListOfMapsToJsonString(List<Map<String, Object>> records) {
        List<String> jsonObjects = new ArrayList<>(records.size());
        for (Map objectMap : records) {
            jsonObjects.add(convertMapToJsonString(objectMap));
        }
        JSONArray result = new JSONArray(jsonObjects);
        return result.toString();
    }

    /**
     * Convert map to json string
     *
     * @param requestParams Request params map
     * @return Json string
     */
    private String convertMapToJsonString(Map<String, Object> requestParams) {
        JSONObject jsonObject = new JSONObject(requestParams);
        return jsonObject.toString();
    }

    /**
     * Save history record as csv file
     *
     * @param requestParams Request params
     */
    private void saveHistoryRecordAsCsv(Map<String, Object> requestParams) {
        /* Make csv string */
        StringBuilder csvResult = new StringBuilder();
        for (String key : KEYS) {
            Object value = requestParams.get(key);
            csvResult.append((value != null ? value.toString() : "") + DELIMITER);
        }
        csvResult.append("\n");
        /* Create file */
        String currentDate = DATE_FORMAT.format(new Date());
        File csvFile = new File(properties.getProperty(CSV_RESULT_FOLDER, DEFAULT_RESULT_CSV_FOLDER)
            + HISTORY_RECORD_FILE_NAME + currentDate + ".csv");
        try {
            Files.write(
                Paths.get(csvFile.toURI()),
                csvResult.toString().getBytes(),
                StandardOpenOption.APPEND,
                StandardOpenOption.CREATE,
                StandardOpenOption.SYNC
            );
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Update document history status
     *
     * @param documentNodeRef Document node reference
     * @param newStatus       New document status
     */
    @Override
    public void updateDocumentHistoryStatus(NodeRef documentNodeRef, boolean newStatus) {
        try {
            setUseNewHistoryStatus(documentNodeRef, newStatus);
        } catch (Exception e) {
            log.error("Unexpected error with args documentNodeRef = " + documentNodeRef +
                ", newStatus = " + newStatus, e);
        }
    }

    /**
     * Remove history events by document
     *
     * @param documentNodeRef Document node reference
     */
    @Override
    public void removeEventsByDocument(NodeRef documentNodeRef) {
        if (useActiveMq()) {
            if (rabbitTemplate != null) {
                rabbitTemplate.convertAndSend(DELETE_RECORDS_BY_DOCUMENT_QUEUE, documentNodeRef.getId());
            }
            if (jmsTemplate != null) {
                jmsTemplate.convertAndSend(DELETE_RECORDS_BY_DOCUMENT_QUEUE, documentNodeRef.getId());
            }
        } else {
            restTemplate.postForObject(
                properties.getProperty(HISTORY_SERVICE_HOST) + DELETE_BY_DOCUMENT_ID_PATH,
                documentNodeRef.getId(),
                Object.class);
        }
    }

    /**
     * Check - use active mq for history records sending
     *
     * @return Check result
     */
    private Boolean useActiveMq() {
        String propertyValue = properties.getProperty(USE_ACTIVE_MQ);
        if (propertyValue == null) {
            return false;
        } else {
            return Boolean.valueOf(propertyValue);
        }
    }

    private void setUseNewHistoryStatus(NodeRef documentNodeRef, boolean newStatus) {

        Boolean currentStatus = nodeUtils.getProperty(documentNodeRef, IdocsModel.PROP_USE_NEW_HISTORY);

        if (!Boolean.valueOf(newStatus).equals(currentStatus) && !transactionService.isReadOnly()) {

            boolean requiresNew = getTransactionReadState() != TxnReadState.TXN_READ_WRITE;
            RetryingTransactionHelper txnHelper = transactionService.getRetryingTransactionHelper();

            AuthenticationUtil.runAsSystem(() -> txnHelper.doInTransaction(() -> {
                try {
                    behaviourFilter.disableBehaviour(documentNodeRef);
                    if (nodeUtils.isValidNode(documentNodeRef)) {
                        nodeService.setProperty(documentNodeRef, IdocsModel.PROP_USE_NEW_HISTORY, newStatus);
                    }
                    return null;
                } finally {
                    behaviourFilter.enableBehaviour(documentNodeRef);
                }
            }, false, requiresNew));
        }
    }

    public void setRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void setTransactionService(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setPersonService(PersonService personService) {
        this.personService = personService;
    }

    public void setBehaviourFilter(BehaviourFilter behaviourFilter) {
        this.behaviourFilter = behaviourFilter;
    }

    public void setNodeUtils(NodeUtils nodeUtils) {
        this.nodeUtils = nodeUtils;
    }
}
