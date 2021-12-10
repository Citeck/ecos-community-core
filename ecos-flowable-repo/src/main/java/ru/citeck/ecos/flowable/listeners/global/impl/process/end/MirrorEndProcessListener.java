package ru.citeck.ecos.flowable.listeners.global.impl.process.end;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import ru.citeck.ecos.flowable.constants.FlowableConstants;
import ru.citeck.ecos.flowable.listeners.global.GlobalEndExecutionListener;
import ru.citeck.ecos.workflow.mirror.WorkflowMirrorService;

import java.util.List;

/**
 * Mirror end process listener
 */
public class MirrorEndProcessListener implements GlobalEndExecutionListener {

    private WorkflowMirrorService workflowMirrorService;
    private NodeService nodeService;

    /**
     * Notify
     *
     * @param delegateExecution Execution
     */
    @Override
    public void notify(DelegateExecution delegateExecution) {
        if (!(delegateExecution instanceof ExecutionEntity)) {
            return;
        }

        ExecutionEntity entity = (ExecutionEntity) delegateExecution;
        String deleteReason = entity.getDeleteReason();

        if (!entity.isEnded() && deleteReason != null
            && (deleteReason.equals("cancelled") || deleteReason.equals("deleted"))) {

            String workflowId = FlowableConstants.ENGINE_PREFIX + entity.getProcessInstanceId();
            List<NodeRef> mirrors = workflowMirrorService.getTaskMirrorsByWorkflowId(workflowId);
            for (NodeRef mirror : mirrors) {
                final NodeRef taskMirror = mirror;
                AuthenticationUtil.runAs((AuthenticationUtil.RunAsWork<Void>) () -> {
                    nodeService.deleteNode(taskMirror);
                    return null;
                }, AuthenticationUtil.getSystemUserName());
            }
        } else {
            if (entity.getProcessInstance().isEnded()) {
                String workflowId = FlowableConstants.ENGINE_PREFIX + entity.getProcessInstanceId();
                List<NodeRef> mirrors = workflowMirrorService.getTaskMirrorsByWorkflowId(workflowId);
                for (NodeRef mirror : mirrors) {
                    workflowMirrorService.mirrorTask((String) nodeService.getProperty(mirror, ContentModel.PROP_NAME), false);
                }
            }
        }
    }

    public void setWorkflowMirrorService(WorkflowMirrorService workflowMirrorService) {
        this.workflowMirrorService = workflowMirrorService;
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }
}
