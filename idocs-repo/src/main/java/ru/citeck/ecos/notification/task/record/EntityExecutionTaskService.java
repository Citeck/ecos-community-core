package ru.citeck.ecos.notification.task.record;

import org.activiti.engine.impl.persistence.entity.ExecutionEntity;
import org.alfresco.repo.workflow.activiti.ActivitiScriptNode;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.notification.task.record.services.EcosExecutionTaskService;
import ru.citeck.ecos.notification.task.record.services.EcosExecutionsTaskService;
import ru.citeck.ecos.records2.RecordRef;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Service
public class EntityExecutionTaskService implements EcosExecutionTaskService {

    @Autowired
    private NodeService nodeService;

    @Autowired
    EntityExecutionTaskService(EcosExecutionsTaskService executionsTaskService){
        executionsTaskService.register(ExecutionEntity.class, this);
    }

    @Override
    public TaskExecutionRecord getExecutionTaskRecord(Object taskObj) {
        if (!(taskObj instanceof ExecutionEntity)) {
            return null;
        }

        ExecutionEntity task = (ExecutionEntity) taskObj;
        TaskExecutionRecord taskRecord = new TaskExecutionRecord();

        taskRecord.setWorkflow(getWorkflow(task));

        return taskRecord;
    }

    private HashMap<String, Object> getWorkflow(ExecutionEntity task) {
        HashMap<String, Object> workflow = new HashMap<>();

        workflow.put("id", task.getId());
        workflow.put("documents", getWorkflowDocuments(task));
        workflow.put("properties", getWorkflowProperties(task));

        return workflow;
    }

    private Map<String, Object> getWorkflowProperties(ExecutionEntity task) {
        Map<String, Object> properties = new HashMap<>();
        for (Map.Entry<String, Object> entry : task.getVariables().entrySet()) {
            if (entry.getValue() != null) {
                properties.put(entry.getKey(), entry.getValue().toString());
            } else {
                properties.put(entry.getKey(), null);
            }
        }
        return properties;
    }

    private List<RecordRef> getWorkflowDocuments(ExecutionEntity task) {
        List<RecordRef> documents = new LinkedList<>();

        ActivitiScriptNode scriptNode = (ActivitiScriptNode) task.getVariable("bpm_package");
        NodeRef workflowPackage = scriptNode != null ? scriptNode.getNodeRef() : null;
        if (workflowPackage == null) {
            return documents;
        }

        List<ChildAssociationRef> children = nodeService.getChildAssocs(workflowPackage);
        for (ChildAssociationRef child : children) {
            NodeRef node = child.getChildRef();
            if (node != null && nodeService.exists(node)) {
                documents.add(RecordRef.valueOf(node.toString()));
            }
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
