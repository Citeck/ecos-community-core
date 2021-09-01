package ru.citeck.ecos.cases.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.springframework.util.CollectionUtils;
import ru.citeck.ecos.cases.RemoteCaseModelService;
import ru.citeck.ecos.cases.RemoteRestoreCaseModelService;
import ru.citeck.ecos.dto.*;
import ru.citeck.ecos.model.*;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Remote restore case model service
 */
@Slf4j
public class RemoteRestoreCaseModelServiceImpl implements RemoteRestoreCaseModelService {

    /**
     * Object mapper
     */
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Workspace prefix
     */
    private static final String WORKSPACE_PREFIX = "workspace://SpacesStore/";

    /**
     * Datetime format
     */
    private static final ThreadLocal<SimpleDateFormat> DATE_TIME_FORMAT = ThreadLocal.withInitial(() ->
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));

    /**
     * Node service
     */
    private NodeService nodeService;

    /**
     * Remote case model service
     */
    private RemoteCaseModelService remoteCaseModelService;

    /**
     * Restore case models
     * @param documentRef Document reference
     */
    @Override
    public void restoreCaseModels(NodeRef documentRef) {
        if (!nodeService.exists(documentRef)) {
            return;
        }
        List<CaseModelDto> caseModels = remoteCaseModelService.getCaseModelsByNodeRef(documentRef, true);
        for (CaseModelDto caseModelDto : caseModels) {
            List<CaseModelDto> childCaseModels = loadChildCases(caseModelDto);
            caseModelDto.setChildCases(childCaseModels);
        }
        /* Restore data */
        Map<CaseModelDto, NodeRef> restoreMap = new HashMap<>();
        for (CaseModelDto caseModelDto : caseModels) {
            restoreCaseModelNodeRef(caseModelDto, documentRef, restoreMap);
        }
        /* Restore events data */
        for (CaseModelDto caseModelDto : caseModels) {
            restoreCaseEventsNodeRefs(caseModelDto, restoreMap);
        }
        /* Remote useless data and set flags */
        nodeService.setProperty(documentRef, IdocsModel.PROP_DOCUMENT_CASE_COMPLETED, false);
        nodeService.setProperty(documentRef, IdocsModel.PROP_CASE_MODELS_SENT, false);
        remoteCaseModelService.deleteCaseModelsByDocumentId(documentRef.getId());
    }

    /**
     * Load child cases
     * @param parentCaseModel Parent case model
     * @return List of child cases
     */
    private List<CaseModelDto> loadChildCases(CaseModelDto parentCaseModel) {
        List<CaseModelDto> caseModels = remoteCaseModelService.getCaseModelsByNodeRef(new NodeRef(WORKSPACE_PREFIX + parentCaseModel.getNodeUUID()), true);
        for (CaseModelDto caseModelDto : caseModels) {
            List<CaseModelDto> childCaseModels = loadChildCases(caseModelDto);
            caseModelDto.setChildCases(childCaseModels);
        }
        return caseModels;
    }

    /**
     * Restore case model node reference
     * @param caseModelDto Case model data transfer object
     * @param parentNodeRef Parent node reference
     * @param restoreMap Restore map
     */
    private void restoreCaseModelNodeRef(CaseModelDto caseModelDto, NodeRef parentNodeRef, Map<CaseModelDto, NodeRef> restoreMap) {
        /* Create properties map */
        Map<QName, Serializable> properties = new HashMap<>();
        properties.put(ContentModel.PROP_CREATED, caseModelDto.getCreated());
        properties.put(ContentModel.PROP_CREATOR, caseModelDto.getCreator());
        properties.put(ContentModel.PROP_MODIFIED, caseModelDto.getModified());
        properties.put(ContentModel.PROP_MODIFIER, caseModelDto.getModifier());
        properties.put(ContentModel.PROP_TITLE, caseModelDto.getTitle());
        properties.put(ContentModel.PROP_DESCRIPTION, caseModelDto.getDescription());
        /* Case model properties */
        properties.put(ActivityModel.PROP_PLANNED_START_DATE, caseModelDto.getPlannedStartDate());
        properties.put(ActivityModel.PROP_PLANNED_END_DATE, caseModelDto.getPlannedEndDate());
        properties.put(ActivityModel.PROP_ACTUAL_START_DATE, caseModelDto.getActualStartDate());
        properties.put(ActivityModel.PROP_ACTUAL_END_DATE, caseModelDto.getActualEndDate());
        properties.put(ActivityModel.PROP_EXPECTED_PERFORM_TIME, caseModelDto.getExpectedPerformTime());
        properties.put(ActivityModel.PROP_MANUAL_STARTED, caseModelDto.getManualStarted());
        properties.put(ActivityModel.PROP_MANUAL_STOPPED, caseModelDto.getManualStopped());
        properties.put(ActivityModel.PROP_INDEX, caseModelDto.getIndex());
        properties.put(ActivityModel.PROP_AUTO_EVENTS, caseModelDto.getAutoEvents());
        properties.put(ActivityModel.PROP_REPEATABLE, caseModelDto.getRepeatable());
        properties.put(ActivityModel.PROP_TYPE_VERSION, caseModelDto.getTypeVersion());
        /* Create node */
        QName caseType = getCaseModelType(caseModelDto);
        ChildAssociationRef childAssociationRef = nodeService.createNode(parentNodeRef,
                ActivityModel.ASSOC_ACTIVITIES, ActivityModel.ASSOC_ACTIVITIES, caseType, properties);
        NodeRef caseModelRef = childAssociationRef.getChildRef();
        fillAdditionalInfo(caseModelDto, caseModelRef);
        restoreMap.put(caseModelDto, caseModelRef);
        /* Restore child cases */
        for (CaseModelDto childCaseModel : caseModelDto.getChildCases()) {
            restoreCaseModelNodeRef(childCaseModel, caseModelRef, restoreMap);
        }
    }

    /**
     * Restore case events node references
     * @param caseModelDto Case model data transfer object
     * @param restoreMap Restore map
     */
    private void restoreCaseEventsNodeRefs(CaseModelDto caseModelDto, Map<CaseModelDto, NodeRef> restoreMap) {
        NodeRef caseModelRef = restoreMap.get(caseModelDto);
        if (caseModelRef == null) {
            return;
        }
        /* Start events */
        for (EventDto eventDto : caseModelDto.getStartEvents()) {
            restoreEvent(eventDto, caseModelRef, ICaseEventModel.ASSOC_ACTIVITY_START_EVENTS, restoreMap);
        }

        /* End events */
        for (EventDto eventDto : caseModelDto.getEndEvents()) {
            restoreEvent(eventDto, caseModelRef, ICaseEventModel.ASSOC_ACTIVITY_END_EVENTS, restoreMap);
        }

        /* Restart events */
        for (EventDto eventDto : caseModelDto.getRestartEvents()) {
            restoreEvent(eventDto, caseModelRef, ICaseEventModel.ASSOC_ACTIVITY_RESTART_EVENTS, restoreMap);
        }

        /* Reset events */
        for (EventDto eventDto : caseModelDto.getResetEvents()) {
            restoreEvent(eventDto, caseModelRef, ICaseEventModel.ASSOC_ACTIVITY_RESET_EVENTS, restoreMap);
        }
        /* Restore child cases */
        for (CaseModelDto childCaseModel : caseModelDto.getChildCases()) {
            restoreCaseEventsNodeRefs(childCaseModel, restoreMap);
        }
    }

    /**
     * Restore event
     * @param eventDto Event data transfer object
     * @param parentCaseModelRef Parent case model reference
     * @param assocType Assoc type
     * @param restoreMap Restore map
     */
    private void restoreEvent(EventDto eventDto, NodeRef parentCaseModelRef, QName assocType, Map<CaseModelDto, NodeRef> restoreMap) {
        /* Create properties map */
        Map<QName, Serializable> properties = new HashMap<>();
        properties.put(ContentModel.PROP_CREATED, eventDto.getCreated());
        properties.put(ContentModel.PROP_CREATOR, eventDto.getCreator());
        properties.put(ContentModel.PROP_MODIFIED, eventDto.getModified());
        properties.put(ContentModel.PROP_MODIFIER, eventDto.getModifier());
        properties.put(ContentModel.PROP_TITLE, eventDto.getTitle());
        properties.put(ContentModel.PROP_DESCRIPTION, eventDto.getDescription());
        /* Create node */
        QName eventType = getEventType(eventDto);
        ChildAssociationRef childAssociationRef = nodeService.createNode(parentCaseModelRef,
                assocType, assocType, eventType, properties);
        NodeRef eventRef = childAssociationRef.getChildRef();
        /* Source */
        NodeRef sourceRef = null;
        if (eventDto.getSourceCaseId() != null) {
            if (eventDto.getIsSourceCase() != null && eventDto.getIsSourceCase()) {
                sourceRef = getCaseModelBySourceId(eventDto.getSourceCaseId(), restoreMap);
            } else {
                sourceRef = new NodeRef(WORKSPACE_PREFIX + eventDto.getSourceCaseId());
                if (!nodeService.exists(sourceRef)) {
                    sourceRef = null;
                }
            }
        }
        if (sourceRef != null) {
            nodeService.createAssociation(eventRef, sourceRef, EventModel.ASSOC_EVENT_SOURCE);
        }
        fillAdditionalInfo(eventDto, eventRef);
        /* Conditions */
        for (ConditionDto conditionDto : eventDto.getConditions()) {
            restoreCondition(conditionDto, eventRef);
        }
    }

    /**
     * Restore condition
     * @param conditionDto Condition data transfer object
     * @param parentEventRef Parent event node reference
     */
    private void restoreCondition(ConditionDto conditionDto, NodeRef parentEventRef) {
        /* Create properties map */
        Map<QName, Serializable> properties = new HashMap<>();
        properties.put(ContentModel.PROP_CREATED, conditionDto.getCreated());
        properties.put(ContentModel.PROP_CREATOR, conditionDto.getCreator());
        properties.put(ContentModel.PROP_MODIFIED, conditionDto.getModified());
        properties.put(ContentModel.PROP_MODIFIER, conditionDto.getModifier());
        properties.put(ContentModel.PROP_TITLE, conditionDto.getTitle());
        properties.put(ContentModel.PROP_DESCRIPTION, conditionDto.getDescription());
        /* Create node */
        QName conditionType = getConditionType(conditionDto);
        ChildAssociationRef childAssociationRef = nodeService.createNode(parentEventRef,
                EventModel.ASSOC_CONDITIONS, EventModel.ASSOC_CONDITIONS, conditionType, properties);
        NodeRef conditionRef = childAssociationRef.getChildRef();
        fillAdditionalInfo(conditionDto, conditionRef);
    }

    /**
     * Get case model by source id from restore map
     * @param sourceId Source id
     * @param restoreMap Restore map
     * @return Case model node reference
     */
    private NodeRef getCaseModelBySourceId(String sourceId, Map<CaseModelDto, NodeRef> restoreMap) {
        for (Map.Entry<CaseModelDto, NodeRef> entry : restoreMap.entrySet()) {
            if (sourceId.equals(entry.getKey().getNodeUUID())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Fill additional info
     * @param caseModelDto Case model data transfer object
     * @param caseModelRef Case model node reference
     */
    private void fillAdditionalInfo(CaseModelDto caseModelDto, NodeRef caseModelRef) {
        if (caseModelDto instanceof StageDto) {
            fillAdditionalStageInfo((StageDto) caseModelDto, caseModelRef);
            return;
        }
        if (caseModelDto instanceof ExecutionScriptDto) {
            fillAdditionalExecutionScriptInfo((ExecutionScriptDto) caseModelDto, caseModelRef);
            return;
        }
        if (caseModelDto instanceof FailDto) {
            fillAdditionalFailInfo((FailDto) caseModelDto, caseModelRef);
            return;
        }
        if (caseModelDto instanceof MailDto) {
            fillAdditionalMailInfo((MailDto) caseModelDto, caseModelRef);
            return;
        }
        if (caseModelDto instanceof SetProcessVariableDto) {
            fillAdditionalSetProcessVariableInfo((SetProcessVariableDto) caseModelDto, caseModelRef);
            return;
        }
        if (caseModelDto instanceof SetPropertyValueDto) {
            fillAdditionalSetPropertyValueInfo((SetPropertyValueDto) caseModelDto, caseModelRef);
            return;
        }
        if (caseModelDto instanceof SetCaseStatusDto) {
            fillAdditionalSetCaseStatusInfo((SetCaseStatusDto) caseModelDto, caseModelRef);
            return;
        }
        if (caseModelDto instanceof CaseTimerDto) {
            fillAdditionalCaseTimerInfo((CaseTimerDto) caseModelDto, caseModelRef);
            return;
        }
        if (caseModelDto instanceof CaseTaskDto) {
            fillAdditionalCaseTaskInfo((CaseTaskDto) caseModelDto, caseModelRef);
            return;
        }
    }

    /**
     * Fill additional info
     * @param eventDto Event data transfer object
     * @param eventRef Event node reference
     */
    private void fillAdditionalInfo(EventDto eventDto, NodeRef eventRef) {
        if (eventDto instanceof UserActionEventDto) {
            fillAdditionalUserActionEventInfo((UserActionEventDto) eventDto, eventRef);
        }
    }

    /**
     * Fill additional info
     * @param conditionDto Condition data transfer object
     * @param conditionRef Condition node reference
     */
    private void fillAdditionalInfo(ConditionDto conditionDto, NodeRef conditionRef) {
        if (conditionDto instanceof CompareProcessVariableConditionDto) {
            fillAdditionalCompareProcessVariableConditionInfo(
                    (CompareProcessVariableConditionDto) conditionDto,
                    conditionRef
            );
            return;
        }
        if (conditionDto instanceof ComparePropertyValueConditionDto) {
            fillAdditionalComparePropertyValueConditionInfo(
                    (ComparePropertyValueConditionDto) conditionDto,
                    conditionRef
            );
            return;
        }
        if (conditionDto instanceof EvaluateScriptConditionDto) {
            fillAdditionalEvaluateScriptConditionInfo(
                    (EvaluateScriptConditionDto) conditionDto,
                    conditionRef
            );
            return;
        }
        if (conditionDto instanceof UserHasPermissionConditionDto) {
            fillAdditionalUserHasPermissionConditionInfo(
                    (UserHasPermissionConditionDto) conditionDto,
                    conditionRef
            );
            return;
        }
        if (conditionDto instanceof UserInDocumentConditionDto) {
            fillAdditionalUserInDocumentConditionInfo(
                    (UserInDocumentConditionDto) conditionDto,
                    conditionRef
            );
            return;
        }
        if (conditionDto instanceof UserInGroupConditionDto) {
            fillAdditionalUserInGroupConditionInfo(
                    (UserInGroupConditionDto) conditionDto,
                    conditionRef
            );
            return;
        }
    }

    /**
     * Fill additional user action event info
     * @param eventRef Event node reference
     * @param eventDto Event data transfer object
     */
    private void fillAdditionalUserActionEventInfo(UserActionEventDto eventDto, NodeRef eventRef) {
        nodeService.setProperty(eventRef, EventModel.PROP_ADDITIONAL_DATA_TYPE, eventDto.getAdditionalDataType());
        nodeService.setProperty(eventRef, EventModel.PROP_CONFIRMATION_MESSAGE, eventDto.getConfirmationMessage());
        nodeService.setProperty(eventRef, EventModel.PROP_SUCCESS_MESSAGE, eventDto.getSuccessMessage());
        nodeService.setProperty(eventRef, EventModel.PROP_SUCCESS_MESSAGE_SPAN_CLASS, eventDto.getSuccessMessageSpanClass());
        /* Roles */
        for (RoleDto roleDto : eventDto.getRoles()) {
            NodeRef roleNodeRef = new NodeRef(WORKSPACE_PREFIX + roleDto.getNodeUUID());
            if (nodeService.exists(roleNodeRef)) {
                nodeService.createAssociation(eventRef, roleNodeRef, EventModel.ASSOC_AUTHORIZED_ROLES);
            }
        }
        /* Additional data item */
        for (AdditionalDataItemDto dataItemDto : eventDto.getAdditionalDataItems()) {
            restoreAdditionalDataItem(dataItemDto, eventRef);
        }
    }

    /**
     * Restore additional data item
     * @param dataItemDto Additional data item data transfer object
     * @param eventRef Parent event reference
     */
    private void restoreAdditionalDataItem(AdditionalDataItemDto dataItemDto, NodeRef eventRef) {
        /* Create properties map */
        Map<QName, Serializable> properties = new HashMap<>();
        properties.put(ContentModel.PROP_CREATED, dataItemDto.getCreated());
        properties.put(ContentModel.PROP_CREATOR, dataItemDto.getCreator());
        properties.put(ContentModel.PROP_MODIFIED, dataItemDto.getModified());
        properties.put(ContentModel.PROP_MODIFIER, dataItemDto.getModifier());
        properties.put(ContentModel.PROP_TITLE, dataItemDto.getTitle());
        properties.put(ContentModel.PROP_DESCRIPTION, dataItemDto.getDescription());
        properties.put(EventModel.PROP_COMMENT, dataItemDto.getComment());
        /* Create node */
        QName dataItemType = getAdditionalItemType(dataItemDto);
        ChildAssociationRef childAssociationRef = nodeService.createNode(eventRef,
                EventModel.ASSOC_ADDITIONAL_DATA_ITEMS, EventModel.ASSOC_ADDITIONAL_DATA_ITEMS, dataItemType, properties);
        NodeRef conditionRef = childAssociationRef.getChildRef();
        fillAdditionalInfo(dataItemDto, conditionRef);
    }

    /**
     * Fill additional data item info
     * @param dataItemDto Additional data item info
     * @param dataItemRef Additional data item reference
     */
    private void fillAdditionalInfo(AdditionalDataItemDto dataItemDto, NodeRef dataItemRef) {
        if (dataItemDto instanceof AdditionalPerformersDto) {
            fillAdditionalPerformersInfo((AdditionalPerformersDto) dataItemDto, dataItemRef);
            return;
        }
        if (dataItemDto instanceof AdditionalConfirmerDto) {
            fillAdditionalConfirmerInfo((AdditionalConfirmerDto) dataItemDto, dataItemRef);
            return;
        }
    }

    /**
     * Fill additional performers info
     * @param dataItemDto  Additional data item info
     * @param dataItemRef Additional data item reference
     */
    private void fillAdditionalPerformersInfo(AdditionalPerformersDto dataItemDto, NodeRef dataItemRef) {
        if (!CollectionUtils.isEmpty(dataItemDto.getPerformers())) {
            for (AuthorityDto authorityDto : dataItemDto.getPerformers()) {
                NodeRef authorityRef = new NodeRef(WORKSPACE_PREFIX + authorityDto.getNodeUUID());
                if (nodeService.exists(authorityRef)) {
                    nodeService.createAssociation(dataItemRef, authorityRef, EventModel.ASSOC_PERFORMERS);
                }
            }
        }
    }

    /**
     * Fill additional confirmer info
     * @param dataItemDto  Additional data item info
     * @param dataItemRef Additional data item reference
     */
    private void fillAdditionalConfirmerInfo(AdditionalConfirmerDto dataItemDto, NodeRef dataItemRef) {
        if (dataItemDto.getConfirmer() != null) {
            NodeRef authorityRef = new NodeRef(WORKSPACE_PREFIX + dataItemDto.getConfirmer().getNodeUUID());
            if (nodeService.exists(authorityRef)) {
                nodeService.createAssociation(dataItemRef, authorityRef, EventModel.ASSOC_CONFIRMER);
            }
        }
    }

    /**
     * Fill additional compare process variable condition info
     * @param conditionRef Condition node reference
     * @param conditionDto Condition data transfer object
     */
    private void fillAdditionalCompareProcessVariableConditionInfo(CompareProcessVariableConditionDto conditionDto, NodeRef conditionRef) {
        nodeService.setProperty(conditionRef, ConditionModel.CompareProcessVariable.PROP_VARIABLE, conditionDto.getProcessVariable());
        nodeService.setProperty(conditionRef, ConditionModel.CompareProcessVariable.PROP_VALUE, conditionDto.getProcessVariableValue());

    }

    /**
     * Fill additional compare property value condition info
     * @param conditionRef Condition node reference
     * @param conditionDto Condition data transfer object
     */
    private void fillAdditionalComparePropertyValueConditionInfo(ComparePropertyValueConditionDto conditionDto, NodeRef conditionRef) {
        QName propertyName = QName.createQName(conditionDto.getPropertyName());
        nodeService.setProperty(conditionRef, ConditionModel.ComparePropertyValue.PROP_PROPERTY, propertyName);
        nodeService.setProperty(conditionRef, ConditionModel.ComparePropertyValue.PROP_VALUE, conditionDto.getPropertyValue());
        nodeService.setProperty(conditionRef, ConditionModel.ComparePropertyValue.PROP_OPERATION, conditionDto.getPropertyOperation());
    }

    /**
     * Fill additional evaluate script condition info
     * @param conditionRef Condition node reference
     * @param conditionDto Condition data transfer object
     */
    private void fillAdditionalEvaluateScriptConditionInfo(EvaluateScriptConditionDto conditionDto, NodeRef conditionRef) {
        nodeService.setProperty(conditionRef, ConditionModel.EvaluateScript.PROP_SCRIPT, conditionDto.getEvaluateScript());
    }

    /**
     * Fill additional user has permission condition info
     * @param conditionRef Condition node reference
     * @param conditionDto Condition data transfer object
     */
    private void fillAdditionalUserHasPermissionConditionInfo(UserHasPermissionConditionDto conditionDto, NodeRef conditionRef) {
        nodeService.setProperty(conditionRef, ConditionModel.UserHasPermission.PROP_PERMISSION, conditionDto.getPermission());
        nodeService.setProperty(conditionRef, ConditionModel.UserHasPermission.PROP_USERNAME, conditionDto.getPermissionUsername());
    }

    /**
     * Fill additional user in document condition info
     * @param conditionRef Condition node reference
     * @param conditionDto Condition data transfer object
     */
    private void fillAdditionalUserInDocumentConditionInfo(UserInDocumentConditionDto conditionDto, NodeRef conditionRef) {
        nodeService.setProperty(conditionRef, ConditionModel.UserInDocument.PROP_PROPERTY, conditionDto.getDocumentProperty());
        nodeService.setProperty(conditionRef, ConditionModel.UserInDocument.PROP_USERNAME, conditionDto.getDocumentUsername());
    }

    /**
     * Fill additional user in group condition info
     * @param conditionRef Condition node reference
     * @param conditionDto Condition data transfer object
     */
    private void fillAdditionalUserInGroupConditionInfo(UserInGroupConditionDto conditionDto, NodeRef conditionRef) {
        nodeService.setProperty(conditionRef, ConditionModel.UserInGroup.PROP_GROUPNAME, conditionDto.getGroupName());
        nodeService.setProperty(conditionRef, ConditionModel.UserInGroup.PROP_USERNAME, conditionDto.getGroupUsername());
    }

    /**
     * Fill additional stage info
     * @param caseModelDto Case model data transfer object
     * @param caseModelRef Case model node reference
     */
    private void fillAdditionalStageInfo(StageDto caseModelDto, NodeRef caseModelRef) {
        nodeService.setProperty(caseModelRef, StagesModel.PROP_DOCUMENT_STATUS, caseModelDto.getDocumentStatus());
        if (caseModelDto.getCaseStatus() != null) {
            NodeRef statusNodeRef = new NodeRef(WORKSPACE_PREFIX + caseModelDto.getCaseStatus().getNodeUUID());
            if (nodeService.exists(statusNodeRef)) {
                nodeService.createAssociation(caseModelRef, statusNodeRef, StagesModel.ASSOC_CASE_STATUS);
            }
        }
    }

    /**
     * Fill additional execution script info
     * @param caseModelDto Case model data transfer object
     * @param caseModelRef Case model node reference
     */
    private void fillAdditionalExecutionScriptInfo(ExecutionScriptDto caseModelDto, NodeRef caseModelRef) {
        nodeService.setProperty(caseModelRef, ActionModel.ExecuteScript.PROP_SCRIPT, caseModelDto.getExecuteScript());
    }

    /**
     * Fill additional info fail info
     * @param caseModelDto Case model data transfer object
     * @param caseModelRef Case model node reference
     */
    private void fillAdditionalFailInfo(FailDto caseModelDto, NodeRef caseModelRef) {
        nodeService.setProperty(caseModelRef, ActionModel.Fail.PROP_MESSAGE, caseModelDto.getFailMessage());
    }

    /**
     * Fill additional info mail info
     * @param caseModelDto Case model data transfer object
     * @param caseModelRef Case model node reference
     */
    private void fillAdditionalMailInfo(MailDto caseModelDto, NodeRef caseModelRef) {
        nodeService.setProperty(caseModelRef, ActionModel.Mail.PROP_TO, caseModelDto.getMailTo());
        nodeService.setProperty(caseModelRef, ActionModel.Mail.PROP_TO_MANY, caseModelDto.getToMany());
        nodeService.setProperty(caseModelRef, ActionModel.Mail.PROP_SUBJECT, caseModelDto.getSubject());
        nodeService.setProperty(caseModelRef, ActionModel.Mail.PROP_FROM, caseModelDto.getFromUser());
        nodeService.setProperty(caseModelRef, ActionModel.Mail.PROP_TEXT, caseModelDto.getMailText());
        nodeService.setProperty(caseModelRef, ActionModel.Mail.PROP_HTML, caseModelDto.getMailHtml());
    }

    /**
     * Fill additional info set process variable info
     * @param caseModelDto Case model data transfer object
     * @param caseModelRef Case model node reference
     */
    private void fillAdditionalSetProcessVariableInfo(SetProcessVariableDto caseModelDto, NodeRef caseModelRef) {
        nodeService.setProperty(caseModelRef, ActionModel.SetProcessVariable.PROP_VARIABLE, caseModelDto.getProcessVariableValue());
        nodeService.setProperty(caseModelRef, ActionModel.SetProcessVariable.PROP_VALUE, caseModelDto.getProcessVariableValue());
    }

    /**
     * Fill additional info set property value info
     * @param caseModelDto Case model data transfer object
     * @param caseModelRef Case model node reference
     */
    private void fillAdditionalSetPropertyValueInfo(SetPropertyValueDto caseModelDto, NodeRef caseModelRef) {
        QName propertyName = caseModelDto.getPropertyFullName() != null ? QName.createQName(caseModelDto.getPropertyFullName()) : null;
        nodeService.setProperty(caseModelRef, ActionModel.SetPropertyValue.PROP_PROPERTY, propertyName);
        nodeService.setProperty(caseModelRef, ActionModel.SetPropertyValue.PROP_VALUE, caseModelDto.getPropertyValue());
    }

    /**
     * Fill additional info set case status info
     * @param caseModelDto Case model data transfer object
     * @param caseModelRef Case model node reference
     */
    private void fillAdditionalSetCaseStatusInfo(SetCaseStatusDto caseModelDto, NodeRef caseModelRef) {
        if (caseModelDto.getCaseStatus() != null) {
            NodeRef statusNodeRef = new NodeRef(WORKSPACE_PREFIX + caseModelDto.getCaseStatus().getNodeUUID());
            if (nodeService.exists(statusNodeRef)) {
                nodeService.createAssociation(caseModelRef, statusNodeRef, ActionModel.SetCaseStatus.ASSOC_STATUS);
            }
        }
    }

    /**
     * Fill additional info case timer info
     * @param caseModelDto Case model data transfer object
     * @param caseModelRef Case model node reference
     */
    private void fillAdditionalCaseTimerInfo(CaseTimerDto caseModelDto, NodeRef caseModelRef) {
        nodeService.setProperty(caseModelRef, CaseTimerModel.PROP_EXPRESSION_TYPE, caseModelDto.getExpressionType());
        nodeService.setProperty(caseModelRef, CaseTimerModel.PROP_TIMER_EXPRESSION, caseModelDto.getTimerExpression());
        nodeService.setProperty(caseModelRef, CaseTimerModel.PROP_DATE_PRECISION, caseModelDto.getDatePrecision());
        nodeService.setProperty(caseModelRef, CaseTimerModel.PROP_COMPUTED_EXPRESSION, caseModelDto.getComputedExpression());
        nodeService.setProperty(caseModelRef, CaseTimerModel.PROP_REPEAT_COUNTER, caseModelDto.getRepeatCounter());
        nodeService.setProperty(caseModelRef, CaseTimerModel.PROP_OCCUR_DATE, caseModelDto.getOccurDate());
    }

    /**
     * Fill additional info case task info
     * @param caseModelDto Case model data transfer object
     * @param caseModelRef Case model node reference
     */
    private void fillAdditionalCaseTaskInfo(CaseTaskDto caseModelDto, NodeRef caseModelRef) {
        nodeService.setProperty(caseModelRef, ICaseTaskModel.PROP_WORKFLOW_DEFINITION_NAME, caseModelDto.getWorkflowDefinitionName());
        nodeService.setProperty(caseModelRef, ICaseTaskModel.PROP_WORKFLOW_INSTANCE_ID, caseModelDto.getWorkflowInstanceId());
        nodeService.setProperty(caseModelRef, ICaseTaskModel.PROP_DEADLINE, caseModelDto.getDueDate());
        nodeService.setProperty(caseModelRef, ICaseTaskModel.PROP_PRIORITY, caseModelDto.getPriority());

        /* BPM Package */
        if (caseModelDto.getBpmPackage() != null) {
            NodeRef packageNodeRef = new NodeRef(WORKSPACE_PREFIX + caseModelDto.getBpmPackage().getNodeUUID());
            if (nodeService.exists(packageNodeRef)) {
                nodeService.createAssociation(caseModelRef, packageNodeRef, ICaseTaskModel.ASSOC_WORKFLOW_PACKAGE);
            }
        }
        restoreTaskProperties(caseModelRef, caseModelDto);
        restoreTaskAssocs(caseModelRef, caseModelDto);
    }

    /**
     * Restore task assocs
     * @param taskModelRef Task model reference
     * @param caseTaskDto Case task data transfer object
     */
    private void restoreTaskAssocs(NodeRef taskModelRef, CaseTaskDto caseTaskDto) {
        try {
            ArrayNode arrayNode = objectMapper.readValue(caseTaskDto.getTaskAssocs(), ArrayNode.class);
            for (int i = 0; i < arrayNode.size(); i++) {
                JsonNode paramNode =  arrayNode.get(i);
                TaskAssocDto assocDto =  objectMapper.treeToValue(paramNode, TaskAssocDto.class);
                /* Create property */
                QName assocTypeName = QName.createQName(assocDto.getAssocType());
                NodeRef nodeRef = new NodeRef(WORKSPACE_PREFIX + assocDto.getNodeRef());
                if (nodeService.exists(nodeRef)) {
                    nodeService.createAssociation(taskModelRef, nodeRef, assocTypeName);
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Restore task properties
     * @param taskModelRef Task model reference
     * @param caseTaskDto Case task data transfer object
     */
    private void restoreTaskProperties(NodeRef taskModelRef, CaseTaskDto caseTaskDto) {
        try {
            ArrayNode arrayNode = objectMapper.readValue(caseTaskDto.getTaskProperties(), ArrayNode.class);
            for (int i = 0; i < arrayNode.size(); i++) {
                JsonNode paramNode =  arrayNode.get(i);
                TaskPropertyDto propertyDto =  objectMapper.treeToValue(paramNode, TaskPropertyDto.class);
                /* Create property */
                QName typeName = QName.createQName(propertyDto.getTypeName());
                Class clazz = Class.forName(propertyDto.getValueClass());
                Serializable propertyValue = getPropertyValue(clazz, propertyDto.getValue());
                nodeService.setProperty(taskModelRef, typeName, propertyValue);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Get property value
     * @param className Value class name
     * @param rawValue Raw value
     * @return Serializable object
     * @throws ParseException
     * @throws NoSuchMethodException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     * @throws InstantiationException
     */
    private Serializable getPropertyValue(Class className, String rawValue) throws ParseException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        if (className.equals(String.class)) {
            return rawValue;
        }
        if (className.equals(Date.class)) {
            return DATE_TIME_FORMAT.get().parse(rawValue);
        }
        if (Number.class.isAssignableFrom(className) || className.equals(Boolean.class)) {
            return (Serializable) className.getDeclaredConstructor(String.class).newInstance(rawValue);
        }
        return null;
    }

    /**
     * Get case model type
     * @param caseModelDto Case model data transfer object
     * @return Type name
     */
    private QName getCaseModelType(CaseModelDto caseModelDto) {
        if (caseModelDto instanceof StageDto) {
            return StagesModel.TYPE_STAGE;
        }
        if (caseModelDto instanceof ExecutionScriptDto) {
            return ActionModel.ExecuteScript.TYPE;
        }
        if (caseModelDto instanceof FailDto) {
            return ActionModel.Fail.TYPE;
        }
        if (caseModelDto instanceof MailDto) {
            return ActionModel.Mail.TYPE;
        }
        if (caseModelDto instanceof SetProcessVariableDto) {
            return ActionModel.SetProcessVariable.TYPE;
        }
        if (caseModelDto instanceof SetPropertyValueDto) {
            return ActionModel.SetPropertyValue.TYPE;
        }
        if (caseModelDto instanceof SetCaseStatusDto) {
            return ActionModel.SetCaseStatus.TYPE;
        }
        if (caseModelDto instanceof CaseTimerDto) {
            return CaseTimerModel.TYPE_TIMER;
        }
        if (caseModelDto instanceof CaseTaskDto) {
            return QName.createQName(((CaseTaskDto) caseModelDto).getTaskTypeFullName());
        }
        return ActivityModel.TYPE_ACTIVITY;
    }

    /**
     * Get event type
     * @param eventDto Event data transfer object
     * @return Type name
     */
    private QName getEventType(EventDto eventDto) {
        if (eventDto instanceof UserActionEventDto) {
            return EventModel.TYPE_USER_ACTION;
        }
        if (eventDto instanceof ActivityStartedEventDto) {
            return ICaseEventModel.TYPE_ACTIVITY_STARTED_EVENT;
        }
        if (eventDto instanceof ActivityStoppedEventDto) {
            return ICaseEventModel.TYPE_ACTIVITY_STOPPED_EVENT;
        }
        if (eventDto instanceof CaseCreatedEventDto) {
            return ICaseEventModel.TYPE_CASE_CREATED;
        }
        if (eventDto instanceof CasePropertiesChangedEventDto) {
            return ICaseEventModel.TYPE_CASE_PROPERTIES_CHANGED;
        }
        if (eventDto instanceof StageChildrenStoppedEventDto) {
            return ICaseEventModel.TYPE_STAGE_CHILDREN_STOPPED;
        }
        return null;
    }

    /**
     * Get condition type
     * @param conditionDto Condition data transfer object
     * @return Condition type
     */
    private QName getConditionType(ConditionDto conditionDto) {
        if (conditionDto instanceof CompareProcessVariableConditionDto) {
            return ConditionModel.CompareProcessVariable.TYPE;
        }
        if (conditionDto instanceof ComparePropertyValueConditionDto) {
            return ConditionModel.ComparePropertyValue.TYPE;
        }
        if (conditionDto instanceof EvaluateScriptConditionDto) {
            return ConditionModel.EvaluateScript.TYPE;
        }

        if (conditionDto instanceof UserHasPermissionConditionDto) {
            return ConditionModel.UserHasPermission.TYPE;
        }
        if (conditionDto instanceof UserInDocumentConditionDto) {
            return ConditionModel.UserInDocument.TYPE;
        }
        if (conditionDto instanceof UserInGroupConditionDto) {
            return ConditionModel.UserInGroup.TYPE;
        }
        return null;
    }

    /**
     * Get additional item type
     * @param additionalDataItemDto Additional data item data transfer object
     * @return Additional item type
     */
    private QName getAdditionalItemType(AdditionalDataItemDto additionalDataItemDto) {
        if (additionalDataItemDto instanceof AdditionalConfirmerDto) {
            return EventModel.TYPE_ADDITIONAL_CONFIRMER;
        }
        if (additionalDataItemDto instanceof AdditionalPerformersDto) {
            return EventModel.TYPE_ADDITIONAL_PERFORMERS;
        } else {
            return EventModel.TYPE_ADDITIONAL_DATA;
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
     * Set remote case model service
     * @param remoteCaseModelService Remote case model service
     */
    public void setRemoteCaseModelService(RemoteCaseModelService remoteCaseModelService) {
        this.remoteCaseModelService = remoteCaseModelService;
    }
}
