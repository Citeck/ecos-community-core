package ru.citeck.ecos.cases.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.repository.AssociationRef;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.transaction.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import ru.citeck.ecos.cases.RemoteCaseModelService;
import ru.citeck.ecos.dto.*;
import ru.citeck.ecos.model.*;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Remote case model service
 */
public class RemoteCaseModelServiceImpl implements RemoteCaseModelService {

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
     * Object mapper
     */
    private ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Date format
     */
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    /**
     * Datetime format
     */
    private SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");


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
        transactionService.getRetryingTransactionHelper().doInTransaction(() -> {
            ArrayNode arrayNode = objectMapper.createArrayNode();
            /** Create json array */
            for (NodeRef caseRef : getCaseModelsByNode(documentRef)) {
                ObjectNode objectNode = createObjectNodeFromCaseModel(caseRef);
                if (objectNode != null) {
                    arrayNode.add(objectNode);
                }
                nodeService.deleteNode(caseRef);
            }
            nodeService.setProperty(documentRef, IdocsModel.PROP_CASE_MODELS_SENT, true);
            /** Send request */
            postForObject(SAVE_CASE_MODELS_METHOD, arrayNode.toString(), String.class);
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
        /** Base properties */
        ObjectNode objectNode = objectMapper.createObjectNode();
        String dtoType = getCaseModelType(nodeService.getType(caseModelRef));
        objectNode.put("dtoType", dtoType);
        if (nodeService.getProperty(caseModelRef, ContentModel.PROP_CREATED) != null) {
            objectNode.put("created", dateTimeFormat.format((Date) nodeService.getProperty(caseModelRef, ContentModel.PROP_CREATED)));
        }
        objectNode.put("creator", (String) nodeService.getProperty(caseModelRef, ContentModel.PROP_CREATOR));
        if (nodeService.getProperty(caseModelRef, ContentModel.PROP_MODIFIED) != null) {
            objectNode.put("modified", dateTimeFormat.format((Date) nodeService.getProperty(caseModelRef, ContentModel.PROP_MODIFIED)));
        }
        objectNode.put("modifier", (String) nodeService.getProperty(caseModelRef, ContentModel.PROP_MODIFIER));
        objectNode.put("title", (String) nodeService.getProperty(caseModelRef, ContentModel.PROP_TITLE));
        objectNode.put("description", (String) nodeService.getProperty(caseModelRef, ContentModel.PROP_DESCRIPTION));
        ChildAssociationRef parentAssoc = nodeService.getPrimaryParent(caseModelRef);
        if (parentAssoc != null) {
            if (parentAssoc.getParentRef() != null) {
                if (dictionaryService.isSubClass(
                        nodeService.getType(parentAssoc.getParentRef()),
                        IdocsModel.TYPE_DOC)) {
                    objectNode.put("documentId", parentAssoc.getParentRef().getId());
                }
            }
        }
        objectNode.put("nodeUUID", caseModelRef.getId());
        if (nodeService.getProperty(caseModelRef, ActivityModel.PROP_PLANNED_START_DATE) != null) {
            objectNode.put("plannedStartDate", dateFormat.format((Date) nodeService.getProperty(caseModelRef, ActivityModel.PROP_PLANNED_START_DATE)));
        }
        if (nodeService.getProperty(caseModelRef, ActivityModel.PROP_PLANNED_END_DATE) != null) {
            objectNode.put("plannedEndDate", dateFormat.format((Date) nodeService.getProperty(caseModelRef, ActivityModel.PROP_PLANNED_END_DATE)));
        }
        if (nodeService.getProperty(caseModelRef, ActivityModel.PROP_ACTUAL_START_DATE) != null) {
            objectNode.put("actualStartDate", dateFormat.format((Date) nodeService.getProperty(caseModelRef, ActivityModel.PROP_ACTUAL_START_DATE)));
        }
        if (nodeService.getProperty(caseModelRef, ActivityModel.PROP_ACTUAL_END_DATE) != null) {
            objectNode.put("actualEndDate", dateFormat.format((Date) nodeService.getProperty(caseModelRef, ActivityModel.PROP_ACTUAL_END_DATE)));
        }
        objectNode.put("expectedPerformTime", (Integer) nodeService.getProperty(caseModelRef, ActivityModel.PROP_EXPECTED_PERFORM_TIME));
        objectNode.put("manualStarted", (Boolean) nodeService.getProperty(caseModelRef, ActivityModel.PROP_MANUAL_STARTED));
        objectNode.put("manualStopped", (Boolean) nodeService.getProperty(caseModelRef, ActivityModel.PROP_MANUAL_STOPPED));
        objectNode.put("index", (Integer) nodeService.getProperty(caseModelRef, ActivityModel.PROP_INDEX));
        objectNode.put("autoEvents", (Boolean) nodeService.getProperty(caseModelRef, ActivityModel.PROP_AUTO_EVENTS));
        objectNode.put("repeatable", (Boolean) nodeService.getProperty(caseModelRef, ActivityModel.PROP_REPEATABLE));
        objectNode.put("typeVersion", (Integer) nodeService.getProperty(caseModelRef, ActivityModel.PROP_TYPE_VERSION));
        fillAdditionalInfo(dtoType, caseModelRef, objectNode);
        /** Child activities */
        ArrayNode arrayNode = objectMapper.createArrayNode();
        for (NodeRef childCaseRef : getCaseModelsByNode(caseModelRef)) {
            ObjectNode childCaseNode = createObjectNodeFromCaseModel(childCaseRef);
            if (childCaseNode != null) {
                arrayNode.add(childCaseNode);
            }
            nodeService.deleteNode(childCaseRef);
        }
        objectNode.put("childCases", arrayNode);
        return objectNode;
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
        if (StartWorkflowDto.DTO_TYPE.equals(dtoType)) {
            fillAdditionalStartWorkflowInfo(caseModelRef, objectNode);
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
     * Fill additional info start workflow info
     * @param caseModelRef Case model node reference
     * @param objectNode Object node
     */
    private void fillAdditionalStartWorkflowInfo(NodeRef caseModelRef, ObjectNode objectNode) {
        objectNode.put("workflowName", (String) nodeService.getProperty(caseModelRef, ActionModel.StartWorkflow.PROP_WORKFLOW_NAME));
    }

    /**
     * Fill additional info set case status info
     * @param caseModelRef Case model node reference
     * @param objectNode Object node
     */
    private void fillAdditionalSetCaseStatusInfo(NodeRef caseModelRef, ObjectNode objectNode) {
        List<AssociationRef> assocs = nodeService.getTargetAssocs(caseModelRef, ActionModel.SetCaseStatus.PROP_STATUS);
        if (!CollectionUtils.isEmpty(assocs)) {
            NodeRef statusRef = assocs.get(0).getTargetRef();
            if (statusRef != null) {
                ObjectNode statusObjectNode = objectMapper.createObjectNode();
                if (nodeService.getProperty(statusRef, ContentModel.PROP_CREATED) != null) {
                    statusObjectNode.put("created", dateTimeFormat.format((Date) nodeService.getProperty(statusRef, ContentModel.PROP_CREATED)));
                }
                statusObjectNode.put("creator", (String) nodeService.getProperty(statusRef, ContentModel.PROP_CREATOR));
                if (nodeService.getProperty(statusRef, ContentModel.PROP_MODIFIED) != null) {
                    statusObjectNode.put("modified", dateTimeFormat.format((Date) nodeService.getProperty(statusRef, ContentModel.PROP_MODIFIED)));
                }
                statusObjectNode.put("modifier", (String) nodeService.getProperty(statusRef, ContentModel.PROP_MODIFIER));
                statusObjectNode.put("title", (String) nodeService.getProperty(statusRef, ContentModel.PROP_TITLE));
                statusObjectNode.put("description", (String) nodeService.getProperty(statusRef, ContentModel.PROP_DESCRIPTION));
                statusObjectNode.put("nodeUUID", statusRef.getId());
                /** Set status */
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
            objectNode.put("occurDate", dateTimeFormat.format((Date) nodeService.getProperty(caseModelRef, CaseTimerModel.PROP_OCCUR_DATE)));
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
            objectNode.put("dueDate", dateTimeFormat.format((Date) nodeService.getProperty(caseModelRef, ICaseTaskModel.PROP_DEADLINE)));
        }
        objectNode.put("priority", (Integer) nodeService.getProperty(caseModelRef, ICaseTaskModel.PROP_PRIORITY));
        /** BPM Package */
        List<AssociationRef> packageAssocs = nodeService.getTargetAssocs(caseModelRef, ICaseTaskModel.ASSOC_WORKFLOW_PACKAGE);
        if (!CollectionUtils.isEmpty(packageAssocs)) {
            if (packageAssocs.get(0).getTargetRef() != null) {
                NodeRef packageRef = packageAssocs.get(0).getTargetRef();
                ObjectNode packageNode = objectMapper.createObjectNode();
                if (nodeService.getProperty(packageRef, ContentModel.PROP_CREATED) != null) {
                    packageNode.put("created", dateTimeFormat.format((Date) nodeService.getProperty(packageRef, ContentModel.PROP_CREATED)));
                }
                packageNode.put("creator", (String) nodeService.getProperty(packageRef, ContentModel.PROP_CREATOR));
                if (nodeService.getProperty(packageRef, ContentModel.PROP_MODIFIED) != null) {
                    packageNode.put("modified", dateTimeFormat.format((Date) nodeService.getProperty(packageRef, ContentModel.PROP_MODIFIED)));
                }
                packageNode.put("modifier", (String) nodeService.getProperty(packageRef, ContentModel.PROP_MODIFIER));
                packageNode.put("title", (String) nodeService.getProperty(packageRef, ContentModel.PROP_TITLE));
                packageNode.put("description", (String) nodeService.getProperty(packageRef, ContentModel.PROP_DESCRIPTION));
                packageNode.put("nodeUUID", packageRef.getId());

                packageNode.put("workflowInstanceId", (String) nodeService.getProperty(packageRef, BpmPackageModel.PROP_WORKFLOW_INSTANCE_ID));
                packageNode.put("workflowDefinitionName", (String) nodeService.getProperty(packageRef, BpmPackageModel.PROP_WORKFLOW_DEFINITION_NAME));
                packageNode.put("isSystemPackage", (Boolean) nodeService.getProperty(packageRef, BpmPackageModel.PROP_IS_SYSTEM_PACKAGE));

                objectNode.set("bpmPackage", packageNode);
            }
        }
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
        if (dictionaryService.isSubClass(nodeType, ActionModel.StartWorkflow.TYPE)) {
            return StartWorkflowDto.DTO_TYPE;
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
        return CaseModelDto.DTO_TYPE;
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
        /** Header */
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        /** Body params */
        HttpEntity requestEntity = new HttpEntity<>(requestBody, headers);
        return restTemplate.postForObject(properties.getProperty(CASE_MODELS_SERVICE_HOST) + serviceMethod, requestEntity, objectClass);
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
}

