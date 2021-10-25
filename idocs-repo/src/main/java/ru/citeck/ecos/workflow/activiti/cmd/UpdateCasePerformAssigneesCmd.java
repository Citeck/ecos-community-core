package ru.citeck.ecos.workflow.activiti.cmd;

import org.activiti.engine.impl.bpmn.behavior.ParallelMultiInstanceBehavior;
import org.activiti.engine.impl.cmd.NeedsActiveExecutionCmd;
import org.activiti.engine.impl.interceptor.CommandContext;
import org.activiti.engine.impl.persistence.entity.ExecutionEntity;
import org.activiti.engine.impl.persistence.entity.TaskEntity;
import org.activiti.engine.impl.persistence.entity.TaskEntityManager;
import org.activiti.engine.impl.pvm.process.ActivityImpl;
import org.alfresco.repo.workflow.activiti.ActivitiConstants;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import ru.citeck.ecos.model.CasePerformModel;
import ru.citeck.ecos.service.CiteckServices;
import ru.citeck.ecos.utils.RepoUtils;
import ru.citeck.ecos.workflow.mirror.WorkflowMirrorService;
import ru.citeck.ecos.workflow.perform.CasePerformUtils;

import java.util.*;

/**
 * @author Pavel Simonov
 */
public class UpdateCasePerformAssigneesCmd extends NeedsActiveExecutionCmd<Void> {

    private static final String NUMBER_OF_INSTANCES = "nrOfInstances";
    private static final String NUMBER_OF_ACTIVE_INSTANCES = "nrOfActiveInstances";
    private static final String ACTIVITI_PREFIX = ActivitiConstants.ENGINE_ID + "$";

    private WorkflowMirrorService mirrorService;
    private TaskEntityManager taskEntityManager;
    private CasePerformUtils casePerformUtils;
    private NamespaceService namespaceService;
    private NodeService nodeService;

    private Set<NodeRef> performersAdd;
    private Set<NodeRef> performersRemove;

    public UpdateCasePerformAssigneesCmd(String workflowId,
                                         Set<NodeRef> performersAdd,
                                         Set<NodeRef> performersRemove,
                                         ServiceRegistry serviceRegistry) {
        super(workflowId.replace(ACTIVITI_PREFIX, ""));
        this.namespaceService = serviceRegistry.getNamespaceService();
        this.performersRemove = performersRemove;
        this.performersAdd = performersAdd;
        this.casePerformUtils = (CasePerformUtils) serviceRegistry.getService(CiteckServices.CASE_PERFORM_UTILS);
        this.nodeService = serviceRegistry.getNodeService();
        this.mirrorService = (WorkflowMirrorService) serviceRegistry.getService(CiteckServices.WORKFLOW_MIRROR_SERVICE);
    }

    @Override
    protected Void execute(CommandContext commandContext, ExecutionEntity processExecution) {

        taskEntityManager = commandContext.getTaskEntityManager();

        String processDefinitionId = processExecution.getProcessDefinition().getName();
        if (!CasePerformUtils.PROC_DEFINITION_NAME.equals(processDefinitionId)) {
            return null;
        }

        ExecutionEntity execution = processExecution.findExecution(CasePerformUtils.SUB_PROCESS_NAME);
        if (execution == null) return null;

        Boolean syncEnabled = (Boolean) execution.getVariable(toString(CasePerformModel.PROP_SYNC_ROLES_TO_WORKFLOW));

        if (syncEnabled != null && syncEnabled) {
            removePerformers(execution, performersRemove);
            addPerformers(execution, performersAdd);
        }

        return null;
    }

    private void addPerformers(ExecutionEntity execution, Set<NodeRef> performers) {

        if (performers.isEmpty()) return;

        List<NodeRef> additionalPerformers = new ArrayList<>(performers);
        List<ExecutionEntity> parallelExecutions = new ArrayList<>(execution.getExecutions());

        for (ExecutionEntity entity : parallelExecutions) {

            TaskEntity task = getTaskFromParallelExecution(entity);
            if (task == null) continue;

            NodeRef performer = casePerformUtils.getFirstPerformer(task);
            additionalPerformers.remove(performer);
        }

        if (!additionalPerformers.isEmpty()) {
            AddParallelExecutionInstanceCmd.addParallelExecution(
                    execution,
                    CasePerformUtils.SUB_PROCESS_NAME,
                    additionalPerformers
            );
        }
    }

    private void removePerformers(ExecutionEntity execution, Set<NodeRef> performers) {

        if (performers.isEmpty()) return;

        Set<NodeRef> deleted = deleteTasksByPerformers(execution, performers);

        if (!deleted.isEmpty()) {
            ActivityImpl activity = execution.getActivity();
            ParallelMultiInstanceBehavior activityBehavior = (ParallelMultiInstanceBehavior) activity.getActivityBehavior();
            Collection collection = (Collection) activityBehavior.getCollectionExpression().getValue(execution);
            collection.removeAll(deleted);
        }
    }

    private Set<NodeRef> deleteTasksByPerformers(ExecutionEntity execution, Set<NodeRef> performers) {

        List<ExecutionEntity> parallelExecutions = new ArrayList<>(execution.getExecutions());
        Set<NodeRef> deleted = new HashSet<>();

        for (ExecutionEntity entity : parallelExecutions) {

            TaskEntity task = getTaskFromParallelExecution(entity);
            if (task == null) continue;

            NodeRef performer = casePerformUtils.getFirstPerformer(task);
            if (performers.contains(performer)) {

                Collection mandatoryTasks = (Collection) execution.getVariable(CasePerformUtils.MANDATORY_TASKS);
                mandatoryTasks.remove(task.getId());

                taskEntityManager.deleteTask(task, null, true);
                entity.deleteCascade(null);

                NodeRef mirrorRef = mirrorService.getTaskMirror(ACTIVITI_PREFIX + task.getId());
                if (mirrorRef != null && nodeService.exists(mirrorRef)) {
                    RepoUtils.deleteNode(mirrorRef, nodeService);
                }

                deleted.add(performer);
            }
        }

        if (!deleted.isEmpty()) {
            int numberOfInstances = (Integer) execution.getVariableLocal(NUMBER_OF_INSTANCES);
            int numberOfActiveInstances = (Integer) execution.getVariableLocal(NUMBER_OF_ACTIVE_INSTANCES);
            execution.setVariableLocal(NUMBER_OF_INSTANCES, numberOfInstances - deleted.size());
            execution.setVariableLocal(NUMBER_OF_ACTIVE_INSTANCES, numberOfActiveInstances - deleted.size());
        }

        return deleted;
    }

    private TaskEntity getTaskFromParallelExecution(ExecutionEntity execution) {
        List<ExecutionEntity> taskExecutions = execution.getExecutions();
        if (taskExecutions == null || taskExecutions.isEmpty()) {
            return null;
        }
        List<TaskEntity> tasks = taskExecutions.get(0).getTasks();
        if (tasks == null || tasks.isEmpty()) {
            return null;
        }
        return tasks.get(0);
    }

    private String toString(QName qname) {
        return qname.toPrefixString(namespaceService).replaceAll(":", "_");
    }

}
