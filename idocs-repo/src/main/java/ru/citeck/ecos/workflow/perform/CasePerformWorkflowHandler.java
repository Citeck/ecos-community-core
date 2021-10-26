package ru.citeck.ecos.workflow.perform;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.AuthorityType;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.extensions.surf.util.I18NUtil;
import ru.citeck.ecos.model.CasePerformModel;
import ru.citeck.ecos.model.CiteckWorkflowModel;
import ru.citeck.ecos.workflow.variable.type.NodeRefsList;
import ru.citeck.ecos.workflow.variable.type.TaskConfig;
import ru.citeck.ecos.workflow.variable.type.TaskConfigs;
import ru.citeck.ecos.workflow.variable.type.TaskStages;

import java.io.Serializable;
import java.util.*;

/**
 * @author Pavel Simonov
 */
@Slf4j
public class CasePerformWorkflowHandler implements Serializable {

    private static final long serialVersionUID = -2309572351324327537L;
    private static final int WORKFLOW_VERSION = 1;

    private CasePerformUtils utils;
    private AuthorityService authorityService;

    public void init() {
    }

    public void onWorkflowStart(PerformExecution execution) {

        execution.setVariable(CasePerformUtils.WORKFLOW_VERSION_KEY, WORKFLOW_VERSION);

        if (execution.hasVariable(CasePerformUtils.OPTIONAL_PERFORMERS)) {
            Collection<NodeRef> optionalPerformers = utils.getNodeRefsList(execution, CasePerformUtils.OPTIONAL_PERFORMERS);
            Collection<NodeRef> expandedPerformers = new NodeRefsList();

            for (NodeRef performer : optionalPerformers) {
                utils.addIfNotContains(expandedPerformers, performer);
                Set<NodeRef> authorities = utils.getContainedAuthorities(performer, AuthorityType.USER, true);
                utils.addAllIfNotContains(expandedPerformers, authorities);
            }

            execution.setVariableLocal(CasePerformUtils.OPTIONAL_PERFORMERS, expandedPerformers);
        }
    }

    public void onRepeatIterationGatewayStarted(PerformExecution execution) {

        TaskStages stages = utils.getTaskStages(execution);

        if (stages != null && !stages.isEmpty()) {

            Integer stageIndex = (Integer) execution.getVariable(CasePerformUtils.PERFORM_STAGE_IDX);
            if (stageIndex == null) {
                stageIndex = 0;
                execution.setVariable(CasePerformUtils.PERFORM_STAGE_IDX, stageIndex);
            }

            TaskConfigs configs = stages.get(stageIndex);
            NodeRefsList performers = new NodeRefsList();

            for (TaskConfig config : configs) {
                String performer = config.getPerformer();
                if (StringUtils.isNotBlank(performer)) {
                    NodeRef performerRef = authorityService.getAuthorityNodeRef(performer);
                    performers.add(performerRef);
                }
            }

            execution.setVariable(CasePerformUtils.TASK_CONFIGS, configs);
            String performersKey = utils.toString(CasePerformModel.ASSOC_PERFORMERS);
            execution.setVariable(performersKey, performers);

        } else {

            execution.setVariable(CasePerformUtils.PERFORM_STAGE_IDX, null);
            execution.setVariable(CasePerformUtils.TASK_CONFIGS, null);
        }
    }

    public void onBeforePerformingFlowTake(PerformExecution execution) {

        String performersKey = utils.toString(CasePerformModel.ASSOC_PERFORMERS);
        Collection<NodeRef> initialPerformers = utils.getNodeRefsList(execution, performersKey);
        Collection<NodeRef> excludedPerformers = utils.getNodeRefsList(execution, CasePerformUtils.EXCLUDED_PERFORMERS);
        List<NodeRef> performers = new NodeRefsList();

        for (NodeRef performer : initialPerformers) {
            if (!excludedPerformers.contains(performer)) {
                performers.add(performer);
            }
        }

        execution.setVariableLocal(CasePerformUtils.PERFORMERS, performers);
        utils.fillRolesByPerformers(execution);
    }

