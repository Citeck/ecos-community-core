package ru.citeck.ecos.cases.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.lock.LockService;
import org.alfresco.service.cmr.lock.LockStatus;
import org.alfresco.service.cmr.repository.AssociationRef;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.namespace.RegexQNamePattern;
import org.alfresco.service.transaction.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.util.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import ru.citeck.ecos.cases.RemoteCaseModelService;
import ru.citeck.ecos.dto.*;
import ru.citeck.ecos.model.*;

import java.io.IOException;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Remote case model service
 */
@Slf4j
public class RemoteCaseModelServiceImpl implements RemoteCaseModelService {

    /**
     * Exclude properties
     */
    private static final QName[] EXCLUDE_PROPERTIES = {
            ContentModel.PROP_NODE_UUID, ContentModel.PROP_NODE_DBID,
            ContentModel.PROP_CREATED, ContentModel.PROP_CREATOR,
            ContentModel.PROP_MODIFIED, ContentModel.PROP_MODIFIER,
            ContentModel.PROP_TITLE,  ContentModel.PROP_DESCRIPTION,
            ActivityModel.PROP_EXPECTED_PERFORM_TIME,
            ICaseTaskModel.PROP_PRIORITY, ICaseTaskModel.PROP_DEADLINE,
            ICaseTaskModel.PROP_WORKFLOW_INSTANCE_ID, ICaseTaskModel.PROP_WORKFLOW_DEFINITION_NAME,
            ActivityModel.PROP_ACTUAL_START_DATE, ActivityModel.PROP_ACTUAL_END_DATE,
            ActivityModel.PROP_PLANNED_START_DATE, ActivityModel.PROP_PLANNED_END_DATE,
            ActivityModel.PROP_AUTO_EVENTS, ActivityModel.PROP_INDEX,
            ActivityModel.PROP_MANUAL_STARTED, ActivityModel.PROP_MANUAL_STOPPED,
            ActivityModel.PROP_REPEATABLE, ActivityModel.PROP_TYPE_VERSION
    };

    /**
     * Exclude assocs
     */
    private static final QName[] EXCLUDE_ASSOCS = {
            ICaseTaskModel.ASSOC_WORKFLOW_PACKAGE
    };

    /**
     * Properties keys
     */
    private static final String CASE_MODELS_SERVICE_HOST = "citeck.remote.case.service.host";

    /**
     * Service methods constants
     */
    private static final String SAVE_CASE_MODELS_METHOD = "/case_models/save_case_models";
    private static final String GET_CASE_MODEL_BY_NODE_ID = "/case_models/case_model_by_id/";
    private static final String GET_CASE_MODELS_BY_DOCUMENT_ID = "/case_models/case_models_by_document_id/";
    private static final String GET_CASE_MODELS_BY_PARENT_CASE_MODEL_ID = "/case_models/case_models_by_parent_case_models_id/";
    private static final String DELETE_CASE_MODELS_BY_DOCUMENT_ID = "/case_models/delete_case_models_by_document_id/";

    /**
     * Date format
     */
    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT = ThreadLocal.withInitial(() ->
        new SimpleDateFormat("yyyy-MM-dd"));

