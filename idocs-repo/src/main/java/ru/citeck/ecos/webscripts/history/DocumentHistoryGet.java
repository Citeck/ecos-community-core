package ru.citeck.ecos.webscripts.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.alfresco.repo.i18n.MessageService;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.dictionary.TypeDefinition;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.extensions.surf.util.I18NUtil;
import org.springframework.extensions.webscripts.*;
import ru.citeck.ecos.constants.DocumentHistoryConstants;
import ru.citeck.ecos.history.HistoryRemoteService;
import ru.citeck.ecos.history.filter.Criteria;
import ru.citeck.ecos.history.impl.HistoryGetService;
import ru.citeck.ecos.model.IdocsModel;
import ru.citeck.ecos.spring.registry.MappingRegistry;

import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

public class DocumentHistoryGet extends AbstractWebScript {

    private static final String ENABLED_REMOTE_HISTORY_SERVICE = "ecos.citeck.history.service.enabled";

    public static final String ALFRESCO_NAMESPACE = "http://www.alfresco.org/model/content/1.0";
    public static final String HISTORY_PROPERTY_NAME = "history";
    public static final String ATTRIBUTES_PROPERTY_NAME = "attributes";

    /**
     * Request params
     */
    private static final String PARAM_DOCUMENT_NODE_REF = "nodeRef";
    private static final String PARAM_EVENTS = "events";
    private static final String PARAM_FILTER = "filter";
    private static final String PARAM_TASK_TYPES = "taskTypes";

    @Autowired
    @Qualifier("global-properties")
    private Properties properties;

    private NodeService nodeService;
    private PersonService personService;
    private MessageService messageService;
    private ServiceRegistry serviceRegistry;
    private DictionaryService dictionaryService;
    private HistoryGetService historyGetService;
    private HistoryRemoteService historyRemoteService;

    private MappingRegistry<String, Criteria> filterRegistry = new MappingRegistry<>();

    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void execute(WebScriptRequest req, WebScriptResponse res) throws IOException {

        String nodeRefUuid = req.getParameter(PARAM_DOCUMENT_NODE_REF);
        String eventsParam = req.getParameter(PARAM_EVENTS);
        String filterParam = req.getParameter(PARAM_FILTER);
        String taskTypeParam = req.getParameter(PARAM_TASK_TYPES);

        /* Check history event status */

        List<ObjectNode> events = getHistoryEvents(nodeRefUuid, filterParam, eventsParam, taskTypeParam);

        try (Writer writer = res.getWriter()) {
            res.setContentType(Format.JSON.mimetype() + ";charset=UTF-8");
            objectMapper.writeValue(writer, Collections.singletonMap(HISTORY_PROPERTY_NAME, events));
            res.setStatus(Status.STATUS_OK);
        }
    }

    public List<ObjectNode> getAllHistoryEvents(int page, int limit, String filter, String events, String taskTypes) {
        Set<String> includeEvents = split(events);
        Set<String> includeTypes = split(taskTypes);
        Criteria filterCriteria = null;

        if (StringUtils.isNotBlank(filter)) {
            filterCriteria = filterRegistry.getMapping().get(filter);
            if (filterCriteria == null) {
                throw new WebScriptException(Status.STATUS_BAD_REQUEST, "Filter with id: " + filter + " not found");
            }
        }

        /* Load data */
        List<Map> historyRecordMaps = Collections.emptyList();
        if (isEnabledRemoteHistoryService()) {
            historyRecordMaps = historyRemoteService.getAllHistoryRecords(page, limit);
        }

        if (filterCriteria != null) {
            historyRecordMaps = filterCriteria.meetCriteria(historyRecordMaps);
        }

        return formatHistoryNodes(historyRecordMaps, includeEvents, includeTypes);
    }

