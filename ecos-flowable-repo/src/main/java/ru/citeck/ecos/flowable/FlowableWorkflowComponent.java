package ru.citeck.ecos.flowable;

import org.alfresco.repo.transaction.AlfrescoTransactionSupport;
import org.alfresco.repo.transaction.TransactionalResourceHelper;
import org.alfresco.repo.workflow.BPMEngineRegistry;
import org.alfresco.repo.workflow.WorkflowComponent;
import org.alfresco.repo.workflow.activiti.ActivitiConstants;
import org.alfresco.service.cmr.repository.AssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AuthenticationService;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.cmr.workflow.*;
import org.alfresco.service.namespace.QName;
import org.apache.commons.collections.CollectionUtils;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.common.engine.api.FlowableIllegalArgumentException;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.DeploymentBuilder;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceBuilder;
import org.flowable.image.ProcessDiagramGenerator;
import org.flowable.image.impl.DefaultProcessDiagramGenerator;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import ru.citeck.ecos.flowable.services.*;
import ru.citeck.ecos.flowable.utils.FlowableWorkflowPropertyHandlerRegistry;
import ru.citeck.ecos.model.BpmPackageModel;

import java.io.InputStream;
import java.io.Serializable;
import java.util.*;

import static ru.citeck.ecos.flowable.constants.FlowableConstants.*;

/**
 * Flowable workflow component
 */
public class FlowableWorkflowComponent implements WorkflowComponent, InitializingBean {

    private static final QName INITIATOR_QNAME = QName.createQName("initiator");
    private static final QName INITIATOR_USERNAME_QNAME = QName.createQName("initiator_username");
    private static final String KEY_PENDING_DELETE_NODES = "DbNodeServiceImpl.pendingDeleteNodes";

    private BPMEngineRegistry bpmEngineRegistry;
    private WorkflowAdminService workflowAdminService;
    private AuthenticationService authenticationService;
    private PersonService personService;
    private NodeService nodeService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private FlowableProcessDefinitionService flowableProcessDefinitionService;

    @Autowired
    private FlowableTransformService flowableTransformService;

    @Autowired
    @Qualifier("flowableWorkflowPropertyHandlerRegistry")
    private FlowableWorkflowPropertyHandlerRegistry workflowPropertyHandlerRegistry;

    @Autowired
    private FlowableTaskService flowableTaskService;

    @Autowired
    private FlowableProcessInstanceService flowableProcessInstanceService;

    @Autowired
    private FlowableHistoryService flowableHistoryService;

    /**
     * After properties set
     */
    @Override
    public void afterPropertiesSet() {
        if (repositoryService == null || runtimeService == null || historyService == null) {
            return;
        }
        bpmEngineRegistry.registerWorkflowComponent(ENGINE_ID, this);
        workflowAdminService.setEngineEnabled(ENGINE_ID, true);
        workflowAdminService.setEngineVisibility(ENGINE_ID, true);
    }

    /**
     * Deploy a Workflow Definition
     *
     * @param workflowDefinition the content object containing the definition
     * @param mimetype           (optional)  the mime type of the workflow definition
     * @return workflow deployment descriptor
     */
    @Override
    public WorkflowDeployment deployDefinition(InputStream workflowDefinition, String mimetype) {
        return deployDefinition(workflowDefinition, mimetype, "flowable-deploy-definition");
    }

    /**
     * Deploy a Workflow Definition
     *
     * @param workflowDefinition the content object containing the definition
     * @param mimetype           (optional)  the mime type of the workflow definition
     * @param name               (optional)  a name to represent the deployment
     * @return workflow deployment descriptor
     * @since 4.0
     */
    @Override
    public WorkflowDeployment deployDefinition(InputStream workflowDefinition, String mimetype, String name) {
        if (repositoryService == null) {
            return null;
        }
        if (name == null) {
            name = "flowable-deploy-definition.bpmn20.xml";
        }
        DeploymentBuilder deploymentBuilder = this.repositoryService.createDeployment();
        deploymentBuilder.addInputStream(name, workflowDefinition);
        deploymentBuilder.name(name);
        Deployment deployment = deploymentBuilder.deploy();
        ProcessDefinition processDefinition = flowableProcessDefinitionService.getProcessDefinitionByDeploymentId(
                deployment.getId());
        return new WorkflowDeployment(
                flowableTransformService.transformProcessDefinition(processDefinition)
        );
    }