    /**
     * Datetime format
     */
    private static final ThreadLocal<SimpleDateFormat> DATE_TIME_FORMAT = ThreadLocal.withInitial(() ->
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));

    /**
     * Object mapper
     */
    private ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Node service
     */
    private NodeService nodeService;

    /**
     * Dictionary service
     */
    private DictionaryService dictionaryService;

    /**
     * Transaction service
     */
    private TransactionService transactionService;

    /**
     * Res template
     */
    private RestTemplate restTemplate;

    /**
     * Lock service
     */
    private LockService lockService;


    /**
     * Global properties
     */
    @Autowired
    @Qualifier("global-properties")
    private Properties properties;

    /**
     * Get case models by node
     * @param nodeRef Node reference
     * @return List of case models node references
     */
    @Override
    public List<NodeRef> getCaseModelsByNode(NodeRef nodeRef) {
        List<ChildAssociationRef> caseAssocs = nodeService.getChildAssocs(nodeRef);
        List<NodeRef> result = new ArrayList<>(caseAssocs.size());
        for (ChildAssociationRef caseAssoc : caseAssocs) {
            if (ActivityModel.ASSOC_ACTIVITIES.equals(caseAssoc.getTypeQName())) {
                NodeRef caseRef = caseAssoc.getChildRef();
                result.add(caseRef);
            }
        }
        return result;
    }

    /**
     * Send and remove case models by document
     * @param documentRef Document reference
     */
    @Override
    public void sendAndRemoveCaseModelsByDocument(NodeRef documentRef) {
        LockStatus lockStatus = lockService.getLockStatus(documentRef);
        if (lockStatus != null && lockStatus!= LockStatus.NO_LOCK) {
            log.info("Document " + documentRef.getId() + " is locked - " + lockStatus);
            return;
        }
        transactionService.getRetryingTransactionHelper().doInTransaction(() -> {
            AuthenticationUtil.runAsSystem(() -> {
                ArrayNode arrayNode = objectMapper.createArrayNode();
                List<NodeRef> forDelete = new ArrayList<>();

                StopWatch watch = new StopWatch(RemoteCaseModelServiceImpl.class.getName());

                /* Create json array */
                runWatch(watch, "Receiving of caseModels for " + documentRef.toString());
                for (NodeRef caseRef : getCaseModelsByNode(documentRef)) {
                    ObjectNode objectNode = createObjectNodeFromCaseModel(caseRef);
                    if (objectNode != null) {
                        arrayNode.add(objectNode);
                    }
                    forDelete.add(caseRef);
                }
                nodeService.setProperty(documentRef, IdocsModel.PROP_CASE_MODELS_SENT, true);
                watch.stop();

                /* Send request */
                runWatch(watch, "Post request for saving case models for " + documentRef.toString());
                postForObject(SAVE_CASE_MODELS_METHOD, arrayNode.toString(), String.class);
                watch.stop();

                /* Delete nodes */
                runWatch(watch, "Deletion of case models nodes from alfresco for " + documentRef.toString());
                for (NodeRef caseNodeRef : forDelete) {
                    nodeService.addAspect(caseNodeRef, ContentModel.ASPECT_TEMPORARY, Collections.emptyMap());
                    nodeService.deleteNode(caseNodeRef);
                }
                watch.stop();

                log.debug(watch.prettyPrint());

                return null;
            });
            return null;
        });
    }

    /**
     * Create object node from case model
     * @param caseModelRef Case model reference
     * @return Object node
     */
    private ObjectNode createObjectNodeFromCaseModel(NodeRef caseModelRef) {
        if (caseModelRef == null) {
            return null;
        }
        /* Base properties */
        ObjectNode objectNode = objectMapper.createObjectNode();
        String dtoType = getCaseModelType(nodeService.getType(caseModelRef));
        objectNode.put("dtoType", dtoType);
        fillBaseNodeInfo(caseModelRef, objectNode);
        /* Document id */
        ChildAssociationRef parentAssoc = nodeService.getPrimaryParent(caseModelRef);
        if (parentAssoc != null && parentAssoc.getParentRef() != null) {
            if (dictionaryService.isSubClass(
                    nodeService.getType(parentAssoc.getParentRef()),
                    IdocsModel.TYPE_DOC)) {
                objectNode.put("documentId", parentAssoc.getParentRef().getId());
            }
        }
        /* Case info */
        if (nodeService.getProperty(caseModelRef, ActivityModel.PROP_PLANNED_START_DATE) != null) {
            objectNode.put("plannedStartDate", DATE_FORMAT.get().format((Date) nodeService.getProperty(caseModelRef, ActivityModel.PROP_PLANNED_START_DATE)));
        }
        if (nodeService.getProperty(caseModelRef, ActivityModel.PROP_PLANNED_END_DATE) != null) {
            objectNode.put("plannedEndDate", DATE_FORMAT.get().format((Date) nodeService.getProperty(caseModelRef, ActivityModel.PROP_PLANNED_END_DATE)));
        }
        if (nodeService.getProperty(caseModelRef, ActivityModel.PROP_ACTUAL_START_DATE) != null) {
            objectNode.put("actualStartDate", DATE_FORMAT.get().format((Date) nodeService.getProperty(caseModelRef, ActivityModel.PROP_ACTUAL_START_DATE)));
        }
        if (nodeService.getProperty(caseModelRef, ActivityModel.PROP_ACTUAL_END_DATE) != null) {
            objectNode.put("actualEndDate", DATE_FORMAT.get().format((Date) nodeService.getProperty(caseModelRef, ActivityModel.PROP_ACTUAL_END_DATE)));
        }
        objectNode.put("expectedPerformTime", (Integer) nodeService.getProperty(caseModelRef, ActivityModel.PROP_EXPECTED_PERFORM_TIME));
        objectNode.put("manualStarted", (Boolean) nodeService.getProperty(caseModelRef, ActivityModel.PROP_MANUAL_STARTED));
        objectNode.put("manualStopped", (Boolean) nodeService.getProperty(caseModelRef, ActivityModel.PROP_MANUAL_STOPPED));
        objectNode.put("index", (Integer) nodeService.getProperty(caseModelRef, ActivityModel.PROP_INDEX));
        objectNode.put("autoEvents", (Boolean) nodeService.getProperty(caseModelRef, ActivityModel.PROP_AUTO_EVENTS));
        objectNode.put("repeatable", (Boolean) nodeService.getProperty(caseModelRef, ActivityModel.PROP_REPEATABLE));
        objectNode.put("typeVersion", (Integer) nodeService.getProperty(caseModelRef, ActivityModel.PROP_TYPE_VERSION));
        /* Additional info */
        fillEventsInfo(caseModelRef, objectNode);
        fillAdditionalInfo(dtoType, caseModelRef, objectNode);
        /* Child activities */
        ArrayNode arrayNode = objectMapper.createArrayNode();
        for (NodeRef childCaseRef : getCaseModelsByNode(caseModelRef)) {
            ObjectNode childCaseNode = createObjectNodeFromCaseModel(childCaseRef);
            if (childCaseNode != null) {
                arrayNode.add(childCaseNode);
            }
        }
        objectNode.put("childCases", arrayNode);
        return objectNode;
    }

    /**
     * Fill base node info
     * @param nodeRef Node reference
     * @param objectNode Object node
     */
    private void fillBaseNodeInfo(NodeRef nodeRef, ObjectNode objectNode) {
        objectNode.put("nodeUUID", nodeRef.getId());
        if (nodeService.getProperty(nodeRef, ContentModel.PROP_CREATED) != null) {
            objectNode.put("created", DATE_TIME_FORMAT.get().format((Date) nodeService.getProperty(nodeRef, ContentModel.PROP_CREATED)));
        }
        objectNode.put("creator", (String) nodeService.getProperty(nodeRef, ContentModel.PROP_CREATOR));
        if (nodeService.getProperty(nodeRef, ContentModel.PROP_MODIFIED) != null) {
            objectNode.put("modified", DATE_TIME_FORMAT.get().format((Date) nodeService.getProperty(nodeRef, ContentModel.PROP_MODIFIED)));
        }
        objectNode.put("modifier", (String) nodeService.getProperty(nodeRef, ContentModel.PROP_MODIFIER));
        objectNode.put("title", (String) nodeService.getProperty(nodeRef, ContentModel.PROP_TITLE));
        objectNode.put("description", (String) nodeService.getProperty(nodeRef, ContentModel.PROP_DESCRIPTION));
    }

    /**
     * Fill events info
     * @param caseModelRef Case model reference
     * @param objectNode Object node
     */
    private void fillEventsInfo(NodeRef caseModelRef, ObjectNode objectNode) {
        List<ChildAssociationRef> caseAssocs = nodeService.getChildAssocs(caseModelRef);

        ArrayNode startEventsNode = objectMapper.createArrayNode();
        ArrayNode endEventsNode = objectMapper.createArrayNode();
        ArrayNode restartEventsNode = objectMapper.createArrayNode();
        ArrayNode resetEventsNode = objectMapper.createArrayNode();

        for (ChildAssociationRef caseAssoc : caseAssocs) {
            if (ICaseEventModel.ASSOC_ACTIVITY_START_EVENTS.equals(caseAssoc.getTypeQName())) {
                NodeRef eventRef = caseAssoc.getChildRef();
                ObjectNode eventNode = createEventObjectNode(eventRef);
                startEventsNode.add(eventNode);
            }
            if (ICaseEventModel.ASSOC_ACTIVITY_END_EVENTS.equals(caseAssoc.getTypeQName())) {
                NodeRef eventRef = caseAssoc.getChildRef();
                ObjectNode eventNode = createEventObjectNode(eventRef);
                endEventsNode.add(eventNode);
            }
            if (ICaseEventModel.ASSOC_ACTIVITY_RESTART_EVENTS.equals(caseAssoc.getTypeQName())) {
                NodeRef eventRef = caseAssoc.getChildRef();
                ObjectNode eventNode = createEventObjectNode(eventRef);
                restartEventsNode.add(eventNode);
            }
            if (ICaseEventModel.ASSOC_ACTIVITY_RESET_EVENTS.equals(caseAssoc.getTypeQName())) {
                NodeRef eventRef = caseAssoc.getChildRef();
                ObjectNode eventNode = createEventObjectNode(eventRef);
                resetEventsNode.add(eventNode);
            }
        }

        objectNode.put("startEvents", startEventsNode);
        objectNode.put("endEvents", endEventsNode);
        objectNode.put("restartEvents", restartEventsNode);
        objectNode.put("resetEvents", resetEventsNode);
    }

    /**
     * Create event object node
     * @param eventNodeRef Event node reference
     * @return Object node
     */
    private ObjectNode createEventObjectNode(NodeRef eventNodeRef) {
        ObjectNode eventNode = objectMapper.createObjectNode();
        String dtoType = getEventType(nodeService.getType(eventNodeRef));
        fillBaseNodeInfo(eventNodeRef, eventNode);
        eventNode.put("dtoType", dtoType);
        /* Event info */
        eventNode.put("type", (String) nodeService.getProperty(eventNodeRef, ICaseEventModel.PROPERTY_TYPE));
        fillAdditionalEventInfo(dtoType, eventNodeRef, eventNode);
        fillConditionsInfo(eventNodeRef, eventNode);
        /* Source info */
        List<AssociationRef> sourcesAssocs = nodeService.getTargetAssocs(eventNodeRef, EventModel.ASSOC_EVENT_SOURCE);
        if (!CollectionUtils.isEmpty(sourcesAssocs)) {
            NodeRef sourceRef = sourcesAssocs.get(0).getTargetRef();
            String sourceType = getCaseModelType(nodeService.getType(sourceRef));
            eventNode.put("sourceCaseId", sourceRef.getId());
            eventNode.put("isSourceCase", sourceType != null);
        }
        return eventNode;
    }

    /**
     * Fill conditions info
     * @param eventNodeRef Event node reference
     * @param objectNode Object node
     */
    private void fillConditionsInfo(NodeRef eventNodeRef, ObjectNode objectNode) {
        List<ChildAssociationRef> childAssocs = nodeService.getChildAssocs(eventNodeRef);
        ArrayNode conditionsNode = objectMapper.createArrayNode();
        for (ChildAssociationRef childAssoc : childAssocs) {
            if (EventModel.ASSOC_CONDITIONS.equals(childAssoc.getTypeQName())) {
                ObjectNode conditionNode = createConditionObjectNode(childAssoc.getChildRef());
                conditionsNode.add(conditionNode);
            }
        }
        objectNode.put("conditions", conditionsNode);
    }

    /**
     * Create condition object node
     * @param conditionNodeRef Condition node reference
     * @return Object node
     */
    private ObjectNode createConditionObjectNode(NodeRef conditionNodeRef) {
        ObjectNode conditionNode = objectMapper.createObjectNode();
        fillBaseNodeInfo(conditionNodeRef, conditionNode);
        String dtoType = getConditionType(nodeService.getType(conditionNodeRef));
        conditionNode.put("dtoType", dtoType);
        fillAdditionalConditionInfo(dtoType, conditionNodeRef, conditionNode);
        return conditionNode;
    }

    /**
     * Fill additional condition info
     * @param dtoType Dto type
     * @param conditionNodeRef Condition node reference
     * @param objectNode Object node
     */
    private void fillAdditionalConditionInfo(String dtoType, NodeRef conditionNodeRef, ObjectNode objectNode) {
        if (CompareProcessVariableConditionDto.DTO_TYPE.equals(dtoType)) {
            fillAdditionalCompareProcessVariableConditionInfo(conditionNodeRef, objectNode);
        }
        if (ComparePropertyValueConditionDto.DTO_TYPE.equals(dtoType)) {
            fillAdditionalComparePropertyValueConditionInfo(conditionNodeRef, objectNode);
        }
        if (EvaluateScriptConditionDto.DTO_TYPE.equals(dtoType)) {
            fillAdditionalEvaluateScriptConditionInfo(conditionNodeRef, objectNode);
        }
        if (UserHasPermissionConditionDto.DTO_TYPE.equals(dtoType)) {
            fillAdditionalUserHasPermissionConditionInfo(conditionNodeRef, objectNode);
        }
        if (UserInDocumentConditionDto.DTO_TYPE.equals(dtoType)) {
            fillAdditionalUserInDocumentConditionInfo(conditionNodeRef, objectNode);
        }
        if (UserInGroupConditionDto.DTO_TYPE.equals(dtoType)) {
            fillAdditionalUserInGroupConditionInfo(conditionNodeRef, objectNode);
        }
    }

    /**
     * Fill additional compare process variable condition info
     * @param conditionNodeRef Condition node reference
     * @param objectNode Object node
     */
    private void fillAdditionalCompareProcessVariableConditionInfo(NodeRef conditionNodeRef, ObjectNode objectNode) {
        objectNode.put("processVariable", (String) nodeService.getProperty(conditionNodeRef, ConditionModel.CompareProcessVariable.PROP_VARIABLE));
        objectNode.put("processVariableValue", (String) nodeService.getProperty(conditionNodeRef, ConditionModel.CompareProcessVariable.PROP_VALUE));
    }

    /**
     * Fill additional compare property value condition info
     * @param conditionNodeRef Condition node reference
     * @param objectNode Object node
     */
    private void fillAdditionalComparePropertyValueConditionInfo(NodeRef conditionNodeRef, ObjectNode objectNode) {
        QName propertyName = (QName) nodeService.getProperty(conditionNodeRef, ConditionModel.ComparePropertyValue.PROP_PROPERTY);
        objectNode.put("propertyName", propertyName != null ? propertyName.toString() : null);
        objectNode.put("propertyValue", (String) nodeService.getProperty(conditionNodeRef, ConditionModel.ComparePropertyValue.PROP_VALUE));
        objectNode.put("propertyOperation", (String) nodeService.getProperty(conditionNodeRef, ConditionModel.ComparePropertyValue.PROP_OPERATION));
    }

    /**
     * Fill additional evaluate script condition info
     * @param conditionNodeRef Condition node reference
     * @param objectNode Object node
     */
    private void fillAdditionalEvaluateScriptConditionInfo(NodeRef conditionNodeRef, ObjectNode objectNode) {
        objectNode.put("evaluateScript", (String) nodeService.getProperty(conditionNodeRef, ConditionModel.EvaluateScript.PROP_SCRIPT));
    }

    /**
     * Fill additional user has permission condition info
     * @param conditionNodeRef Condition node reference
     * @param objectNode Object node
     */
    private void fillAdditionalUserHasPermissionConditionInfo(NodeRef conditionNodeRef, ObjectNode objectNode) {
        objectNode.put("permission", (String) nodeService.getProperty(conditionNodeRef, ConditionModel.UserHasPermission.PROP_PERMISSION));
        objectNode.put("permissionUsername", (String) nodeService.getProperty(conditionNodeRef, ConditionModel.UserHasPermission.PROP_USERNAME));
    }

    /**
     * Fill additional user in document condition info
     * @param conditionNodeRef Condition node reference
     * @param objectNode Object node
     */
    private void fillAdditionalUserInDocumentConditionInfo(NodeRef conditionNodeRef, ObjectNode objectNode) {
        objectNode.put("documentProperty", (String) nodeService.getProperty(conditionNodeRef, ConditionModel.UserInDocument.PROP_PROPERTY));
        objectNode.put("documentUsername", (String) nodeService.getProperty(conditionNodeRef, ConditionModel.UserInDocument.PROP_USERNAME));
    }

    /**
     * Fill additional user in group condition info
     * @param conditionNodeRef Condition node reference
     * @param objectNode Object node
     */
    private void fillAdditionalUserInGroupConditionInfo(NodeRef conditionNodeRef, ObjectNode objectNode) {
        objectNode.put("groupName", (String) nodeService.getProperty(conditionNodeRef, ConditionModel.UserInGroup.PROP_GROUPNAME));
        objectNode.put("groupUsername", (String) nodeService.getProperty(conditionNodeRef, ConditionModel.UserInGroup.PROP_USERNAME));
    }

    /**
     * Fill additional event info
     * @param dtoType Dto type
     * @param eventNodeRef Event node reference
     * @param objectNode Object node
     */
    private void fillAdditionalEventInfo(String dtoType, NodeRef eventNodeRef, ObjectNode objectNode) {
        if (UserActionEventDto.DTO_TYPE.equals(dtoType)) {
            fillAdditionalUserActionEventInfo(eventNodeRef, objectNode);
        }
    }

    /**
     * Fill additional user action event info
     * @param eventNodeRef Event node reference
     * @param objectNode Object node
     */
    private void fillAdditionalUserActionEventInfo(NodeRef eventNodeRef, ObjectNode objectNode) {
        objectNode.put("additionalDataType", (String) nodeService.getProperty(eventNodeRef, EventModel.PROP_ADDITIONAL_DATA_TYPE));
        objectNode.put("confirmationMessage", (String) nodeService.getProperty(eventNodeRef, EventModel.PROP_CONFIRMATION_MESSAGE));
        objectNode.put("successMessage", (String) nodeService.getProperty(eventNodeRef, EventModel.PROP_SUCCESS_MESSAGE));
        objectNode.put("successMessageSpanClass", (String) nodeService.getProperty(eventNodeRef, EventModel.PROP_SUCCESS_MESSAGE_SPAN_CLASS));
        /* Roles */
        ArrayNode rolesNode = objectMapper.createArrayNode();
        List<AssociationRef> rolesAssocs = nodeService.getTargetAssocs(eventNodeRef, EventModel.ASSOC_AUTHORIZED_ROLES);
        for (AssociationRef associationRef : rolesAssocs) {
            ObjectNode roleNode = objectMapper.createObjectNode();
            fillBaseNodeInfo(associationRef.getTargetRef(), roleNode);
            roleNode.put("varName", (String) nodeService.getProperty(associationRef.getTargetRef(), ICaseRoleModel.PROP_VARNAME));
            roleNode.put("isReferenceRole", (Boolean) nodeService.getProperty(associationRef.getTargetRef(), ICaseRoleModel.PROP_IS_REFERENCE_ROLE));
            rolesNode.add(roleNode);
        }
        objectNode.put("roles", rolesNode);
        /* Additional items */
        ArrayNode additionalItems = objectMapper.createArrayNode();
        List<ChildAssociationRef> childAssocs = nodeService.getChildAssocs(eventNodeRef);
        for (ChildAssociationRef childAssociationRef : childAssocs) {
            if (EventModel.ASSOC_ADDITIONAL_DATA_ITEMS.equals(childAssociationRef.getTypeQName())) {
                ObjectNode addItemNode = createAdditionalItemData(childAssociationRef.getChildRef());
                additionalItems.add(addItemNode);
            }
        }
        objectNode.put("additionalDataItems", additionalItems);
    }

    /**
     * Create additional item data
     * @param additionalDataRef Additional item data reference
     * @return Object node
     */
    private ObjectNode createAdditionalItemData(NodeRef additionalDataRef) {
        ObjectNode additionalDataNode = objectMapper.createObjectNode();
        fillBaseNodeInfo(additionalDataRef, additionalDataNode);
        String dtoType = getAdditionalItemType(nodeService.getType(additionalDataRef));
        additionalDataNode.put("dtoType", dtoType);
        additionalDataNode.put("comment", (String) nodeService.getProperty(additionalDataRef, EventModel.PROP_COMMENT));
        fillAdditionalAddDataItemInfo(dtoType, additionalDataRef, additionalDataNode);
        return additionalDataNode;
    }

    /**
     * Fill additional additional item info
     * @param dtoType Dto type
     * @param additionalDataRef Additional item data reference
     * @param objectNode Object node
     */
    private void fillAdditionalAddDataItemInfo(String dtoType, NodeRef additionalDataRef, ObjectNode objectNode) {
        if (AdditionalConfirmerDto.DTO_TYPE.equals(dtoType)) {
            fillAdditionalConfirmerInfo(additionalDataRef, objectNode);
        }
        if (AdditionalPerformersDto.DTO_TYPE.equals(dtoType)) {
            fillAdditionalPerformersInfo(additionalDataRef, objectNode);
        }
    }

    /**
     * Fill additional confirmer info
     * @param additionalDataRef Additional data reference
     * @param objectNode Object node
     */
    private void fillAdditionalConfirmerInfo(NodeRef additionalDataRef, ObjectNode objectNode) {
        List<AssociationRef> assocs = nodeService.getTargetAssocs(additionalDataRef, EventModel.ASSOC_CONFIRMER);
        if (!CollectionUtils.isEmpty(assocs)) {
            NodeRef confirmerRef = assocs.get(0).getTargetRef();
            ObjectNode confirmerNode = objectMapper.createObjectNode();
            fillBaseNodeInfo(confirmerRef, confirmerNode);
            objectNode.put("confirmer", confirmerNode);

        }
    }

    /**
     * Fill additional performers info
     * @param additionalDataRef Additional data reference
     * @param objectNode Object node
     */
    private void fillAdditionalPerformersInfo(NodeRef additionalDataRef, ObjectNode objectNode) {
        List<AssociationRef> assocs = nodeService.getTargetAssocs(additionalDataRef, EventModel.ASSOC_PERFORMERS);
        ArrayNode performersNode = objectMapper.createArrayNode();
        for (AssociationRef associationRef : assocs) {
            ObjectNode performerNode = objectMapper.createObjectNode();
            fillBaseNodeInfo(associationRef.getTargetRef(), performerNode);
            performersNode.add(performerNode);
        }
        objectNode.put("performers", performersNode);
    }

    /**
     * Fill additional info
     * @param dtoType Dto type
     * @param caseModelRef Case model node reference
     * @param objectNode Object node
     */
    private void fillAdditionalInfo(String dtoType, NodeRef caseModelRef, ObjectNode objectNode) {
        if (StageDto.DTO_TYPE.equals(dtoType)) {
            fillAdditionalStageInfo(caseModelRef, objectNode);
        }
        if (ExecutionScriptDto.DTO_TYPE.equals(dtoType)) {
            fillAdditionalExecutionScriptInfo(caseModelRef, objectNode);
        }
        if (FailDto.DTO_TYPE.equals(dtoType)) {
            fillAdditionalFailInfo(caseModelRef, objectNode);
        }
        if (MailDto.DTO_TYPE.equals(dtoType)) {
            fillAdditionalMailInfo(caseModelRef, objectNode);
        }
        if (SetProcessVariableDto.DTO_TYPE.equals(dtoType)) {
            fillAdditionalSetProcessVariableInfo(caseModelRef, objectNode);
        }
        if (SetPropertyValueDto.DTO_TYPE.equals(dtoType)) {
            fillAdditionalSetPropertyValueInfo(caseModelRef, objectNode);
        }
        if (SetCaseStatusDto.DTO_TYPE.equals(dtoType)) {
            fillAdditionalSetCaseStatusInfo(caseModelRef, objectNode);
        }
        if (CaseTimerDto.DTO_TYPE.equals(dtoType)) {
            fillAdditionalCaseTimerInfo(caseModelRef, objectNode);
        }
        if (CaseTaskDto.DTO_TYPE.equals(dtoType)) {
            fillAdditionalCaseTaskInfo(caseModelRef, objectNode);
        }
    }

    /**
     * Fill additional stage info
     * @param caseModelRef Case model node reference
     * @param objectNode Object node
     */
    private void fillAdditionalStageInfo(NodeRef caseModelRef, ObjectNode objectNode) {
        objectNode.put("documentStatus", (String) nodeService.getProperty(caseModelRef, StagesModel.PROP_DOCUMENT_STATUS));
        List<AssociationRef> assocs = nodeService.getTargetAssocs(caseModelRef, StagesModel.ASSOC_CASE_STATUS);
        if (!CollectionUtils.isEmpty(assocs)) {
            NodeRef statusRef = assocs.get(0).getTargetRef();
            if (statusRef != null) {
                ObjectNode statusObjectNode = objectMapper.createObjectNode();
                fillBaseNodeInfo(statusRef, statusObjectNode);
                /* Set status */
                objectNode.set("caseStatus", statusObjectNode);
            }
        }
    }

    /**
     * Fill additional execution script info
     * @param caseModelRef Case model node reference
     * @param objectNode Object node
     */
    private void fillAdditionalExecutionScriptInfo(NodeRef caseModelRef, ObjectNode objectNode) {
        objectNode.put("executeScript", (String) nodeService.getProperty(caseModelRef, ActionModel.ExecuteScript.PROP_SCRIPT));
    }

    /**
     * Fill additional info fail info
     * @param caseModelRef Case model node reference
     * @param objectNode Object node
     */
    private void fillAdditionalFailInfo(NodeRef caseModelRef, ObjectNode objectNode) {
        objectNode.put("failMessage", (String) nodeService.getProperty(caseModelRef, ActionModel.Fail.PROP_MESSAGE));
    }

    /**
     * Fill additional info mail info
     * @param caseModelRef Case model node reference
     * @param objectNode Object node
     */
    private void fillAdditionalMailInfo(NodeRef caseModelRef, ObjectNode objectNode) {
        objectNode.put("mailTo", (String) nodeService.getProperty(caseModelRef, ActionModel.Mail.PROP_TO));
        objectNode.put("toMany", (String) nodeService.getProperty(caseModelRef, ActionModel.Mail.PROP_TO_MANY));
        objectNode.put("subject", (String) nodeService.getProperty(caseModelRef, ActionModel.Mail.PROP_SUBJECT));
        objectNode.put("fromUser", (String) nodeService.getProperty(caseModelRef, ActionModel.Mail.PROP_FROM));
        objectNode.put("mailText", (String) nodeService.getProperty(caseModelRef, ActionModel.Mail.PROP_TEXT));
        objectNode.put("mailHtml", (String) nodeService.getProperty(caseModelRef, ActionModel.Mail.PROP_HTML));
    }

    /**
     * Fill additional info set process variable info
     * @param caseModelRef Case model node reference
     * @param objectNode Object node
     */
    private void fillAdditionalSetProcessVariableInfo(NodeRef caseModelRef, ObjectNode objectNode) {
        objectNode.put("processVariableName", (String) nodeService.getProperty(caseModelRef, ActionModel.SetProcessVariable.PROP_VARIABLE));
        objectNode.put("processVariableValue", (String) nodeService.getProperty(caseModelRef, ActionModel.SetProcessVariable.PROP_VALUE));
    }

    /**
     * Fill additional info set property value info
     * @param caseModelRef Case model node reference
     * @param objectNode Object node
     */
    private void fillAdditionalSetPropertyValueInfo(NodeRef caseModelRef, ObjectNode objectNode) {
        QName propertyName = (QName) nodeService.getProperty(caseModelRef, ActionModel.SetPropertyValue.PROP_PROPERTY);
        objectNode.put("propertyFullName", propertyName != null ? propertyName.toString() : "");
        objectNode.put("propertyValue", (String) nodeService.getProperty(caseModelRef, ActionModel.SetPropertyValue.PROP_VALUE));
    }

    /**
     * Fill additional info set case status info
     * @param caseModelRef Case model node reference
     * @param objectNode Object node
     */
    private void fillAdditionalSetCaseStatusInfo(NodeRef caseModelRef, ObjectNode objectNode) {
        List<AssociationRef> assocs = nodeService.getTargetAssocs(caseModelRef, ActionModel.SetCaseStatus.ASSOC_STATUS);
        if (!CollectionUtils.isEmpty(assocs)) {
            NodeRef statusRef = assocs.get(0).getTargetRef();
            if (statusRef != null) {
                ObjectNode statusObjectNode = objectMapper.createObjectNode();
                fillBaseNodeInfo(statusRef, statusObjectNode);
                /* Set status */
                objectNode.set("caseStatus", statusObjectNode);
            }
        }
    }

    /**
     * Fill additional info case timer info
     * @param caseModelRef Case model node reference
     * @param objectNode Object node
     */
    private void fillAdditionalCaseTimerInfo(NodeRef caseModelRef, ObjectNode objectNode) {
        objectNode.put("expressionType", (String) nodeService.getProperty(caseModelRef, CaseTimerModel.PROP_EXPRESSION_TYPE));
        objectNode.put("timerExpression", (String) nodeService.getProperty(caseModelRef, CaseTimerModel.PROP_TIMER_EXPRESSION));
        objectNode.put("datePrecision", (String) nodeService.getProperty(caseModelRef, CaseTimerModel.PROP_DATE_PRECISION));
        objectNode.put("computedExpression", (String) nodeService.getProperty(caseModelRef, CaseTimerModel.PROP_COMPUTED_EXPRESSION));
        objectNode.put("repeatCounter", (Integer) nodeService.getProperty(caseModelRef, CaseTimerModel.PROP_REPEAT_COUNTER));
        if (nodeService.getProperty(caseModelRef, CaseTimerModel.PROP_OCCUR_DATE) != null) {
            objectNode.put("occurDate", DATE_TIME_FORMAT.get().format((Date) nodeService.getProperty(caseModelRef, CaseTimerModel.PROP_OCCUR_DATE)));
        }
    }

    /**
     * Fill additional info case task info
     * @param caseModelRef Case model node reference
     * @param objectNode Object node
     */
    private void fillAdditionalCaseTaskInfo(NodeRef caseModelRef, ObjectNode objectNode) {
        QName type = nodeService.getType(caseModelRef);
        objectNode.put("taskTypeFullName", type.toString());
        objectNode.put("workflowDefinitionName", (String) nodeService.getProperty(caseModelRef, ICaseTaskModel.PROP_WORKFLOW_DEFINITION_NAME));
        objectNode.put("workflowInstanceId", (String) nodeService.getProperty(caseModelRef, ICaseTaskModel.PROP_WORKFLOW_INSTANCE_ID));
        if (nodeService.getProperty(caseModelRef, ICaseTaskModel.PROP_DEADLINE) != null) {
            objectNode.put("dueDate", DATE_TIME_FORMAT.get().format((Date) nodeService.getProperty(caseModelRef, ICaseTaskModel.PROP_DEADLINE)));
        }
        objectNode.put("priority", (Integer) nodeService.getProperty(caseModelRef, ICaseTaskModel.PROP_PRIORITY));
        /* BPM Package */
        List<AssociationRef> packageAssocs = nodeService.getTargetAssocs(caseModelRef, ICaseTaskModel.ASSOC_WORKFLOW_PACKAGE);
        if (!CollectionUtils.isEmpty(packageAssocs)) {
            if (packageAssocs.get(0).getTargetRef() != null) {
                NodeRef packageRef = packageAssocs.get(0).getTargetRef();
                ObjectNode packageNode = objectMapper.createObjectNode();
                fillBaseNodeInfo(packageRef, packageNode);
                packageNode.put("workflowInstanceId", (String) nodeService.getProperty(packageRef, BpmPackageModel.PROP_WORKFLOW_INSTANCE_ID));
                packageNode.put("workflowDefinitionName", (String) nodeService.getProperty(packageRef, BpmPackageModel.PROP_WORKFLOW_DEFINITION_NAME));
                packageNode.put("isSystemPackage", (Boolean) nodeService.getProperty(packageRef, BpmPackageModel.PROP_IS_SYSTEM_PACKAGE));

                objectNode.set("bpmPackage", packageNode);
            }
        }
        /* Task properties */
        ArrayNode taskPropertiesNode = objectMapper.createArrayNode();
        Map<QName, Serializable> properties = nodeService.getProperties(caseModelRef);
        List<QName> excludeProperties = Arrays.asList(EXCLUDE_PROPERTIES);
        for (Map.Entry<QName, Serializable> entry : properties.entrySet()) {
            QName key = entry.getKey();
            if (!excludeProperties.contains(key)) {
                Serializable value = entry.getValue();
                if (value != null) {
                    ObjectNode propertyNode = createPropertyObjectNode(key, value);
                    if (propertyNode != null) {
                        taskPropertiesNode.add(propertyNode);
                    }
                }
            }
        }
        objectNode.put("taskProperties", taskPropertiesNode.toString());
        /* Task assocs */
        List<QName> excludeAssocs = Arrays.asList(EXCLUDE_ASSOCS);
        List<AssociationRef> assocs =  nodeService.getTargetAssocs(caseModelRef, RegexQNamePattern.MATCH_ALL);
        ArrayNode assocsNode = objectMapper.createArrayNode();
        for (AssociationRef associationRef : assocs) {
            if (!excludeAssocs.contains(associationRef.getTypeQName())) {
                ObjectNode assocNode = objectMapper.createObjectNode();
                assocNode.put("assocType", associationRef.getTypeQName().toString());
                assocNode.put("nodeRef", associationRef.getTargetRef().getId());
                assocsNode.add(assocNode);
            }
        }
        objectNode.put("taskAssocs", assocsNode.toString());
    }

    /**
     * Create property object node
     * @param typeName Type name
     * @param value Value
     * @return Property type name
     */
    private ObjectNode createPropertyObjectNode(QName typeName, Serializable value) {
        ObjectNode resultNode = objectMapper.createObjectNode();
        resultNode.put("typeName", typeName.toString());
        resultNode.put("valueClass", value.getClass().getName());
        if (value instanceof Date) {
            resultNode.put("value", DATE_TIME_FORMAT.get().format((Date) value));
            return resultNode;
        }
        if (value instanceof Number || value instanceof String || value instanceof Boolean) {
            resultNode.put("value", value.toString());
            return resultNode;
        }
        return null;
    }

    /**
     * Get case model type
     * @param nodeType Node type
     * @return Case model type as string
     */
    private String getCaseModelType(QName nodeType) {
        if (nodeType == null) {
            throw new RuntimeException("Node Type must not be null");
        }
        if (dictionaryService.isSubClass(nodeType, StagesModel.TYPE_STAGE)) {
            return StageDto.DTO_TYPE;
        }
        if (dictionaryService.isSubClass(nodeType, ActionModel.ExecuteScript.TYPE)) {
            return ExecutionScriptDto.DTO_TYPE;
        }
        if (dictionaryService.isSubClass(nodeType, ActionModel.Fail.TYPE)) {
            return FailDto.DTO_TYPE;
        }
        if (dictionaryService.isSubClass(nodeType, ActionModel.Mail.TYPE)) {
            return MailDto.DTO_TYPE;
        }
        if (dictionaryService.isSubClass(nodeType, ActionModel.SetProcessVariable.TYPE)) {
            return SetProcessVariableDto.DTO_TYPE;
        }
        if (dictionaryService.isSubClass(nodeType, ActionModel.SetPropertyValue.TYPE)) {
            return SetPropertyValueDto.DTO_TYPE;
        }
        if (dictionaryService.isSubClass(nodeType, ActionModel.SetCaseStatus.TYPE)) {
            return SetCaseStatusDto.DTO_TYPE;
        }
        if (dictionaryService.isSubClass(nodeType, CaseTimerModel.TYPE_TIMER)) {
            return CaseTimerDto.DTO_TYPE;
        }
        if (dictionaryService.isSubClass(nodeType, ICaseTaskModel.TYPE_TASK)) {
            return CaseTaskDto.DTO_TYPE;
        }
        return null;
    }

    /**
     * Get event type
     * @param nodeType Node type
     * @return Event type as string
     */
    private String getEventType(QName nodeType) {
        if (nodeType == null) {
            throw new RuntimeException("Node Type must not be null");
        }
        if (dictionaryService.isSubClass(nodeType, ICaseEventModel.TYPE_ACTIVITY_STARTED_EVENT)) {
            return ActivityStartedEventDto.DTO_TYPE;
        }
        if (dictionaryService.isSubClass(nodeType, ICaseEventModel.TYPE_ACTIVITY_STOPPED_EVENT)) {
            return ActivityStoppedEventDto.DTO_TYPE;
        }
        if (dictionaryService.isSubClass(nodeType, ICaseEventModel.TYPE_STAGE_CHILDREN_STOPPED)) {
            return StageChildrenStoppedEventDto.DTO_TYPE;
        }
        if (dictionaryService.isSubClass(nodeType, ICaseEventModel.TYPE_CASE_CREATED)) {
            return CaseCreatedEventDto.DTO_TYPE;
        }
        if (dictionaryService.isSubClass(nodeType, ICaseEventModel.TYPE_CASE_PROPERTIES_CHANGED)) {
            return CasePropertiesChangedEventDto.DTO_TYPE;
        }
        if (dictionaryService.isSubClass(nodeType, EventModel.TYPE_USER_ACTION)) {
            return UserActionEventDto.DTO_TYPE;
        }
        return EventDto.DTO_TYPE;
    }

    /**
     * Get condition type
     * @param nodeType Node type
     * @return Condition type as string
     */
    private String getConditionType(QName nodeType) {
        if (nodeType == null) {
            throw new RuntimeException("Node Type must not be null");
        }
        if (dictionaryService.isSubClass(nodeType, ConditionModel.CompareProcessVariable.TYPE)) {
            return CompareProcessVariableConditionDto.DTO_TYPE;
        }
        if (dictionaryService.isSubClass(nodeType, ConditionModel.ComparePropertyValue.TYPE)) {
            return ComparePropertyValueConditionDto.DTO_TYPE;
        }
        if (dictionaryService.isSubClass(nodeType, ConditionModel.EvaluateScript.TYPE)) {
            return EvaluateScriptConditionDto.DTO_TYPE;
        }
        if (dictionaryService.isSubClass(nodeType, ConditionModel.UserHasPermission.TYPE)) {
            return UserHasPermissionConditionDto.DTO_TYPE;
        }
        if (dictionaryService.isSubClass(nodeType, ConditionModel.UserInDocument.TYPE)) {
            return UserInDocumentConditionDto.DTO_TYPE;
        }
        if (dictionaryService.isSubClass(nodeType, ConditionModel.UserInGroup.TYPE)) {
            return UserInGroupConditionDto.DTO_TYPE;
        }
        return ConditionDto.DTO_TYPE;
    }

    /**
     * Get additional item type
     * @param nodeType Node type
     * @return Additional item type as string
     */
    private String getAdditionalItemType(QName nodeType) {
        if (nodeType == null) {
            throw new RuntimeException("Node Type must not be null");
        }
        if (dictionaryService.isSubClass(nodeType, EventModel.TYPE_ADDITIONAL_CONFIRMER)) {
            return AdditionalConfirmerDto.DTO_TYPE;
        }
        if (dictionaryService.isSubClass(nodeType, EventModel.TYPE_ADDITIONAL_PERFORMERS)) {
            return AdditionalPerformersDto.DTO_TYPE;
        }
        return AdditionalDataItemDto.DTO_TYPE;
    }


    /**
     * Get case model by node uuid
     * @param nodeUUID Node uuid
     * @param verboseInformation Verbose information
     * @return Case model or null
     */
    @Override
    public CaseModelDto getCaseModelByNodeUUID(String nodeUUID,  Boolean verboseInformation) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(GET_CASE_MODEL_BY_NODE_ID + nodeUUID)
                .queryParam("verboseInformation", verboseInformation);
        return getForObject(builder.build().toString(),
                new LinkedMultiValueMap<>(), CaseModelDto.class);
    }

    /**
     * Get case models by node reference
     * @param nodeRef Node reference
     * @param verboseInformation Verbose information
     * @return List of case model
     */
    @Override
    public List<CaseModelDto> getCaseModelsByNodeRef(NodeRef nodeRef,  Boolean verboseInformation) {
        if (nodeRef == null) {
            return Collections.emptyList();
        }
        if (nodeService.exists(nodeRef)) {
            if (dictionaryService.isSubClass(
                    nodeService.getType(nodeRef),
                    IdocsModel.TYPE_DOC)) {
                UriComponentsBuilder builder = UriComponentsBuilder.fromPath(GET_CASE_MODELS_BY_DOCUMENT_ID + nodeRef.getId())
                        .queryParam("verboseInformation", verboseInformation);
                String result = getForObject(builder.build().toString(),
                        new LinkedMultiValueMap<>(), String.class);
                return convertJsonToCaseModels(result);
            } else {
                return Collections.emptyList();
            }
        } else {
            UriComponentsBuilder builder = UriComponentsBuilder.fromPath(GET_CASE_MODELS_BY_PARENT_CASE_MODEL_ID + nodeRef.getId())
                    .queryParam("verboseInformation", verboseInformation);
            String result = getForObject(builder.build().toString(),
                    new LinkedMultiValueMap<>(), String.class);
            return convertJsonToCaseModels(result);
        }
    }

    /**
     * Convert json to case models
     * @param jsonString Json string
     * @return List of case models
     */
    private List<CaseModelDto> convertJsonToCaseModels(String jsonString) {
        try {
            if (StringUtils.isEmpty(jsonString)) {
                return Collections.emptyList();
            }
            CaseModelDto[] parseResult = objectMapper.readValue(jsonString, CaseModelDto[].class);
            return Arrays.asList(parseResult);
        } catch (IOException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    /**
     * Delete case models by document id
     * @param documentId Document id
     */
    @Override
    public void deleteCaseModelsByDocumentId(String documentId) {
        restTemplate.delete(properties.getProperty(CASE_MODELS_SERVICE_HOST) + DELETE_CASE_MODELS_BY_DOCUMENT_ID + documentId);
    }

    /**
     * Get for object
     * @param serviceMethod Service method
     * @param params Params
     * @param objectClass Return object class
     * @param <T>
     * @return Return object
     */
    protected <T> T getForObject(String serviceMethod, MultiValueMap<String, Object> params, Class<T> objectClass) {
        return restTemplate.getForObject(properties.getProperty(CASE_MODELS_SERVICE_HOST) + serviceMethod, objectClass, params);
    }

    /**
     * Post for object
     * @param serviceMethod Service method
     * @param requestBody Request body
     * @param objectClass Return object class
     * @param <T>
     * @return Return object
     */
    protected <T> T postForObject(String serviceMethod, String requestBody, Class<T> objectClass) {
        /* Header */
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        /* Body params */
        HttpEntity requestEntity = new HttpEntity<>(requestBody, headers);
        return restTemplate.postForObject(properties.getProperty(CASE_MODELS_SERVICE_HOST) + serviceMethod, requestEntity, objectClass);
    }

    private void runWatch(StopWatch stopWatch, String taskName) {
        if (!stopWatch.isRunning()) {
            stopWatch.start(taskName);
        }
    }

    /**
     * Set node service
     * @param nodeService Node service
     */
    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    /**
     * Set dictionary service
     * @param dictionaryService Dictionary service
     */
    public void setDictionaryService(DictionaryService dictionaryService) {
        this.dictionaryService = dictionaryService;
    }

    /**
     * Set transaction service
     * @param transactionService Transaction service
     */
    public void setTransactionService(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    /**
     * Set rest template
     * @param restTemplate Rest template
     */
    public void setRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Set lock service
     * @param lockService Lock service
     */
    public void setLockService(LockService lockService) {
        this.lockService = lockService;
    }
}

