package ru.citeck.ecos.journals.group.action;

import org.alfresco.repo.workflow.WorkflowModel;
import org.alfresco.repo.workflow.activiti.ActivitiConstants;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import org.springframework.beans.factory.annotation.Autowired;
import ru.citeck.ecos.action.group.impl.GroupActionExecutor;
import ru.citeck.ecos.action.group.ActionStatus;
import ru.citeck.ecos.locks.LockUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author Pavel Simonov
 */
public class CompleteTaskByMirrorExecutor extends GroupActionExecutor {

    public static final String ACTION_ID = "complete-task-by-mirror";
    public static final String TASK_TYPE_KEY = "task-type";
    public static final String TRANSITION_ID = "transition";

    public static final String[] MANDATORY_PARAMS = {TASK_TYPE_KEY, TRANSITION_ID};

    private LockUtils lockUtils;

    @Override
    public String getActionId() {
        return ACTION_ID;
    }

    @Override
    public void invoke(NodeRef mirrorRef, Map<String, String> params) {
        String taskId = String.valueOf(nodeService.getProperty(mirrorRef, WorkflowModel.PROP_TASK_ID));
        String globalTaskId = ActivitiConstants.ENGINE_ID + "$" + taskId;

        String lockId = String.format("%s-%s", "ECOSTask", taskId);
        lockUtils.doWithLock(lockId, () -> {
            workflowService.endTask(globalTaskId, params.get(TRANSITION_ID));
        });
    }

    @Override
    public Map<NodeRef, ActionStatus> invokeBatch(List<NodeRef> nodeRefs, Map<String, String> params) {
        return null;
    }

    @Override
    public boolean isApplicable(NodeRef mirrorRef, Map<String, String> params) {

        Long taskId = (Long) nodeService.getProperty(mirrorRef, WorkflowModel.PROP_TASK_ID);

        if (taskId == null) {
            return false;
        }

        QName taskType = nodeService.getType(mirrorRef);
        QName paramTaskType = QName.resolveToQName(namespaceService, params.get(TASK_TYPE_KEY));

        return Objects.equals(paramTaskType, taskType);
    }

    @Override
    public String[] getMandatoryParams() {
        return MANDATORY_PARAMS;
    }

    @Autowired
    public void setLockUtils(LockUtils lockUtils) {
        this.lockUtils = lockUtils;
    }
}