    /**
     * Is the specified Workflow Definition already deployed?
     * <p>
     * Note: the notion of "already deployed" may differ between bpm engines. For example,
     * different versions of the same process may be considered equal.
     *
     * @param workflowDefinition the definition to check
     * @param mimetype           the mimetype of the definition
     * @return true => already deployed
     */
    @Override
    public boolean isDefinitionDeployed(InputStream workflowDefinition, String mimetype) {
        return flowableProcessDefinitionService.isProcessDefinitionDeployed(workflowDefinition);
    }

    /**
     * Undeploy an exisiting Workflow Definition
     *
     * @param workflowDefinitionId the id of the definition to undeploy
     */
    @Override
    public void undeployDefinition(String workflowDefinitionId) {
        ProcessDefinition processDefinition = flowableProcessDefinitionService.getProcessDefinitionById(
                getLocalValue(workflowDefinitionId));
        if (processDefinition != null) {
            repositoryService.deleteDeployment(processDefinition.getDeploymentId());
        }
    }

    /**
     * Gets all deployed Workflow Definitions
     *
     * @return the deployed workflow definitions
     */
    @Override
    public List<WorkflowDefinition> getDefinitions() {
        List<ProcessDefinition> processDefinitions = flowableProcessDefinitionService.getAllLastProcessDefinitions();
        return flowableTransformService.transformProcessDefinitions(processDefinitions);
    }

    /**
     * Gets all deployed Workflow Definitions (with all previous versions)
     *
     * @return the deployed (and previous) workflow definitions
     */
    @Override
    public List<WorkflowDefinition> getAllDefinitions() {
        List<ProcessDefinition> processDefinitions = flowableProcessDefinitionService.getAllProcessDefinitions();
        return flowableTransformService.transformProcessDefinitions(processDefinitions);
    }

    /**
     * Gets a Workflow Definition by unique Id
     *
     * @param workflowDefinitionId the workflow definition id
     * @return the deployed workflow definition
     */
    @Override
    public WorkflowDefinition getDefinitionById(String workflowDefinitionId) {
        return flowableTransformService.transformProcessDefinition(
                flowableProcessDefinitionService.getProcessDefinitionById(getLocalValue(workflowDefinitionId))
        );
    }

    /**
     * Gets a Workflow Definition by unique name
     *
     * @param workflowName workflow name e.g. jbpm$wf:review
     * @return the deployed workflow definition
     */
    @Override
    public WorkflowDefinition getDefinitionByName(String workflowName) {
        return flowableTransformService.transformProcessDefinition(
                flowableProcessDefinitionService.getProcessDefinitionByKey(getLocalValue(workflowName))
        );
    }

    /**
     * Gets all (including previous) Workflow Definitions for the given unique name
     *
     * @param workflowName workflow name e.g. jbpm$wf:review
     * @return the deployed workflow definition (or null if not found)
     */
    @Override
    public List<WorkflowDefinition> getAllDefinitionsByName(String workflowName) {
        List<ProcessDefinition> processDefinitions = flowableProcessDefinitionService.getAllProcessDefinitionsByKey(
                getLocalValue(workflowName));
        return flowableTransformService.transformProcessDefinitions(processDefinitions);
    }

    /**
     * Gets a graphical view of the Workflow Definition
     *
     * @param workflowDefinitionId the workflow definition id
     * @return graphical image of workflow definition
     */
    @Override
    public byte[] getDefinitionImage(String workflowDefinitionId) {
        return new byte[0];
    }

