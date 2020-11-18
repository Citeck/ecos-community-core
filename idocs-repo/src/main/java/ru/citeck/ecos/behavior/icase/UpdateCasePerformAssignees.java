package ru.citeck.ecos.behavior.icase;

import org.activiti.engine.RuntimeService;
import org.activiti.engine.impl.ServiceImpl;
import org.activiti.engine.impl.interceptor.CommandExecutor;
import org.alfresco.repo.policy.Behaviour;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.TransactionalResourceHelper;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.AssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.Pair;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.behavior.JavaBehaviour;
import ru.citeck.ecos.icase.activity.dto.*;
import ru.citeck.ecos.icase.activity.service.ActivityCommonService;
import ru.citeck.ecos.icase.activity.service.CaseActivityService;
import ru.citeck.ecos.icase.activity.service.eproc.EProcActivityService;
import ru.citeck.ecos.icase.activity.service.eproc.EProcUtils;
import ru.citeck.ecos.icase.activity.service.eproc.importer.parser.CmmnInstanceConstants;
import ru.citeck.ecos.icase.activity.service.eproc.importer.pojo.OptimizedProcessDefinition;
import ru.citeck.ecos.model.CasePerformModel;
import ru.citeck.ecos.model.ICaseRoleModel;
import ru.citeck.ecos.model.ICaseTaskModel;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.role.CaseRolePolicies;
import ru.citeck.ecos.role.CaseRoleService;
import ru.citeck.ecos.service.AlfrescoServices;
import ru.citeck.ecos.service.CiteckServices;
import ru.citeck.ecos.utils.AlfActivityUtils;
import ru.citeck.ecos.workflow.activiti.cmd.UpdateCasePerformAssigneesCmd;
import ru.citeck.ecos.workflow.perform.CasePerformUtils;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Pavel Simonov
 */
