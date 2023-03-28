package ru.citeck.ecos.webscripts.tasks;

import org.alfresco.repo.web.scripts.workflow.AbstractWorkflowWebscript;
import org.alfresco.repo.web.scripts.workflow.WorkflowModelBuilder;
import org.alfresco.service.cmr.workflow.WorkflowTask;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;
import ru.citeck.ecos.workflow.owner.OwnerService;
import ru.citeck.ecos.workflow.owner.OwnerAction;

import java.util.HashMap;
import java.util.Map;

public class ChangeTaskOwnerPut extends AbstractWorkflowWebscript {

    private static final String CM_OWNER_PARAM = "cm_owner";
    private static final String TASK_ID_PARAM = "taskId";
    private static final String ACTION_PARAM = "action";

    private OwnerService ownerService;

    @Override
    protected Map<String, Object> buildModel(WorkflowModelBuilder modelBuilder, WebScriptRequest request, Status status,
                                             Cache cache) {

        String taskId = request.getServiceMatch().getTemplateVars().get(TASK_ID_PARAM);

        try {
            @SuppressWarnings("unchecked")
            JSONObject parameters = (JSONObject) request.parseContent();
            String owner = parameters.getString(CM_OWNER_PARAM);
            if ("null".equals(owner)) {
                owner = null;
            }

            OwnerAction action = getOwnerAction(parameters);

            WorkflowTask workflowTask = ownerService.changeOwner(taskId, action, owner);

            Map<String, Object> model = new HashMap<>();
            model.put("workflowTask", modelBuilder.buildDetailed(workflowTask));
            return model;
        } catch (JSONException e) {
            throw new WebScriptException(400, "Could not parse JSON from request.", e);
        } catch (Exception e) {
            throw new WebScriptException(400, e.getMessage(), e);
        }
    }

    private OwnerAction getOwnerAction(JSONObject parameters) throws Exception {
        try {
            return OwnerAction.valueOf(parameters.getString(ACTION_PARAM).toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new Exception("Unrecognized parameter value. Parameter " + ACTION_PARAM
                    + " is expected to be either claim or release");
        }
    }

    @Autowired
    public void setOwnerService(OwnerService ownerService) {
        this.ownerService = ownerService;
    }
}