    public List<ObjectNode> getHistoryEvents(String nodeRef, String filter, String events, String taskTypes) {

        NodeRef documentRef = new NodeRef(nodeRef);
        Set<String> includeEvents = split(events);
        Set<String> includeTypes = split(taskTypes);
        Criteria filterCriteria = null;

        if (StringUtils.isNotBlank(filter)) {
            filterCriteria = filterRegistry.getMapping().get(filter);
            if (filterCriteria == null) {
                throw new WebScriptException(Status.STATUS_BAD_REQUEST, "Filter with id: " + filter + " not found");
            }
        }

        Boolean useNewHistory = (Boolean) nodeService.getProperty(documentRef, IdocsModel.PROP_USE_NEW_HISTORY);
        if ((useNewHistory == null || !useNewHistory) && isEnabledRemoteHistoryService()) {
            historyRemoteService.sendHistoryEventsByDocumentToRemoteService(documentRef);
        }
        /* Load data */
        List<Map> historyRecordMaps;
        if (isEnabledRemoteHistoryService()) {
            historyRecordMaps = historyRemoteService.getHistoryRecords(documentRef.getId());
        } else {
            historyRecordMaps = historyGetService.getHistoryEventsByDocumentRef(documentRef);
        }

        if (filterCriteria != null) {
            historyRecordMaps = filterCriteria.meetCriteria(historyRecordMaps);
        }

        return formatHistoryNodes(historyRecordMaps, includeEvents, includeTypes);
    }

    /**
     * Check - is remote history service enabled
     *
     * @return Check result
     */
    private Boolean isEnabledRemoteHistoryService() {
        String propertyValue = properties.getProperty(ENABLED_REMOTE_HISTORY_SERVICE);
        if (propertyValue == null) {
            return false;
        } else {
            return Boolean.valueOf(propertyValue);
        }
    }

