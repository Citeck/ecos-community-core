package ru.citeck.ecos.flowable.listeners.global.impl.task.create;

import org.flowable.task.service.delegate.DelegateTask;
import ru.citeck.ecos.flowable.listeners.global.GlobalCreateTaskListener;
import ru.citeck.ecos.workflow.mirror.WorkflowMirrorService;

import static ru.citeck.ecos.flowable.constants.FlowableConstants.ENGINE_PREFIX;

/**
 * Flowable mirror listener
 */
public class CreateTaskMirrorListener implements GlobalCreateTaskListener {

    private WorkflowMirrorService workflowMirrorService;

    /**
     * Notify
     *
     * @param delegateTask Task
     */
    @Override
    public void notify(DelegateTask delegateTask) {
        workflowMirrorService.mirrorTask(ENGINE_PREFIX + delegateTask.getId());
    }

    /**
     * Set workflow mirror service
     *
     * @param workflowMirrorService Workflow mirror service
     */
    public void setWorkflowMirrorService(WorkflowMirrorService workflowMirrorService) {
        this.workflowMirrorService = workflowMirrorService;
    }
}
