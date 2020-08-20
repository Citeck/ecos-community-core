package ru.citeck.ecos.webscripts.cases;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.security.AuthenticationService;
import org.alfresco.service.namespace.QName;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import ru.citeck.ecos.cases.RemoteRestoreCaseModelService;
import ru.citeck.ecos.dto.CaseModelDto;
import ru.citeck.ecos.model.ActionModel;
import ru.citeck.ecos.model.IdocsModel;

import java.util.*;
import java.util.function.Function;

/**
 * Case activities get web-script
 */
public class CaseActivitiesGet extends CaseActivityGet {

    /**
     * Authentication service
     */
    private AuthenticationService authenticationService;

    /**
     * Execute implementation
     * @param req Http-request
     * @param status Status
     * @param cache Cache
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
        Map<String, Object> resultMap = new HashMap<>();
        if (areCaseModelsSent(nodeRef)) {
            List<CaseModelDto> caseModels = remoteCaseModelService.getCaseModelsByNodeRef(nodeRef, false);
            ArrayNode arrayNode = createArrayNodeFromRemoteCaseModels(caseModels);
            if (isRequiredRestoreTask(nodeRef)) {
                arrayNode.add(createRestoreCaseModel(nodeRef));
            }
            resultMap.put("executionResult", arrayNode.toString());
            return resultMap;
        } else {
            List<NodeRef> caseModels = remoteCaseModelService.getCaseModelsByNode(nodeRef);
            ArrayNode arrayNode = createArrayNodeFromNodeRefs(caseModels);
            resultMap.put("executionResult", arrayNode.toString());
            return resultMap;
        }
    }

    /**
     * Create array node from remote case models
     * @param models Remote case models
     * @return Array node
     */
    private ArrayNode createArrayNodeFromRemoteCaseModels(List<CaseModelDto> models) {
        ArrayNode result = objectMapper.createArrayNode();
        for (CaseModelDto caseModelDto : models) {
            result.add(createFromRemoteCaseModel(caseModelDto));
        }
        return result;
    }

    /**
     * Create array node from node references
     * @param nodeRefs List of node reference
     * @return Array node
     */
    private ArrayNode createArrayNodeFromNodeRefs(List<NodeRef> nodeRefs) {

        ArrayNode result = objectMapper.createArrayNode();

        final Function<ObjectNode, Integer> getIndex = n -> {
            JsonNode idxNode = n.get("index");
            if (idxNode != null && idxNode.isInt()) {
                return idxNode.asInt();
            }
            return 0;
        };

        nodeRefs.stream()
                .map(this::createFromNodeReference)
                .sorted(Comparator.comparing(getIndex))
                .forEachOrdered(result::add);

        return result;
    }

    /**
     * Create restore case model
     * @param documentRef Document reference
     * @return Object node
     */
    protected ObjectNode createRestoreCaseModel(NodeRef documentRef) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        QName type = ActionModel.ExecuteScript.TYPE;

        objectNode.put("nodeRef", RemoteRestoreCaseModelService.RESTORE_CASE_MODEL_UUID + documentRef.toString());
        objectNode.put("type", type.toPrefixString(serviceRegistry.getNamespaceService()));
        objectNode.put("index", 0);
        objectNode.put("title", "Восстановить case models");
        objectNode.put("typeTitle", templateNodeService.getClassTitle(type.toString()));
        objectNode.put("description", "");
        objectNode.putNull("plannedStartDate");
        objectNode.putNull("plannedEndDate");
        objectNode.putNull("actualStartDate");
        objectNode.putNull("actualEndDate");
        objectNode.putNull("expectedPerformTime");

        /* Flags */
        objectNode.put("startable", true);
        objectNode.put("stoppable", false);
        objectNode.put("editable", false);
        objectNode.put("removable", false);
        objectNode.put("composite", false);
        return objectNode;
    }


    /**
     * Check - are case models sent
     * @param nodeRef Node reference (document)
     * @return Check result
     */
    private boolean areCaseModelsSent(NodeRef nodeRef) {
        if (nodeRef == null) {
            return false;
        }
        if (!nodeService.exists(nodeRef)) {
            return true;
        }
        Boolean sentFlag = (Boolean) nodeService.getProperty(nodeRef, IdocsModel.PROP_CASE_MODELS_SENT);
        return sentFlag != null ? sentFlag : false;
    }

    /**
     * Check - is required restore task
     * @param nodeRef Node reference (document)
     * @return Check result
     */
    private boolean isRequiredRestoreTask(NodeRef nodeRef) {
        if (nodeRef == null) {
            return false;
        }
        if (!nodeService.exists(nodeRef)) {
            return false;
        }
        Boolean sentFlag = (Boolean) nodeService.getProperty(nodeRef, IdocsModel.PROP_CASE_MODELS_SENT);
        return sentFlag != null ? sentFlag : false;
    }

    /**
     * Set authentication service
     * @param authenticationService Authentication service
     */
    public void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }
}
