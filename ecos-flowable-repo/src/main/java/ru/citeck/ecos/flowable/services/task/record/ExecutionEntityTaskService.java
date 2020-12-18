package ru.citeck.ecos.flowable.services.task.record;

import org.alfresco.repo.jscript.ScriptNode;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import org.springframework.beans.factory.annotation.Autowired;
import ru.citeck.ecos.notification.task.record.TaskExecutionRecord;
import ru.citeck.ecos.notification.task.record.services.EcosExecutionTaskService;
import ru.citeck.ecos.notification.task.record.services.EcosExecutionsTaskService;
import ru.citeck.ecos.records2.RecordRef;

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class ExecutionEntityTaskService implements EcosExecutionTaskService {

    @Autowired
    private NodeService nodeService;

    @Autowired
    ExecutionEntityTaskService(EcosExecutionsTaskService executionsTaskService) {
        executionsTaskService.register(ExecutionEntity.class, this);
    }

    @Override
    public TaskExecutionRecord getExecutionTaskRecord(Object taskObj) {
        if (!(taskObj instanceof ExecutionEntity)) {
            return null;
        }

        ExecutionEntity task = (ExecutionEntity) taskObj;
        TaskExecutionRecord taskRecord = new TaskExecutionRecord();

        taskRecord.setTaskId(task.getId());
        taskRecord.setTaskName(task.getName());
        taskRecord.setTaskDescription(task.getDescription());
        taskRecord.setProperties(getProperties(task));
        taskRecord.setWorkflow(getWorkflow(task));

        return taskRecord;
    }

    private HashMap<String, Object> getProperties(ExecutionEntity task) {
        HashMap<String, Object> properties = new HashMap<>();

        for (Map.Entry<String, Object> entry : task.getVariables().entrySet()) {
            Object value = entry.getValue();
            String key = entry.getKey();
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
                properties.put(key, value.toString());
            } else {
                properties.put(key, null);
            }
        }

        return properties;
    }

    private HashMap<String, Object> getWorkflow(ExecutionEntity task) {
        HashMap<String, Object> workflow = new HashMap<>();

        workflow.put("documents", getWorkflowDocuments(task));

        return workflow;
    }

    private List<RecordRef> getWorkflowDocuments(ExecutionEntity task) {
        List<RecordRef> documents = new LinkedList<>();

        NodeRef workflowPackage = (NodeRef) task.getVariable("bpm_package");
        if (workflowPackage == null || !nodeService.exists(workflowPackage)) {
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
}
