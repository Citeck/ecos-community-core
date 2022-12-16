package ru.citeck.ecos.flowable.services;

import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.workflow.WorkflowService;
import org.apache.commons.lang.StringUtils;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ChangeActivityStateBuilder;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskInfo;
import ru.citeck.ecos.flowable.utils.TaskUtils;
import ru.citeck.ecos.workflow.mirror.WorkflowMirrorService;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class RollbackFlowableTasksService {
    private static final String FLOWABLE_PREFIX = "flowable$";

    private RuntimeService runtimeService;
    private WorkflowService workflowService;
    private WorkflowMirrorService workflowMirrorService;
    private FlowableTaskService flowableTaskService;

    public boolean rollbackTasksV2(NodeRef nodeRef, List<String> newActivityIds) {

        List<Task> currentTasks = TaskUtils.getAllActiveTasksFromNode(nodeRef, workflowService, flowableTaskService);
        if (currentTasks.isEmpty() || newActivityIds.isEmpty()) {
            return false;
        }

        Task firstTask = currentTasks.get(0);
        ChangeActivityStateBuilder changeActivityStateBuilder = runtimeService.createChangeActivityStateBuilder();
        changeActivityStateBuilder.processInstanceId(firstTask.getProcessInstanceId());

        List<String> currentActivityIds = currentTasks.stream()
            .map(TaskInfo::getTaskDefinitionKey)
            .filter(StringUtils::isNotBlank)
            .distinct()
            .collect(Collectors.toList());

        if (currentActivityIds.isEmpty()) {
            return false;
        }

        if (currentActivityIds.size() == 1) {

            changeActivityStateBuilder.moveSingleActivityIdToActivityIds(currentActivityIds.get(0), newActivityIds);

        } else if (newActivityIds.size() == 1) {

            changeActivityStateBuilder.moveActivityIdsToSingleActivityId(currentActivityIds, newActivityIds.get(0));

        } else if (currentActivityIds.size() == newActivityIds.size()) {

            for (int idx = 0; idx < currentActivityIds.size(); idx++) {
                changeActivityStateBuilder.moveActivityIdTo(currentActivityIds.get(idx), newActivityIds.get(idx));
            }

        } else if (currentActivityIds.size() < newActivityIds.size()) {

            for (int idx = 0; idx < currentActivityIds.size() - 1; idx++) {
                changeActivityStateBuilder.moveActivityIdTo(currentActivityIds.get(idx), newActivityIds.get(idx));
            }
            changeActivityStateBuilder.moveSingleActivityIdToActivityIds(
                currentActivityIds.get(currentActivityIds.size() - 1),
                newActivityIds.subList(currentActivityIds.size() - 1, newActivityIds.size())
            );

        } else {

            for (int idx = 0; idx < newActivityIds.size() - 1; idx++) {
                changeActivityStateBuilder.moveActivityIdTo(currentActivityIds.get(idx), newActivityIds.get(idx));
            }
            changeActivityStateBuilder.moveActivityIdsToSingleActivityId(
                currentActivityIds.subList(newActivityIds.size() - 1, currentActivityIds.size()),
                newActivityIds.get(newActivityIds.size() - 1)
            );
        }
        changeActivityStateBuilder.changeState();

        for (Task task : currentTasks) {
            workflowMirrorService.mirrorTask(FLOWABLE_PREFIX + task.getId());
        }
        return true;
    }

    public boolean rollbackTasks(NodeRef node, List<String> newActivityIds) {
        List<Task> currentTasks = TaskUtils.getAllActiveTasksFromNode(node, workflowService, flowableTaskService);
        if (currentTasks.isEmpty()) {
            return false;
        }

        Task firstTask = currentTasks.get(0);
        ChangeActivityStateBuilder changeActivityStateBuilder = runtimeService.createChangeActivityStateBuilder();
        changeActivityStateBuilder.processInstanceId(firstTask.getProcessInstanceId());

        if (currentTasks.size() == 1) {
            changeActivityStateBuilder.moveSingleExecutionToActivityIds(firstTask.getExecutionId(), newActivityIds);
            changeActivityStateBuilder.changeState();
        }

        if (currentTasks.size() > 1) {
            List<String> currentTaskIDs = new LinkedList<>();
            for (Task task : currentTasks) {
                currentTaskIDs.add(task.getExecutionId());
            }
            int currentTaskIDsSize = currentTaskIDs.size();
            int newTaskIDsSize = newActivityIds.size();

            if (currentTaskIDsSize > newTaskIDsSize) {
                for (int taskIdIndex = 0; taskIdIndex < newTaskIDsSize - 1; ++taskIdIndex) {
                    changeActivityStateBuilder.moveActivityIdTo(
                            currentTaskIDs.get(taskIdIndex),
                            newActivityIds.get(taskIdIndex)
                    );
                    changeActivityStateBuilder.changeState();
                }
                changeActivityStateBuilder.moveExecutionsToSingleActivityId(
                        currentTaskIDs.subList(newTaskIDsSize - 1, currentTaskIDsSize),
                        newActivityIds.get(newTaskIDsSize - 1)
                );
                changeActivityStateBuilder.changeState();
            }

            if (currentTaskIDsSize < newTaskIDsSize) {
                for (int taskIdIndex = 0; taskIdIndex < currentTaskIDsSize - 1; ++taskIdIndex) {
                    changeActivityStateBuilder.moveActivityIdTo(
                            currentTaskIDs.get(taskIdIndex),
                            newActivityIds.get(taskIdIndex)
                    );
                    changeActivityStateBuilder.changeState();
                }
                changeActivityStateBuilder.moveSingleExecutionToActivityIds(
                        currentTaskIDs.get(currentTaskIDsSize - 1),
                        newActivityIds.subList(currentTaskIDsSize - 1, newTaskIDsSize)
                );
                changeActivityStateBuilder.changeState();
            }

            if (currentTaskIDsSize == newTaskIDsSize) {
                for (int taskIdIndex = 0; taskIdIndex < currentTaskIDsSize; ++taskIdIndex) {
                    changeActivityStateBuilder.moveActivityIdTo(
                            currentTaskIDs.get(taskIdIndex),
                            newActivityIds.get(taskIdIndex)
                    );
                    changeActivityStateBuilder.changeState();
                }
            }
        }
        for (Task task : currentTasks) {
            workflowMirrorService.mirrorTask(FLOWABLE_PREFIX + task.getId());
        }
        currentTasks = TaskUtils.getAllActiveTasksFromNode(node, workflowService, flowableTaskService);
        return currentTasks.size() == newActivityIds.size();
    }

    public void setRuntimeService(RuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    public void setWorkflowService(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    public void setWorkflowMirrorService(WorkflowMirrorService workflowMirrorService) {
        this.workflowMirrorService = workflowMirrorService;
    }

    public void setFlowableTaskService(FlowableTaskService flowableTaskService) {
        this.flowableTaskService = flowableTaskService;
    }
}
