package ru.citeck.ecos.notification.task.record;

import org.alfresco.repo.workflow.WorkflowQNameConverter;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.workflow.WorkflowInstance;
import org.alfresco.service.cmr.workflow.WorkflowTask;
import org.alfresco.service.namespace.QName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.notification.task.record.services.EcosExecutionTaskService;
import ru.citeck.ecos.notification.task.record.services.EcosExecutionsTaskService;
import ru.citeck.ecos.records2.RecordRef;

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Service
public class WorkflowExecutionTaskService implements EcosExecutionTaskService {

    @Autowired
    private NodeService nodeService;

    private final WorkflowQNameConverter qNameConverter;

    @Autowired
    WorkflowExecutionTaskService(EcosExecutionsTaskService executionsTaskService, ServiceRegistry serviceRegistry){
        executionsTaskService.register(WorkflowTask.class, this);
        this.qNameConverter = new WorkflowQNameConverter(serviceRegistry.getNamespaceService());
    }

    @Override
    public TaskExecutionRecord getExecutionTaskRecord(Object taskObj) {
        if (!(taskObj instanceof WorkflowTask)) {
            return null;
        }

        WorkflowTask task = (WorkflowTask) taskObj;
        TaskExecutionRecord taskRecord = new TaskExecutionRecord();

        taskRecord.setTaskId(task.getId());
        taskRecord.setTaskName(task.getName());
        taskRecord.setTaskTitle(task.getTitle());
        taskRecord.setTaskDescription(task.getDescription());
        taskRecord.setProperties(getProperties(task));
        taskRecord.setWorkflow(getWorkflow(task));

        return taskRecord;
    }

    private HashMap<String, Object> getProperties(WorkflowTask task) {
        HashMap<String, Object> properties = new HashMap<>();
        for (Map.Entry<QName, Serializable> entry : task.getProperties().entrySet()) {
            properties.put(qNameConverter.mapQNameToName(entry.getKey()), entry.getValue());
        }

        return properties;
    }

    private HashMap<String, Object> getWorkflow(WorkflowTask task) {
        HashMap<String, Object> workflow = new HashMap<>();

        WorkflowInstance instance = task.getPath().getInstance();
        workflow.put("id", instance.getId());
        workflow.put("documents", getWorkflowDocuments(instance));

        return workflow;
    }

    private List<RecordRef> getWorkflowDocuments(WorkflowInstance workflow) {

        List<RecordRef> documents = new LinkedList<>();

        NodeRef wfPackage = workflow.getWorkflowPackage();
        if (wfPackage == null) {
            return documents;
        }

        if (nodeService.exists(wfPackage)) {
            List<ChildAssociationRef> children = nodeService.getChildAssocs(wfPackage);
            for (ChildAssociationRef child : children) {
                documents.add(RecordRef.valueOf(child.getChildRef().toString()));
            }
        }

        return documents;
    }
}