    /**
     * Gets the Task Definitions for the given Workflow Definition
     *
     * @param workflowDefinitionId the workflow definition id
     * @return the deployed task definitions (or null if not found)
     */
    @Override
    public List<WorkflowTaskDefinition> getTaskDefinitions(String workflowDefinitionId) {
        List<Task> tasks = flowableTaskService.getTasksByProcessDefinitionId(getLocalValue(workflowDefinitionId));
        return flowableTransformService.transformTaskDefinitions(tasks);
    }

    /**
     * Start a Workflow Instance
     *
     * @param workflowDefinitionId the workflow definition id
     * @param parameters           the initial set of parameters used to populate the "Start Task" properties
     * @return the initial workflow path
     */
    @Override
    public WorkflowPath startWorkflow(String workflowDefinitionId, Map<QName, Serializable> parameters) {
        if (workflowDefinitionId == null) {
            throw new FlowableIllegalArgumentException("Workflow definition id is required.");
        }
        WorkflowDefinition workflowDefinition = getDefinitionById(workflowDefinitionId);
        /* Load current user and set as initiator */
        if (!parameters.containsKey(INITIATOR_QNAME)) {
            String currentUsername = authenticationService.getCurrentUserName();
            if (currentUsername != null) {
                NodeRef userNode = personService.getPerson(currentUsername);
                parameters.put(INITIATOR_QNAME, userNode);
                parameters.put(INITIATOR_USERNAME_QNAME, currentUsername);
            }
        }
        /* Build process */
        String processDefinitionId = getLocalValue(workflowDefinitionId);
        ProcessInstanceBuilder processInstanceBuilder = runtimeService.createProcessInstanceBuilder();
        processInstanceBuilder.processDefinitionId(processDefinitionId);

        if (parameters != null) {
            Map<String, Object> transformedParameters = workflowPropertyHandlerRegistry.handleVariablesToSet(parameters,
                    workflowDefinition.getStartTaskDefinition().getMetadata(), null, Void.class);
            processInstanceBuilder.variables(transformedParameters);
        }

        ProcessInstance processInstance = processInstanceBuilder.start();

        // In case the process ends immediately (e.g. it doesn't have user tasks)
        if (processInstance.isEnded()) {
            String processId = processInstance.getId();
            HistoricProcessInstance historicProcessInstance = flowableHistoryService.getProcessInstanceById(processId);
            return flowableTransformService.transformHistoryProcessInstanceToWorkflowPath(historicProcessInstance);
        }

        return flowableTransformService.transformProcessInstanceToWorkflowPath(processInstance);
    }

    /**
     * Gets all "in-flight" active workflow instances of the specified Workflow Definition
     *
     * @param workflowDefinitionId the workflow definition id
     * @return the list of "in-flight" workflow instances
     */
    @Override
    public List<WorkflowInstance> getActiveWorkflows(String workflowDefinitionId) {
        return null;
    }

    /**
     * Gets all "in-flight" completed workflow instances of the specified Workflow Definition
     *
     * @param workflowDefinitionId the workflow definition id
     * @return the list of "in-flight" workflow instances
     */
    @Override
    public List<WorkflowInstance> getCompletedWorkflows(String workflowDefinitionId) {
        return null;
    }

    /**
     * Gets all "in-flight" workflow instances (both active and completed) of the specified Workflow Definition
     *
     * @param workflowDefinitionId the workflow definition id
     * @return the list of "in-flight" workflow instances
     */
    @Override
    public List<WorkflowInstance> getWorkflows(String workflowDefinitionId) {
        return null;
    }

    /**
     * Gets all "in-flight" workflow instances according to the specified workflowInstanceQuery parameter
     *
     * @param workflowInstanceQuery WorkflowInstanceQuery
     * @return the list of "in-flight" workflow instances
     */
    @Override
    public List<WorkflowInstance> getWorkflows(WorkflowInstanceQuery workflowInstanceQuery) {
        List<HistoricProcessInstance> processInstances = flowableHistoryService.getProcessInstancesByQuery(
                workflowInstanceQuery);
        return flowableTransformService.transformHistoryProcessInstancesToWorkflowInstances(processInstances);
    }

