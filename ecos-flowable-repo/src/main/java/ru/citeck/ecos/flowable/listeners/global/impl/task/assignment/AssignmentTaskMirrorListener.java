package ru.citeck.ecos.flowable.listeners.global.impl.task.assignment;

import org.flowable.task.service.delegate.DelegateTask;
import ru.citeck.ecos.flowable.listeners.global.GlobalAssignmentTaskListener;
import ru.citeck.ecos.workflow.mirror.WorkflowMirrorService;

import static ru.citeck.ecos.flowable.constants.FlowableConstants.ENGINE_PREFIX;

public class AssignmentTaskMirrorListener implements GlobalAssignmentTaskListener {

    private WorkflowMirrorService workflowMirrorService;

    @Override
    public void notify(DelegateTask delegateTask) {
        workflowMirrorService.mirrorTask(ENGINE_PREFIX + delegateTask.getId(), false);
    }

    public void setWorkflowMirrorService(WorkflowMirrorService workflowMirrorService) {
        this.workflowMirrorService = workflowMirrorService;
    }
}