    /**
     * Create json response
     *
     * @param historyRecordMaps History records maps
     * @return Json string
     */
    @SuppressWarnings("unchecked")
    private List<ObjectNode> formatHistoryNodes(List<Map> historyRecordMaps, Set<String> includeEvents, Set<String> includeTaskTypes) {

        List<ObjectNode> result = new ArrayList<>();
        Map<List<String>, String> outcomeTitles = new HashMap<>();
        Map<Object, String> taskTitles = new HashMap<>();

        /* Transform records */
        for (Map<String, Object> historyRecordMap : historyRecordMaps) {

            String eventType = (String) historyRecordMap.get(DocumentHistoryConstants.EVENT_TYPE.getValue());

            if (includeEvents != null && !includeEvents.isEmpty() && !includeEvents.contains(eventType)) {
                continue;
            }

            ObjectNode recordObjectNode = objectMapper.createObjectNode();
            recordObjectNode.put(DocumentHistoryConstants.NODE_REF.getKey(),
                (String) historyRecordMap.get(DocumentHistoryConstants.NODE_REF.getValue()));
            ObjectNode attributesNode = objectMapper.createObjectNode();

            String taskType = (String) historyRecordMap.get(DocumentHistoryConstants.TASK_TYPE.getValue());
            String taskTypeShort = null;

            if (StringUtils.isNotEmpty(taskType)) {

                QName taskTypeQName = QName.createQName(taskType);

                ObjectNode taskTypeNode = objectMapper.createObjectNode();
                taskTypeNode.put("fullQName", taskType);

                taskTypeShort = taskTypeQName.toPrefixString(serviceRegistry.getNamespaceService());
                taskTypeNode.put("shortQName", taskTypeShort);

                /* filter out records by taskTypes if specified */
                if (CollectionUtils.isNotEmpty(includeTaskTypes) && !includeTaskTypes.contains(taskTypeShort)) {
                    continue;
                }

                attributesNode.put(DocumentHistoryConstants.TASK_TYPE.getKey(), taskTypeNode);

                String taskTitle = getTaskTitle(taskTypeQName, historyRecordMap, taskTitles);
                attributesNode.put(DocumentHistoryConstants.TASK_TITLE.getKey(), taskTitle);
            }

            /* Populate object */
            Date date = new Date((Long) historyRecordMap.get(DocumentHistoryConstants.DOCUMENT_DATE.getValue()));
            ZoneOffset offset = ZoneOffset.systemDefault().getRules().getOffset(Instant.now());
            OffsetDateTime offsetDateTime = date.toInstant().atOffset(offset);
            attributesNode.put(DocumentHistoryConstants.DOCUMENT_DATE.getKey(), offsetDateTime.toString());
            attributesNode.put(DocumentHistoryConstants.DOCUMENT_VERSION.getKey(),
                (String) historyRecordMap.get(DocumentHistoryConstants.DOCUMENT_VERSION.getValue()));
            attributesNode.put(DocumentHistoryConstants.COMMENTS.getKey(),
                (String) historyRecordMap.get(DocumentHistoryConstants.COMMENTS.getValue()));
            attributesNode.put(DocumentHistoryConstants.EVENT_TYPE.getKey(),
                (String) historyRecordMap.get(DocumentHistoryConstants.EVENT_TYPE.getValue()));
            attributesNode.put(DocumentHistoryConstants.TASK_ROLE.getKey(),
                (String) historyRecordMap.get(DocumentHistoryConstants.TASK_ROLE.getValue()));
            attributesNode.put(DocumentHistoryConstants.TASK_OUTCOME.getKey(),
                (String) historyRecordMap.get(DocumentHistoryConstants.TASK_OUTCOME.getValue()));
            attributesNode.put(
                DocumentHistoryConstants.TASK_OUTCOME_TITLE.getKey(),
                getTaskOutcomeTitle(taskTypeShort, historyRecordMap, outcomeTitles)
            );
            attributesNode.put(DocumentHistoryConstants.TASK_INSTANCE_ID.getKey(),
                (String) historyRecordMap.get(DocumentHistoryConstants.TASK_INSTANCE_ID.getValue()));

            ArrayList<NodeRef> attachments = (ArrayList<NodeRef>) historyRecordMap.get(
                DocumentHistoryConstants.TASK_ATTACHMENTS.getValue());
            if (attachments != null) {
                attributesNode.put(DocumentHistoryConstants.TASK_ATTACHMENTS.getKey(),
                    transformNodeRefsToArrayNode(attachments));
            }

            ArrayList<NodeRef> pooledActors = (ArrayList<NodeRef>) historyRecordMap.get(
                DocumentHistoryConstants.TASK_POOLED_ACTORS.getValue());
            if (pooledActors != null) {
                attributesNode.put(DocumentHistoryConstants.TASK_POOLED_ACTORS.getKey(),
                    transformNodeRefsToArrayNode(pooledActors));
            }

            /* User */

            Object initiatorObj = historyRecordMap.get(DocumentHistoryConstants.EVENT_INITIATOR.getValue());
            NodeRef initiatorRef = null;
            if (initiatorObj instanceof NodeRef) {
                initiatorRef = (NodeRef) initiatorObj;
            } else if (initiatorObj instanceof String) {
                String initiatorStr = (String) initiatorObj;
                if (initiatorStr.startsWith("workspace://")) {
                    initiatorRef = new NodeRef(initiatorStr);
                } else {
                    initiatorRef = personService.getPersonOrNull(initiatorStr);
                }
            }
            if (initiatorRef != null) {
                attributesNode.put(DocumentHistoryConstants.EVENT_INITIATOR.getKey(), createUserNode(initiatorRef));
            }

            /* Add history node to result */
            recordObjectNode.put(ATTRIBUTES_PROPERTY_NAME, attributesNode);

            result.add(recordObjectNode);
        }

        return result;
    }

    private String getTaskTitle(QName taskType, Map<String, Object> historyRecordMap, Map<Object, String> taskTitles) {
        String title = (String) historyRecordMap.get(DocumentHistoryConstants.TASK_TITLE.getValue());
        if (StringUtils.isBlank(title)) {
            if (taskType != null) {
                title = taskTitles.computeIfAbsent(taskType, type -> {
                    TypeDefinition typeDef = dictionaryService.getType(taskType);
                    return typeDef != null ? typeDef.getTitle(messageService) : type.toString();
                });
            } else {
                title = "";
            }
        } else {
            title = taskTitles.computeIfAbsent(title, key -> {
                String strKey = key.toString();
                String titleMessage = I18NUtil.getMessage(strKey);
                return StringUtils.isNotBlank(titleMessage) ? titleMessage : strKey;
            });
        }
        return title;
    }

