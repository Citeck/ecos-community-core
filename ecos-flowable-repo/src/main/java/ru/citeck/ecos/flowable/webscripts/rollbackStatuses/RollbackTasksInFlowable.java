package ru.citeck.ecos.flowable.webscripts.rollbackStatuses;

import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.workflow.WorkflowInstance;
import org.alfresco.service.cmr.workflow.WorkflowService;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.flowable.task.api.Task;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.extensions.surf.util.Content;
import org.springframework.extensions.surf.util.I18NUtil;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.DeclarativeWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import ru.citeck.ecos.flowable.services.FlowableTaskService;
import ru.citeck.ecos.flowable.services.RollbackFlowableTasksService;
import ru.citeck.ecos.icase.activity.dto.ActivityRef;
import ru.citeck.ecos.icase.activity.dto.CaseActivity;
import ru.citeck.ecos.icase.activity.service.ActivityCommonService;
import ru.citeck.ecos.icase.activity.service.CaseActivityService;
import ru.citeck.ecos.model.InvariantsModel;
import ru.citeck.ecos.records.RecordsUtils;
import ru.citeck.ecos.records2.RecordRef;

import java.util.*;

public class RollbackTasksInFlowable extends DeclarativeWebScript {
    private static Log logger = LogFactory.getLog(RollbackTasksInFlowable.class);
    private static final String NODE_KEY = "node";
    private static final String TARGET_STATUS_KEY = "targetStatus";

    private NodeService nodeService;
    private RollbackFlowableTasksService rollbackFlowableTasksService;
    private CaseActivityService caseActivityService;
    private ActivityCommonService activityCommonService;
    private WorkflowService workflowService;
    private FlowableTaskService flowableTaskService;

    private String nodeNotFound;
    private String targetStatusIsEmpty;
    private String rollbackTasksError;
    private String completeWithoutErrors;

    private String titleOfStartProcessStage;

    private Map<String, List<String>> destinationMap;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache) {
        JSONObject jsonData = getDataObject(req.getContent());
        Map<String, Object> result = new HashMap<>();
        if (jsonData == null) {
            return result;
        }

        NodeRef caseRef = getNodeFromJSON(jsonData);
        if (caseRef == null || !nodeService.exists(caseRef)) {
            result.put("resultOfRollback", createJsonResponse(I18NUtil.getMessage(nodeNotFound)));
            return result;
        }

        List<String> currentTasks = getCurrentTasksID(caseRef);

        String key = (String) getKeyForDestinationMapFromJSON(jsonData, TARGET_STATUS_KEY);

        if (StringUtils.isBlank(key)) {
            result.put("resultOfRollback", createJsonResponse(I18NUtil.getMessage(targetStatusIsEmpty)));
            return result;
        }

        boolean rollbackSuccess;
        if (!StringUtils.contains(key, "draft")) {
            List<String> destinationTask = destinationMap.get(key);
            rollbackSuccess = rollbackFlowableTasksService.rollbackTasks(caseRef, destinationTask);
        } else {
            rollbackSuccess = rollbackNotFlowableCaseToDraft(caseRef, true);
        }

        if (rollbackSuccess) {
            result.put("resultOfRollback", createJsonResponse(I18NUtil.getMessage(completeWithoutErrors)));
        } else {
            result.put("resultOfRollback", createJsonResponse(I18NUtil.getMessage(rollbackTasksError)));
            rollbackFlowableTasksService.rollbackTasks(caseRef, currentTasks);
        }
        return result;
    }

    private List<String> getCurrentTasksID(NodeRef caseRef) {
        List<String> currentTask = new LinkedList<>();
        List<WorkflowInstance> workflowInstancesList = workflowService.getWorkflowsForContent(caseRef, true);
        if (workflowInstancesList == null || workflowInstancesList.isEmpty()) {
            return Collections.emptyList();
        }

        for (WorkflowInstance instance : workflowInstancesList) {
            if (instance == null || !instance.isActive()) {
                continue;
            }

            String workflowInstanceId = instance.getId();
            List<Task> flowableTaskList = flowableTaskService.getTasksByProcessInstanceId(
                    workflowInstanceId.substring(workflowInstanceId.indexOf('$') + 1)
            );
            if (flowableTaskList == null || flowableTaskList.isEmpty()) {
                continue;
            }

            for (Task flowableTask : flowableTaskList) {
                currentTask.add(flowableTask.getTaskDefinitionKey());
            }
        }

        return new ArrayList<>(currentTask);
    }

    private String createJsonResponse(String resultOfRepeal) {
        ObjectNode resultObjectNode = objectMapper.createObjectNode();
        resultObjectNode.put("resultOfRollback", resultOfRepeal);

        return resultObjectNode.toString();
    }

    private JSONObject getDataObject(Content content) {
        try {
            return new JSONObject(content.getContent());
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return null;
        }
    }

    private Object getKeyForDestinationMapFromJSON(JSONObject jsonObject, String jsonKey) {
        try {
            return jsonObject.get(jsonKey);
        } catch (JSONException e) {
            logger.error(e.getMessage(), e);
            return null;
        }
    }

    private NodeRef getNodeFromJSON(JSONObject jsonObject) {
        try {
            JSONObject jsonData = jsonObject.getJSONObject(NODE_KEY);
            return RecordsUtils.toNodeRef(RecordRef.valueOf(jsonData.getString("nodeRef")));
        } catch (JSONException e) {
            logger.error(e.getMessage(), e);
            return null;
        }
    }

    private boolean rollbackNotFlowableCaseToDraft(NodeRef caseRef, boolean setDraft) {
        ActivityRef activityRef = activityCommonService.composeRootActivityRef(caseRef);
        caseActivityService.reset(activityRef);
        if (setDraft) {
            nodeService.setProperty(caseRef, InvariantsModel.PROP_IS_DRAFT, true);
        }
        List<CaseActivity> activities = caseActivityService.getActivities(activityRef);
        for (CaseActivity activity : activities) {
            if (Objects.equals(activity.getTitle(), titleOfStartProcessStage)) {
                caseActivityService.startActivity(activity.getActivityRef());
                return true;
            }
        }
        return false;
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setRollbackFlowableTasksService(RollbackFlowableTasksService rollbackFlowableTasksService) {
        this.rollbackFlowableTasksService = rollbackFlowableTasksService;
    }

    public void setCaseActivityService(CaseActivityService caseActivityService) {
        this.caseActivityService = caseActivityService;
    }

    @Autowired
    public void setActivityCommonService(ActivityCommonService activityCommonService) {
        this.activityCommonService = activityCommonService;
    }

    public void setDestinationMap(Map<String, List<String>> destinationMap) {
        this.destinationMap = destinationMap;
    }

    public void setNodeNotFound(String nodeNotFound) {
        this.nodeNotFound = nodeNotFound;
    }

    public void setTargetStatusIsEmpty(String targetStatusIsEmpty) {
        this.targetStatusIsEmpty = targetStatusIsEmpty;
    }

    public void setRollbackTasksError(String rollbackTasksError) {
        this.rollbackTasksError = rollbackTasksError;
    }

    public void setTitleOfStartProcessStage(String titleOfStartProcessStage) {
        this.titleOfStartProcessStage = titleOfStartProcessStage;
    }

    public void setCompleteWithoutErrors(String completeWithoutErrors) {
        this.completeWithoutErrors = completeWithoutErrors;
    }

    public void setWorkflowService(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    public void setFlowableTaskService(FlowableTaskService flowableTaskService) {
        this.flowableTaskService = flowableTaskService;
    }
}
