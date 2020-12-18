package ru.citeck.ecos.notification.task.record;

import org.activiti.engine.delegate.DelegateTask;
import org.activiti.engine.impl.persistence.entity.ExecutionEntity;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.jscript.ScriptNode;
import org.alfresco.repo.workflow.WorkflowModel;
import org.alfresco.repo.workflow.activiti.ActivitiScriptNode;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.RegexQNamePattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.notification.task.record.services.EcosExecutionTaskService;
import ru.citeck.ecos.notification.task.record.services.EcosExecutionsTaskService;
import ru.citeck.ecos.records2.RecordRef;

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

@Service
public class DelegateExecutionTaskService implements EcosExecutionTaskService {

    @Autowired
    private NodeService nodeService;

    @Autowired
    DelegateExecutionTaskService(EcosExecutionsTaskService executionsTaskService){
        executionsTaskService.register(DelegateTask.class, this);
    }

    @Override
    public TaskExecutionRecord getExecutionTaskRecord(Object taskObj) {
        if (!(taskObj instanceof DelegateTask)) {
            return null;
        }

        DelegateTask task = (DelegateTask) taskObj;
        TaskExecutionRecord taskRecord = new TaskExecutionRecord();

        taskRecord.setTaskId("activiti$" + task.getId());
        taskRecord.setTaskName(task.getName());
        taskRecord.setTaskDescription(task.getDescription());
        taskRecord.setProperties(getProperties(task));
        taskRecord.setWorkflow(getWorkflow(task));

        return taskRecord;
    }

    private HashMap<String, Object> getProperties(DelegateTask task) {
        ExecutionEntity processInstance = ((ExecutionEntity) task.getExecution()).getProcessInstance();

        HashMap<String, Object> properties = new HashMap<>();
        processInstance.getVariables().forEach((key, value) -> {
            if (value != null) {
                if (value instanceof NodeRef) {
                    properties.put(key, RecordRef.valueOf(value.toString()));
                } else if (value instanceof ScriptNode) {
                    NodeRef node = ((ScriptNode) value).getNodeRef();
                    properties.put(key, RecordRef.valueOf(node.toString()));
                } else if (value instanceof Serializable) {
                    properties.put(key, value);
                } else {
                    properties.put(key, value.toString());
                }
            } else {
                properties.put(key, null);
            }
        });

        return properties;
    }

    private HashMap<String, Object> getWorkflow(DelegateTask task) {
        HashMap<String, Object> workflow = new HashMap<>();

        workflow.put("id", "activiti$" + task.getProcessInstanceId());
        workflow.put("documents", getWorkflowDocuments(task));

        return workflow;
    }

    private List<RecordRef> getWorkflowDocuments(DelegateTask task) {
        ExecutionEntity processInstance = ((ExecutionEntity) task.getExecution()).getProcessInstance();
        ActivitiScriptNode scriptNode = (ActivitiScriptNode) processInstance.getVariable("bpm_package");
        NodeRef workflowPackage = scriptNode != null ? scriptNode.getNodeRef() : null;

        List<RecordRef> documents = new LinkedList<>();

        if (workflowPackage != null && nodeService.exists(workflowPackage)) {
            addAssocContains(documents,
                nodeService.getChildAssocs(
                    workflowPackage, WorkflowModel.ASSOC_PACKAGE_CONTAINS, RegexQNamePattern.MATCH_ALL
                )
            );

            addAssocContains(documents,
                nodeService.getChildAssocs(
                    workflowPackage, ContentModel.ASSOC_CONTAINS, RegexQNamePattern.MATCH_ALL
                )
            );
        }

        return documents;
    }

    private void addAssocContains(List<RecordRef> docsInfo, List<ChildAssociationRef> children) {
        for (ChildAssociationRef child : children) {
            String childRef = child.getChildRef().toString();
            docsInfo.add(RecordRef.valueOf(childRef));
        }
    }
}
