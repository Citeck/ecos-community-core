package ru.citeck.ecos.webscripts.cases;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.alfresco.model.ContentModel;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AccessStatus;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.namespace.QName;
import org.codehaus.jackson.map.util.ISO8601Utils;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.DeclarativeWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import ru.citeck.ecos.cases.RemoteCaseModelService;
import ru.citeck.ecos.dto.*;
import ru.citeck.ecos.model.*;
import ru.citeck.ecos.template.TemplateNodeService;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Case activity get web-script
 */
public class CaseActivityGet extends DeclarativeWebScript {

    /**
     * Workspace prefix
     */
    protected static final String WORKSPACE_PREFIX = "workspace://SpacesStore/";

    /*
     * Request params
     */
    protected static final String PARAM_DOCUMENT_NODE_REF = "nodeRef";

    /**
     * Object mapper
     */
    protected ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Remote case model service
     */
    protected RemoteCaseModelService remoteCaseModelService;

    /**
     * Node service
     */
    protected NodeService nodeService;

    /**
     * Template node service
     */
    protected TemplateNodeService templateNodeService;

    /**
     * Permission service
     */
    protected PermissionService permissionService;

    /**
     * Service registry
     */
    protected ServiceRegistry serviceRegistry;

    /**
     * Execute implementation
     *
     * @param req    Http-request
     * @param status Status
     * @param cache  Cache
     * @return Map of attributes
     */
    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache) {
        /* Load node reference */
        String nodeRefUuid = req.getParameter(PARAM_DOCUMENT_NODE_REF);
        if (nodeRefUuid == null) {
            return Collections.emptyMap();
        }
        nodeRefUuid = nodeRefUuid.startsWith(WORKSPACE_PREFIX) ? nodeRefUuid : (WORKSPACE_PREFIX + nodeRefUuid);
        NodeRef nodeRef = new NodeRef(nodeRefUuid);
        /* Load and transform data */
        if (nodeService.exists(nodeRef)) {
            ObjectNode objectNode = createFromNodeReference(nodeRef);
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("executionResult", objectNode.toString());
            return resultMap;
        } else {
            CaseModelDto dto = remoteCaseModelService.getCaseModelByNodeUUID(nodeRef.getId(), false);
            Map<String, Object> resultMap = new HashMap<>();
            if (dto == null) {
                resultMap.put("executionResult", "{}");
                return resultMap;
            } else {
                ObjectNode objectNode = createFromRemoteCaseModel(dto);
                resultMap.put("executionResult", objectNode.toString());
                return resultMap;
            }
        }
    }

    /**
     * Create object node from node reference
     *
     * @param nodeRef Node reference
     * @return Object node
     */
    protected ObjectNode createFromNodeReference(NodeRef nodeRef) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        QName type = nodeService.getType(nodeRef);

        objectNode.put("nodeRef", nodeRef.toString());
        objectNode.put("type", type.toPrefixString(serviceRegistry.getNamespaceService()));
        objectNode.put("index", (Integer) nodeService.getProperty(nodeRef, ActivityModel.PROP_INDEX));
        objectNode.put("title", (String) nodeService.getProperty(nodeRef, ContentModel.PROP_TITLE));
        objectNode.put("typeTitle", templateNodeService.getClassTitle(type.toString()));
        objectNode.put("description", (String) nodeService.getProperty(nodeRef, ContentModel.PROP_DESCRIPTION));

        /* Dates */
        if (nodeService.getProperty(nodeRef, ActivityModel.PROP_PLANNED_START_DATE) != null) {
            Date date = (Date) nodeService.getProperty(nodeRef, ActivityModel.PROP_PLANNED_START_DATE);
            objectNode.put("plannedStartDate", formatDate(date));
        }
        if (nodeService.getProperty(nodeRef, ActivityModel.PROP_PLANNED_END_DATE) != null) {
            Date date = (Date) nodeService.getProperty(nodeRef, ActivityModel.PROP_PLANNED_END_DATE);
            objectNode.put("plannedEndDate", formatDate(date));
        }
        Date actualStartDate = (Date) nodeService.getProperty(nodeRef, ActivityModel.PROP_ACTUAL_START_DATE);
        if (actualStartDate != null) {
            objectNode.put("actualStartDate", formatDate(actualStartDate));
        }
        Date actualEndDate = (Date) nodeService.getProperty(nodeRef, ActivityModel.PROP_ACTUAL_END_DATE);
        if (actualEndDate != null) {
            objectNode.put("actualEndDate", formatDate(actualEndDate));
        }
        Integer performTime = (Integer) nodeService.getProperty(nodeRef, ActivityModel.PROP_EXPECTED_PERFORM_TIME);
        objectNode.put("expectedPerformTime", performTime);

        /* Flags */
        boolean manualStarted = nodeService.getProperty(nodeRef, ActivityModel.PROP_MANUAL_STARTED) != null ?
                (Boolean) nodeService.getProperty(nodeRef, ActivityModel.PROP_MANUAL_STARTED) : false;
        boolean manualStopped = nodeService.getProperty(nodeRef, ActivityModel.PROP_MANUAL_STOPPED) != null ?
                (Boolean) nodeService.getProperty(nodeRef, ActivityModel.PROP_MANUAL_STOPPED) : false;
        boolean repeatable = nodeService.getProperty(nodeRef, ActivityModel.PROP_REPEATABLE) != null ?
                (Boolean) nodeService.getProperty(nodeRef, ActivityModel.PROP_REPEATABLE) : false;

        AccessStatus writePermission = permissionService.hasPermission(nodeRef, "Write");

        boolean hasWritePermission = (writePermission == AccessStatus.ALLOWED);
        objectNode.put("editable", hasWritePermission);

        boolean startable = hasWritePermission && ((manualStarted && repeatable) || actualStartDate == null);
        objectNode.put("startable", startable);

        boolean stoppable = hasWritePermission && manualStopped && actualStartDate != null && actualEndDate == null;
        objectNode.put("stoppable", stoppable);


        AccessStatus deletePermission = permissionService.hasPermission(nodeRef, "Delete");
        objectNode.put("removable", (deletePermission == AccessStatus.ALLOWED));
        objectNode.put("composite", nodeService.hasAspect(nodeRef, ActivityModel.ASPECT_HAS_ACTIVITIES));
        return objectNode;
    }

    /**
     * Create object node from remote data transfer object
     *
     * @param caseModelDto Remote case model data transfer object
     * @return Object node
     */
    protected ObjectNode createFromRemoteCaseModel(CaseModelDto caseModelDto) {
        QName type = getCaseModelType(caseModelDto);
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("nodeRef", caseModelDto.getNodeUUID());
        objectNode.put("type", type.toPrefixString(serviceRegistry.getNamespaceService()));
        objectNode.put("index", caseModelDto.getIndex());
        objectNode.put("title", caseModelDto.getTitle());
        objectNode.put("typeTitle", templateNodeService.getClassTitle(type.toString()));
        objectNode.put("description", caseModelDto.getDescription());
        objectNode.put("plannedStartDate", formatDate(caseModelDto.getPlannedStartDate()));
        objectNode.put("plannedEndDate", formatDate(caseModelDto.getPlannedEndDate()));
        objectNode.put("actualStartDate", formatDate(caseModelDto.getActualStartDate()));
        objectNode.put("actualEndDate", formatDate(caseModelDto.getActualEndDate()));
        objectNode.put("expectedPerformTime", caseModelDto.getExpectedPerformTime());

        /* Flags */
        objectNode.put("startable", false);
        objectNode.put("stoppable", false);
        objectNode.put("editable", false);
        objectNode.put("removable", false);
        objectNode.put("composite", caseModelDto.getHasChildCases());
        return objectNode;
    }

    private String formatDate(Date date) {
        return date != null ? ISO8601Utils.format(date) : null;
    }

    /**
     * Get case model type
     *
     * @param caseModelDto Case model data transfer object
     * @return Type
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
        if (caseModelDto instanceof StartWorkflowDto) {
            return ActionModel.StartWorkflow.TYPE;
        }
        if (caseModelDto instanceof SetCaseStatusDto) {
            return ActionModel.SetCaseStatus.TYPE;
        }
        if (caseModelDto instanceof CaseTimerDto) {
            return CaseTimerModel.TYPE_TIMER;
        }
        if (caseModelDto instanceof CaseTaskDto) {
            return ICaseTaskModel.TYPE_TASK;
        }
        return ActivityModel.TYPE_ACTIVITY;
    }

    /**
     * Setters
     */

    public void setRemoteCaseModelService(RemoteCaseModelService remoteCaseModelService) {
        this.remoteCaseModelService = remoteCaseModelService;
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setTemplateNodeService(TemplateNodeService templateNodeService) {
        this.templateNodeService = templateNodeService;
    }

    public void setPermissionService(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
    }
}