@Component
@DependsOn("idocs.dictionaryBootstrap")
public class UpdateCasePerformAssignees implements CaseRolePolicies.OnRoleAssigneesChangedPolicy,
        CaseRolePolicies.OnCaseRolesAssigneesChangedPolicy {

    private static final String UPDATE_BY_ROLE_KEY = UpdateCasePerformAssignees.class + ".update-by-role";

    private ServiceRegistry serviceRegistry;

    private NodeService nodeService;
    private RuntimeService runtimeService;
    private PolicyComponent policyComponent;
    private CaseActivityService caseActivityService;
    private AlfActivityUtils alfActivityUtils;
    private ActivityCommonService activityCommonService;
    private EProcActivityService eprocActivityService;
    private CaseRoleService caseRoleService;

    @Autowired
    public UpdateCasePerformAssignees(ServiceRegistry serviceRegistry,
                                      ActivityCommonService activityCommonService,
                                      EProcActivityService eprocActivityService,
                                      CaseRoleService caseRoleService) {
        this.serviceRegistry = serviceRegistry;
        this.nodeService = serviceRegistry.getNodeService();
        this.runtimeService = (RuntimeService) serviceRegistry.getService(AlfrescoServices.ACTIVITI_RUNTIME_SERVICE);
        this.policyComponent = (PolicyComponent) serviceRegistry.getService(AlfrescoServices.POLICY_COMPONENT);
        this.caseActivityService = (CaseActivityService) serviceRegistry.getService(CiteckServices.CASE_ACTIVITY_SERVICE);
        this.alfActivityUtils = (AlfActivityUtils) serviceRegistry.getService(CiteckServices.ALF_ACTIVITY_UTILS);
        this.activityCommonService = activityCommonService;
        this.eprocActivityService = eprocActivityService;
        this.caseRoleService = caseRoleService;
    }

    @PostConstruct
    public void init() {
        this.policyComponent.bindClassBehaviour(
                CaseRolePolicies.OnCaseRolesAssigneesChangedPolicy.QNAME,
                ICaseRoleModel.ASPECT_HAS_ROLES,
                new JavaBehaviour(this, "onCaseRolesAssigneesChanged", Behaviour.NotificationFrequency.TRANSACTION_COMMIT)
        );
        this.policyComponent.bindClassBehaviour(
                CaseRolePolicies.OnRoleAssigneesChangedPolicy.QNAME,
                ICaseRoleModel.TYPE_ROLE,
                new JavaBehaviour(this, "onRoleAssigneesChanged", Behaviour.NotificationFrequency.EVERY_EVENT)
        );
    }

    @Override
    public void onRoleAssigneesChanged(final NodeRef roleRef, final Set<NodeRef> added, final Set<NodeRef> removed) {
        Map<NodeRef, RoleState> byRole = TransactionalResourceHelper.getMap(UPDATE_BY_ROLE_KEY);
        byRole.put(roleRef, RoleState.merge(byRole.get(roleRef), new RoleState(added, removed)));
    }

    @Override
    public void onCaseRolesAssigneesChanged(NodeRef caseRef) {
        AuthenticationUtil.runAsSystem((AuthenticationUtil.RunAsWork<Void>) () -> {
            updateTasks(caseRef);
            return null;
        });
    }

    private void updateTasks(NodeRef caseRef) {

        Map<NodeRef, RoleState> byRole = TransactionalResourceHelper.getMap(UPDATE_BY_ROLE_KEY);

        for (Map.Entry<NodeRef, RoleState> entry : byRole.entrySet()) {
            NodeRef roleRef = entry.getKey();
            if (!nodeService.exists(roleRef)) {
                continue;
            }
            Set<NodeRef> added = entry.getValue().added;
            Set<NodeRef> removed = entry.getValue().removed;
            updateTask(caseRef, roleRef, added, removed);
        }

        byRole.clear();
    }

    private void updateTask(NodeRef caseRef, NodeRef roleRef, Set<NodeRef> added, Set<NodeRef> removed) {

        Set<String> activeWorkflows = getActiveWorkflows(caseRef, roleRef);

        CommandExecutor commandExecutor = getCommandExecutor();
        if (commandExecutor != null) {
            for (String workflowId : activeWorkflows) {

                String id = workflowId.replace("activiti$", "");

                Set<NodeRef> toAdd = new HashSet<>(added);
                Set<NodeRef> toRemove = new HashSet<>(removed);

                Map<NodeRef, Map<String, Map<NodeRef, NodeRef>>> reassignmentByRole =
                        TransactionalResourceHelper.getMap(CasePerformUtils.REASSIGNMENT_KEY);

                Map<String, Map<NodeRef, NodeRef>> reassignmentByWorkflow = reassignmentByRole.get(roleRef);
                if (reassignmentByWorkflow != null) {
                    Map<NodeRef, NodeRef> reassignment = reassignmentByWorkflow.get(id);
                    if (reassignment != null) {
                        for (Map.Entry<NodeRef, NodeRef> entry : reassignment.entrySet()) {
                            toRemove.remove(entry.getKey());
                            toAdd.remove(entry.getValue());
                        }
                    }
                }

                if (!toAdd.isEmpty() || !toRemove.isEmpty()) {
                    commandExecutor.execute(new UpdateCasePerformAssigneesCmd(id, toAdd, toRemove, serviceRegistry));
                }
            }
        }
    }

    private CommandExecutor getCommandExecutor() {
        if (runtimeService instanceof ServiceImpl) {
            return ((ServiceImpl) runtimeService).getCommandExecutor();
        }
        return null;
    }

    private Set<String> getActiveWorkflows(NodeRef caseRef, NodeRef roleRef) {
        CaseServiceType caseType = activityCommonService.getCaseType(caseRef);
        if (caseType == CaseServiceType.ALFRESCO) {
            return getAlfActiveWorkflows(roleRef);
        } else {
            return getEProcActiveWorkflows(caseRef, roleRef);
        }
    }

    private Set<String> getAlfActiveWorkflows(NodeRef roleRef) {
        List<AssociationRef> caseTaskRefs = nodeService.getSourceAssocs(roleRef, CasePerformModel.ASSOC_PERFORMERS_ROLES);
        Set<String> activeWorkflows = new HashSet<>();

        for (AssociationRef ref : caseTaskRefs) {

            String workflowId = getActiveWorkflowID(ref.getSourceRef());

            if (workflowId != null) {
                activeWorkflows.add(workflowId);
            }
        }

        return activeWorkflows;
    }

    private String getActiveWorkflowID(NodeRef taskRef) {
        QName type = nodeService.getType(taskRef);
        if (!CasePerformModel.TYPE_PERFORM_CASE_TASK.equals(type)) {
            return null;
        }
        ActivityRef activityRef = alfActivityUtils.composeActivityRef(taskRef);
        CaseActivity activity = caseActivityService.getActivity(activityRef);
        if (activity.isActive()) {
            return (String) nodeService.getProperty(taskRef, ICaseTaskModel.PROP_WORKFLOW_INSTANCE_ID);
        }
        return null;
    }

    private Set<String> getEProcActiveWorkflows(NodeRef caseNodeRef, NodeRef roleRef) {
        RecordRef caseRef = RecordRef.valueOf(caseNodeRef.toString());
        Set<ActivityDefinition> taskDefinitions = getTaskDefinitionsByRole(caseRef, roleRef);
        Set<ActivityInstance> taskInstances = getTaskInstancesByDefinitions(caseRef, taskDefinitions);

        Set<String> activeWorkflows = new HashSet<>();

        for (ActivityInstance taskInstance : taskInstances) {
            if (taskInstance.getState() == ActivityState.STARTED) {
                String workflowId = EProcUtils.getAnyAttribute(taskInstance, CmmnInstanceConstants.WORKFLOW_INSTANCE_ID);
                if (StringUtils.isNotBlank(workflowId)) {
                    activeWorkflows.add(workflowId);
                }
            }
        }

        return activeWorkflows;
    }

    private Set<ActivityDefinition> getTaskDefinitionsByRole(RecordRef caseRef, NodeRef roleRef) {

        Pair<String, OptimizedProcessDefinition> optimizedDefinitionWithRevisionId =
                eprocActivityService.getOptimizedDefinitionWithRevisionId(caseRef)
                    .orElseThrow(() -> new IllegalStateException("Definition is not found. "
                                                               + "CaseRef: " + caseRef + " roleRef: " + roleRef));

        OptimizedProcessDefinition optimizedProcessDefinition = optimizedDefinitionWithRevisionId.getSecond();

        String varName = caseRoleService.getRoleId(roleRef);

        Map<String, Set<ActivityDefinition>> cache = optimizedProcessDefinition.getRoleVarNameToTaskDefinitionCache();
        if (cache == null) {
            return Collections.emptySet();
        }

        return cache.get(varName);
    }

    private Set<ActivityInstance> getTaskInstancesByDefinitions(RecordRef caseRef, Set<ActivityDefinition> taskDefinitions) {
        if (taskDefinitions == null) {
            return Collections.emptySet();
        }

        return taskDefinitions.stream()
                .map(definition -> ActivityRef.of(CaseServiceType.EPROC, caseRef, definition.getId()))
                .map(activityRef -> eprocActivityService.getStateInstance(activityRef))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private static class RoleState {
        public Set<NodeRef> added = new HashSet<>();
        public Set<NodeRef> removed = new HashSet<>();

        RoleState(Set<NodeRef> added, Set<NodeRef> removed) {
            this.added = added;
            this.removed = removed;
        }

        static RoleState merge(RoleState state0, RoleState state1) {
            Set<NodeRef> added = new HashSet<>();
            Set<NodeRef> removed = new HashSet<>();
            if (state0 != null) {
                added.addAll(state0.added);
                removed.addAll(state0.removed);
            }
            if (state1 != null) {
                added.removeAll(state1.removed);
                removed.removeAll(state1.added);
                added.addAll(state1.added);
                removed.addAll(state1.removed);
            }
            return new RoleState(added, removed);
        }
    }
}