    /**
     * Gets maxItems "in-flight" workflow instances according to the specified workflowInstanceQuery parameter
     *
     * @param workflowInstanceQuery WorkflowInstanceQuery
     * @param maxItems              int
     * @param skipCount             int
     * @return maxItems workflow instances
     */
    @Override
    public List<WorkflowInstance> getWorkflows(WorkflowInstanceQuery workflowInstanceQuery, int maxItems, int skipCount) {
        List<HistoricProcessInstance> processInstances = flowableHistoryService.getProcessInstancesByQuery(
                workflowInstanceQuery, maxItems, skipCount);
        return flowableTransformService.transformHistoryProcessInstancesToWorkflowInstances(processInstances);
    }

    /**
     * Get count of workflow instances
     *
     * @param workflowInstanceQuery WorkflowInstanceQuery
     * @return count of workflow instances
     */
    @Override
    public long countWorkflows(WorkflowInstanceQuery workflowInstanceQuery) {
        return flowableHistoryService.getProcessInstancesCountByQuery(workflowInstanceQuery);
    }

    /**
     * Gets all "in-flight" active workflow instances.
     *
     * @return the list of "in-flight" workflow instances
     * @since 4.0
     */
    @Override
    public List<WorkflowInstance> getActiveWorkflows() {
        List<HistoricProcessInstance> processInstances = flowableHistoryService.getAllActiveProcessInstances();
        return flowableTransformService.transformHistoryProcessInstancesToWorkflowInstances(processInstances);
    }

    /**
     * Gets all completed workflow instances.
     *
     * @return the list of "in-flight" workflow instances
     * @since 4.0
     */
    @Override
    public List<WorkflowInstance> getCompletedWorkflows() {
        List<HistoricProcessInstance> processInstances = flowableHistoryService.getAllCompletedProcessInstances();
        return flowableTransformService.transformHistoryProcessInstancesToWorkflowInstances(processInstances);
    }

    /**
     * Gets all workflow instances (both active and completed).
     *
     * @return the list of "in-flight" workflow instances
     * @since 4.0
     */
    @Override
    public List<WorkflowInstance> getWorkflows() {
        List<HistoricProcessInstance> processInstances = flowableHistoryService.getAllProcessInstances();
        return flowableTransformService.transformHistoryProcessInstancesToWorkflowInstances(processInstances);
    }

    /**
     * Gets a specific workflow instances
     *
     * @param workflowId the id of the workflow to retrieve
     * @return the workflow instance
     */
    @Override
    public WorkflowInstance getWorkflowById(String workflowId) {
        ProcessInstance processInstance = flowableProcessInstanceService.getProcessInstanceById(getLocalValue(workflowId));
        if (processInstance != null) {
            return flowableTransformService.transformProcessInstanceToWorkflowInstance(processInstance);
        } else {
            HistoricProcessInstance historicProcessInstance = flowableHistoryService.getProcessInstanceById(
                    getLocalValue(workflowId));
            return flowableTransformService.transformHistoryProcessInstanceToWorkflowInstance(historicProcessInstance);
        }

    }

    /**
     * Gets all Paths for the specified Workflow instance
     *
     * @param workflowId workflow instance id
     * @return the list of workflow paths
     */
    @Override
    public List<WorkflowPath> getWorkflowPaths(String workflowId) {
        return null;
    }

    /**
     * Gets the properties associated with the specified path (and parent paths)
     *
     * @param pathId workflow path id
     * @return map of path properties
     */
    @Override
    public Map<QName, Serializable> getPathProperties(String pathId) {
        return null;
    }

    /**
     * Cancel an "in-flight" Workflow instance
     *
     * @param workflowId the workflow instance to cancel
     * @return an updated representation of the workflow instance
     */
    @Override
    public WorkflowInstance cancelWorkflow(String workflowId) {
        return deleteProcessInstanceWithReason(workflowId, DELETE_REASON_CANCELLED);
    }