    public void onSkipPerformingGatewayStarted(PerformExecution execution) {

        Collection<NodeRef> optionalPerformers = utils.getNodeRefsList(execution, CasePerformUtils.OPTIONAL_PERFORMERS);
        Collection<NodeRef> performers = utils.getNodeRefsList(execution, CasePerformUtils.PERFORMERS);
        boolean hasMandatoryTasks = false;

        for (NodeRef performer : performers) {
            if (!optionalPerformers.contains(performer)) {
                hasMandatoryTasks = true;
                break;
            }
        }

        boolean skipPerforming;
        if (performers.isEmpty()) {
            String candidatesKey = utils.toString(CasePerformModel.ASSOC_CANDIDATES);
            Collection<NodeRef> candidates = utils.getNodeRefsList(execution, candidatesKey);
            skipPerforming = candidates.isEmpty();
        } else {
            skipPerforming = !hasMandatoryTasks;
        }

        execution.setVariableLocal(CasePerformUtils.SKIP_PERFORMING, skipPerforming);
    }

    /* skip way */

    public void onSkipPerformingFlowTake(PerformExecution execution) {

    }

    /* skip way */

    /* perform way */

    public void onPerformingFlowTake(PerformExecution execution) {
        TaskConfigs configs = (TaskConfigs) execution.getVariable(CasePerformUtils.TASK_CONFIGS);
        if (configs == null) {
            configs = createTaskConfigs(execution);
            execution.setVariableLocal(CasePerformUtils.TASK_CONFIGS, configs);
        }
    }

    private TaskConfigs createTaskConfigs(PerformExecution execution) {

        Collection<NodeRef> performers = utils.getNodeRefsList(execution, CasePerformUtils.PERFORMERS);

        TaskConfigs taskConfigs = new TaskConfigs();

        if (!performers.isEmpty()) {

            for (NodeRef performer : performers) {

                String authorityName = utils.getAuthorityName(performer);

                if (StringUtils.isNotBlank(authorityName)) {
                    TaskConfig config = new TaskConfig();
                    config.setPerformer(authorityName);
                    taskConfigs.add(config);
                }
            }

        } else {

            TaskConfig config = new TaskConfig();

            String candidatesKey = utils.toString(CasePerformModel.ASSOC_CANDIDATES);
            Collection<NodeRef> candidates = utils.getNodeRefsList(execution, candidatesKey);

            for (NodeRef candidateRef : candidates) {
                String authorityName = utils.getAuthorityName(candidateRef);
                config.addCandidate(authorityName);
            }

            taskConfigs.add(config);
        }

        Date dueDate = (Date) execution.getVariable("bpm_workflowDueDate");
        String formKey = (String) execution.getVariable("wfcp_formKey");
        for (TaskConfig config : taskConfigs) {
            config.setDueDate(dueDate);
            config.setFormKey(formKey);
        }

        return taskConfigs;
    }

    public void onBeforePerformTaskCreated(PerformExecution execution) {

        Object taskConfigObj = execution.getVariableLocal("taskConfig");
        TaskConfig taskConfig;

        if (taskConfigObj instanceof NodeRef) {

            taskConfig = new TaskConfig();

            String authorityName = utils.getAuthorityName((NodeRef) taskConfigObj);
            if (StringUtils.isNotBlank(authorityName)) {
                taskConfig.setPerformer(authorityName);
            }

            taskConfig.setDueDate((Date) execution.getVariable("bpm_workflowDueDate"));
            taskConfig.setFormKey((String) execution.getVariable("wfcp_formKey"));

            execution.setVariableLocal("taskConfig", taskConfig);

        } else if (taskConfigObj instanceof Map) {

            Map data = (Map) taskConfigObj;

            taskConfig = new TaskConfig();
            taskConfig.setFormKey((String) data.get(CasePerformUtils.TASK_CONF_FORM_KEY));
            taskConfig.setDueDate((Date) data.get(CasePerformUtils.TASK_CONF_DUE_DATE));
            taskConfig.setAssignee((String) data.get(CasePerformUtils.TASK_CONF_ASSIGNEE));
            taskConfig.setCandidateGroups((List) data.get(CasePerformUtils.TASK_CONF_CANDIDATE_GROUPS));
            taskConfig.setCandidateUsers((List) data.get(CasePerformUtils.TASK_CONF_CANDIDATE_USERS));
            taskConfig.setCategory((String) data.get(CasePerformUtils.TASK_CONF_CATEGORY));
            taskConfig.setPriority((Integer) data.get(CasePerformUtils.TASK_CONF_PRIORITY));
        } else {
            taskConfig = (TaskConfig) taskConfigObj;
        }

        NodeRef performer = utils.authorityToNodeRef(taskConfig.getPerformer());
        execution.setVariableLocal("wfcp_performer", performer);
    }