    private String getTaskOutcomeTitle(String taskTypeShort,
                                       Map<String, Object> historyRecordMap,
                                       Map<List<String>, String> titles) {

        String outcome = (String) historyRecordMap.get(DocumentHistoryConstants.TASK_OUTCOME.getValue());
        if (outcome == null) {
            return null;
        }

        String taskDefinitionVarKey = DocumentHistoryConstants.TASK_DEFINITION_KEY.getValue();
        String taskDefinitionKey = (String) historyRecordMap.get(taskDefinitionVarKey);

        return titles.computeIfAbsent(Arrays.asList(taskTypeShort, taskDefinitionKey, outcome), itemKey -> {

            if (StringUtils.isNotBlank(taskDefinitionKey)) {
                String key = "flowable.form.button." + taskDefinitionKey + "." + outcome + ".label";
                String title = I18NUtil.getMessage(key);
                if (StringUtils.isNotBlank(title)) {
                    return title;
                }
            }

            if (StringUtils.isNotBlank(taskTypeShort)) {
                String correctType = taskTypeShort.replaceAll(":", "_");
                String title = I18NUtil.getMessage("workflowtask." + correctType + ".outcome." + outcome);
                if (StringUtils.isNotBlank(title)) {
                    return title;
                }
            }

            String title = I18NUtil.getMessage("workflowtask.outcome." + outcome);
            return StringUtils.isNotBlank(title) ? title : outcome;
        });
    }

    private ArrayNode transformNodeRefsToArrayNode(ArrayList<NodeRef> nodes) {
        ObjectMapper objectMapper = new ObjectMapper();
        ArrayNode result = objectMapper.createArrayNode();
        if (nodes == null || nodes.isEmpty()) {
            return result;
        }

        for (NodeRef node : nodes) {
            ObjectNode attachmentNode = objectMapper.createObjectNode();
            attachmentNode.put("nodeRef", node.toString());
            result.add(attachmentNode);
        }

        return result;
    }

    /**
     * Create user node
     *
     * @param userNodeRef User node reference
     * @return Array node
     */
    private ArrayNode createUserNode(NodeRef userNodeRef) {
        ObjectMapper objectMapper = new ObjectMapper();
        ArrayNode result = objectMapper.createArrayNode();
        ObjectNode userNode = objectMapper.createObjectNode();
        userNode.put("nodeRef", userNodeRef.toString());
        userNode.put("type", "cm:person");
        userNode.put("cm:userName", (String) nodeService.getProperty(userNodeRef,
                QName.createQName(ALFRESCO_NAMESPACE, "userName")));
        userNode.put("cm:firstName", (String) nodeService.getProperty(userNodeRef,
                QName.createQName(ALFRESCO_NAMESPACE, "firstName")));
        userNode.put("cm:lastName", (String) nodeService.getProperty(userNodeRef,
                QName.createQName(ALFRESCO_NAMESPACE, "lastName")));
        userNode.put("cm:middleName", (String) nodeService.getProperty(userNodeRef,
                QName.createQName(ALFRESCO_NAMESPACE, "middleName")));
        String displayName = userNode.get("cm:lastName") + " " + userNode.get("cm:firstName") + " "
                + userNode.get("cm:middleName");
        userNode.put("displayName", displayName.trim());
        result.add(userNode);
        return result;

    }

    /**
     * Split comma separated parameters string to Set of parameters
     * @param csv Comma separated values
     * @return Set of parameters
     */
    private Set<String> split(String csv) {
        Set<String> result = Collections.emptySet();
        if (StringUtils.isNotBlank(csv)) {
            result = Arrays.stream(csv.split(",")).collect(Collectors.toSet());
        }
        return result;
    }

    public void setHistoryRemoteService(HistoryRemoteService historyRemoteService) {
        this.historyRemoteService = historyRemoteService;
    }

    public void setHistoryGetService(HistoryGetService historyGetService) {
        this.historyGetService = historyGetService;
    }

    public void setFilterRegistry(MappingRegistry<String, Criteria> filterRegistry) {
        this.filterRegistry = filterRegistry;
    }

    @Autowired
    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
        this.personService = serviceRegistry.getPersonService();
        this.nodeService = serviceRegistry.getNodeService();
        this.dictionaryService = serviceRegistry.getDictionaryService();
        this.messageService = serviceRegistry.getMessageService();
    }
}