    /**
     * Cancel a batch of "in-flight" Workflow instances
     *
     * @param workflowIds List of the workflow instances to cancel
     * @return List of updated representations of the workflow instances
     */
    @Override
    public List<WorkflowInstance> cancelWorkflows(List<String> workflowIds) {
        List<WorkflowInstance> canceledProcessInstances = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(workflowIds)) {
            for (String processInstanceId : workflowIds) {
                WorkflowInstance workflowInstance = cancelWorkflow(processInstanceId);
                if (workflowInstance != null) {
                    canceledProcessInstances.add(workflowInstance);
                }
            }
        }
        return canceledProcessInstances;
    }

    /**
     * Delete an "in-flight" Workflow instance
     *
     * @param workflowId the workflow instance to cancel
     * @return an updated representation of the workflow instance
     */
    @Override
    public WorkflowInstance deleteWorkflow(String workflowId) {
        return deleteProcessInstanceWithReason(workflowId, DELETE_REASON_DELETED);
    }

    /**
     * Delete process instance with reason
     *
     * @param workflowId Workflow id
     * @param reason     Delete reason
     * @return Deleted workflow instance
     */
    private WorkflowInstance deleteProcessInstanceWithReason(String workflowId, String reason) {
        ProcessInstance processInstance = flowableProcessInstanceService.getProcessInstanceById(getLocalValue(workflowId));
        if (processInstance != null) {
            NodeRef bpmPackage = (NodeRef) runtimeService.getVariable(processInstance.getId(), "bpm_package");
            runtimeService.deleteProcessInstance(processInstance.getId(), reason);
            deleteMirrorTasks(bpmPackage);
            HistoricProcessInstance historicProcessInstance = flowableHistoryService.getProcessInstanceById(
                    processInstance.getId());
            if (historicProcessInstance != null) {
                historyService.deleteHistoricProcessInstance(historicProcessInstance.getId());
                return flowableTransformService.transformHistoryProcessInstanceToWorkflowInstance(
                        historicProcessInstance);
            } else {
                return flowableTransformService.transformProcessInstanceToWorkflowInstance(
                        processInstance);
            }
        } else {
            HistoricProcessInstance historicProcessInstance = flowableHistoryService.getProcessInstanceById(
                    getLocalValue(workflowId));
            if (historicProcessInstance != null) {
                historyService.deleteHistoricProcessInstance(historicProcessInstance.getId());
                return flowableTransformService.transformHistoryProcessInstanceToWorkflowInstance(historicProcessInstance);
            } else {
                return null;
            }
        }
    }

    /**
     * Delete mirror tasks
     *
     * @param bpmPackage Bpm package
     */
    private void deleteMirrorTasks(NodeRef bpmPackage) {
        if (bpmPackage == null || isNodeForDelete(bpmPackage)) {
            return;
        }
        List<AssociationRef> assocs = nodeService.getSourceAssocs(bpmPackage, BpmPackageModel.TYPE);
        assocs.forEach(association -> {
            if (association.getSourceRef() != null && !isNodeForDelete(association.getSourceRef())) {
                nodeService.deleteNode(association.getSourceRef());
            }
        });
    }

    /**
     * Check - is node for delete
     *
     * @param nodeRef Node reference
     * @return Check result
     */
    private boolean isNodeForDelete(NodeRef nodeRef) {
        if (AlfrescoTransactionSupport.getTransactionReadState() != AlfrescoTransactionSupport.TxnReadState.TXN_READ_WRITE) {
            return false;
        } else {
            Set<NodeRef> nodesPendingDelete = TransactionalResourceHelper.getSet(KEY_PENDING_DELETE_NODES);
            return nodesPendingDelete.contains(nodeRef);
        }
    }

    /**
     * Signal the transition from one Workflow Node to another within an "in-flight"
     * process.
     *
     * @param pathId       the workflow path to signal on
     * @param transitionId the transition id to follow (or null, for the default transition)
     * @return the updated workflow path
     */
    @Override
    public WorkflowPath signal(String pathId, String transitionId) {
        return null;
    }

    /**
     * Fire custom event against specified path
     *
     * @param pathId the workflow path to fire event on
     * @param event  name of event
     * @return workflow path (it may have been updated as a result of firing the event
     */
    @Override
    public WorkflowPath fireEvent(String pathId, String event) {
        return null;
    }

    /**
     * Gets all Tasks associated with the specified path
     *
     * @param pathId the path id
     * @return the list of associated tasks
     */
    @Override
    public List<WorkflowTask> getTasksForWorkflowPath(String pathId) {
        /* Check is start task active */
        if (flowableProcessInstanceService.isStartTaskActive(getLocalValue(pathId))) {
            ProcessInstance processInstance = flowableProcessInstanceService.getProcessInstanceById(
                    getLocalValue(pathId));
            ProcessDefinition processDefinition = flowableProcessDefinitionService.getProcessDefinitionById(
                    processInstance.getProcessDefinitionId());
            return Collections.singletonList(flowableTransformService.transformStartTask(processDefinition,
                    processInstance));
        } else {
            List<Task> tasks = flowableTaskService.getTasksByProcessInstanceId(getLocalValue(pathId));
            return flowableTransformService.transformTasks(tasks);
        }
    }

    /**
     * Gets all active timers for the specified workflow
     *
     * @param workflowId
     * @return the list of active timers
     */
    @Override
    public List<WorkflowTimer> getTimers(String workflowId) {
        return null;
    }

    /**
     * Determines if a graphical view of the workflow instance exists
     *
     * @param workflowInstanceId the workflow instance id
     * @return true if there is a workflow instance diagram available
     * @since 4.0
     */
    @Override
    public boolean hasWorkflowImage(String workflowInstanceId) {
        ProcessDefinition processDefinition = flowableProcessDefinitionService
                .getProcessDefinitionByProcessInstanceId(getLocalValue(workflowInstanceId));
        if (processDefinition != null) {
            return processDefinition.hasGraphicalNotation();
        } else {
            return false;
        }
    }

    /**
     * Gets a graphical view of the workflow instance
     *
     * @param workflowInstanceId the workflow instance id
     * @return image view of the workflow instance as an InputStream or null if a diagram is not available
     * @since 4.0
     */
    @Override
    public InputStream getWorkflowImage(String workflowInstanceId) {

        String localInstanceId = getLocalValue(workflowInstanceId);

        ExecutionEntity pi = (ExecutionEntity) runtimeService.createProcessInstanceQuery()
            .processInstanceId(localInstanceId).singleResult();

        // If the process is finished, there is no diagram available
        if (pi != null) {
            // Fetch the bpmn model
            BpmnModel model = repositoryService.getBpmnModel(pi.getProcessDefinitionId());

            if (model != null && model.getLocationMap().size() > 0) {

                ProcessDiagramGenerator generator = new DefaultProcessDiagramGenerator();
                return generator.generateDiagram(model,
                    ActivitiConstants.PROCESS_INSTANCE_IMAGE_FORMAT,
                    runtimeService.getActiveActivityIds(localInstanceId), true);
            }
        }
        return null;
    }

    /**
     * Get local value
     *
     * @param rawValue Raw value
     * @return Local value
     */
    private String getLocalValue(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        return rawValue.startsWith(ENGINE_PREFIX)
                ? rawValue.substring(ENGINE_PREFIX.length()) : rawValue;
    }

    public void setBpmEngineRegistry(BPMEngineRegistry bpmEngineRegistry) {
        this.bpmEngineRegistry = bpmEngineRegistry;
    }

    public void setWorkflowAdminService(WorkflowAdminService workflowAdminService) {
        this.workflowAdminService = workflowAdminService;
    }

    public void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    public void setPersonService(PersonService personService) {
        this.personService = personService;
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }
}