    public void onPerformTaskCreated(PerformExecution execution, PerformTask task) {

        utils.shareVariables(execution, task);

        Collection<NodeRef> optionalPerformers = utils.getNodeRefsList(execution, CasePerformUtils.OPTIONAL_PERFORMERS);
        NodeRef performer = utils.getFirstPerformer(task);

        boolean isOptional = optionalPerformers.contains(performer);

        task.setVariableLocal(utils.toString(CiteckWorkflowModel.PROP_IS_OPTIONAL_TASK), isOptional);
        if (!isOptional) {
            Collection<String> mandatoryTasks = utils.getStringsList(execution, CasePerformUtils.MANDATORY_TASKS);
            utils.addIfNotContains(mandatoryTasks, task.getId());
            execution.setVariable(CasePerformUtils.MANDATORY_TASKS, mandatoryTasks);
        }

        utils.removeDelegates(task);
        task.setVariableLocal(utils.toString(CasePerformModel.ASSOC_CASE_ROLE), utils.getCaseRole(performer, execution));
    }

    public void onPerformTaskAssigned(PerformExecution execution, PerformTask task) {

        Boolean syncEnabled = (Boolean) execution.getVariable(utils.toString(CasePerformModel.PROP_SYNC_WORKFLOW_TO_ROLES));

        if (syncEnabled != null && syncEnabled) {
            NodeRef performerRef = utils.getFirstPerformer(task);
            String assignee = task.getAssignee();

            if (assignee != null) {
                NodeRef assigneeRef = authorityService.getAuthorityNodeRef(assignee);
                if (!assigneeRef.equals(performerRef)) {
                    utils.setPerformer(task, assigneeRef);
                }
            } else if (performerRef == null || !utils.hasCandidate(task, performerRef)) {

                utils.setPerformer(task, utils.getFirstGroupCandidate(task));
            }
        }
    }

    public void onPerformTaskCompleted(PerformExecution execution, PerformTask task) {

        utils.shareVariables(task, execution);
        utils.saveTaskResult(execution, task);

        if (utils.isCommentMandatory(execution, task)) {
            String comment = (String) task.getVariableLocal("bpm_comment");
            if (StringUtils.isBlank(comment)) {
                throw new AlfrescoRuntimeException(I18NUtil.getMessage("wfcf_confirmworkflow.message_comment_is_empty"));
            }
        }

        Collection<String> mandatoryTasks = utils.getStringsList(execution, CasePerformUtils.MANDATORY_TASKS);
        mandatoryTasks.remove(task.getId());

        boolean abortOutcomeReceived = utils.isAbortOutcomeReceived(execution, task);
        execution.setVariable(CasePerformUtils.ABORT_PROCESS, abortOutcomeReceived);

        boolean abortPerforming = mandatoryTasks.isEmpty() || abortOutcomeReceived;
        execution.setVariable(CasePerformUtils.ABORT_PERFORMING, abortPerforming);

        execution.setVariable(CasePerformUtils.MANDATORY_TASKS, mandatoryTasks);
    }

    public void onAfterPerformingFlowTake(PerformExecution execution) {

    }

    public void onRepeatPerformingGatewayStarted(PerformExecution execution) {
        Boolean abortProcess = (Boolean) execution.getVariable(CasePerformUtils.ABORT_PROCESS);
        if (Boolean.TRUE.equals(abortProcess)) {
            execution.setVariable(CasePerformUtils.REPEAT_PERFORMING, false);
        } else {
            Integer stageIndex = (Integer) execution.getVariable(CasePerformUtils.PERFORM_STAGE_IDX);
            TaskStages stages = utils.getTaskStages(execution);
            boolean repeatRequired = false;
            if (stageIndex != null && stages != null) {
                stageIndex++;
                repeatRequired = stageIndex < stages.size();
                if (repeatRequired) {
                    execution.setVariable(CasePerformUtils.PERFORM_STAGE_IDX, stageIndex);
                }
            }
            execution.setVariable(CasePerformUtils.REPEAT_PERFORMING, repeatRequired);
        }
    }

    /* perform way */

    public void onWorkflowEnd(PerformExecution execution) {
        utils.getNodeRefsList(execution, CasePerformUtils.OPTIONAL_PERFORMERS).clear();
        utils.getNodeRefsList(execution, CasePerformUtils.EXCLUDED_PERFORMERS).clear();
    }

    public void setUtils(CasePerformUtils utils) {
        this.utils = utils;
    }

    public void setAuthorityService(AuthorityService authorityService) {
        this.authorityService = authorityService;
    }

}
