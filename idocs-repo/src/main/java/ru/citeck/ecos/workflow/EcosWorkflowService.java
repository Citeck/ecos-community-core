package ru.citeck.ecos.workflow;

import lombok.Getter;
import lombok.NonNull;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.workflow.WorkflowModel;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.workflow.*;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.model.CiteckWorkflowModel;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class EcosWorkflowService {

    private Map<String, EngineWorkflowService> serviceByEngine = new ConcurrentHashMap<>();

    private WorkflowService workflowService;
    private DictionaryService dictionaryService;
    private NamespaceService namespaceService;
    private NodeService nodeService;

    @Autowired
    public EcosWorkflowService(@Qualifier("WorkflowService") WorkflowService workflowService,
                               DictionaryService dictionaryService,
                               NamespaceService namespaceService,
                               NodeService nodeService) {
        this.workflowService = workflowService;
        this.dictionaryService = dictionaryService;
        this.namespaceService = namespaceService;
        this.nodeService = nodeService;
    }

    public void sendSignal(NodeRef nodeRef, String signalName) {

        List<WorkflowInstance> workflows = workflowService.getWorkflowsForContent(nodeRef, true);

        if (workflows.isEmpty()) {
            throw new IllegalStateException("Active workflows is not found");
        }

        sendSignal(workflows.stream().map(WorkflowInstance::getId).collect(Collectors.toList()), signalName);
    }

    public void sendSignal(String processId, String signalName) {
        sendSignal(Collections.singletonList(processId), signalName);
    }

    public void sendSignal(Collection<String> processes, String signalName) {
        groupByEngineId(processes).forEach((engine, engineProcesses) -> {
            EngineWorkflowService engineService = needWorkflowService(engine);
            engineService.sendSignal(engineProcesses, signalName);
        });
    }

    private Map<String, List<String>> groupByEngineId(Collection<String> workflows) {

        Map<String, List<String>> result = new HashMap<>();

        for (String workflowId : workflows) {
            WorkflowId id = new WorkflowId(workflowId);
            result.computeIfAbsent(id.engineId, engineId -> new ArrayList<>()).add(id.localId);
        }

        return result;
    }

    private EngineWorkflowService needWorkflowService(String engineId) {
        EngineWorkflowService workflowService = serviceByEngine.get(engineId);
        if (workflowService == null) {
            throw new IllegalArgumentException("Workflow service for engine '" + engineId + "' is not registered");
        }
        return workflowService;
    }

    /**
     * Getting instance of workflow
     */
    public WorkflowInstance getInstanceById(@NonNull String workflowId) {
        return workflowService.getWorkflowById(workflowId);
    }

    public List<WorkflowInstance> getAllInstances(WorkflowInstanceQuery query, int max, int skipCount) {
        return workflowService.getWorkflows(query, max, skipCount);
    }

    public WorkflowInstance cancelWorkflowInstance(String workflowId) {
        return workflowService.cancelWorkflow(workflowId);
    }

    public WorkflowDefinition getDefinitionByName(String workflowName) {
        return workflowService.getDefinitionByName(workflowName);
    }

    @Nullable
    public String startFormWorkflow(String definitionId, Map<String, Object> attributes) {
        Map<QName, Serializable> workflowAttributes = new HashMap<>();

        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            String stringName = entry.getKey();
            if (stringName.contains("_")) {
                stringName = stringName.replaceFirst("_", ":");
            }
            QName resolvedQname = QName.resolveToQName(namespaceService, stringName);
            if (entry.getValue() instanceof Serializable) {
                if (dictionaryService.getAssociation(resolvedQname) != null) {
                    Serializable convertedValue = convertToNode(entry.getValue());
                    if (isNotEmptyValue(convertedValue)) {
                        workflowAttributes.put(resolvedQname, convertedValue);
                    }
                } else {
                    workflowAttributes.put(resolvedQname, (Serializable) entry.getValue());
                }
            }
        }
        NodeRef wfPackage = workflowService.createPackage(null);

        List<NodeRef> itemsRefs = new ArrayList<>();
        Serializable items = workflowAttributes.get(CiteckWorkflowModel.ASSOC_TARGET_ITEMS);

        if (items != null) {
            if (items instanceof NodeRef) {
                itemsRefs.add((NodeRef) items);
            } else if (items instanceof Collection) {
                for (Object item : (Collection) items) {
                    if (item instanceof NodeRef) {
                        itemsRefs.add((NodeRef) item);
                    }
                }
            }
        }
        for (NodeRef docRef : itemsRefs) {

            String docName = (String) nodeService.getProperty(docRef, ContentModel.PROP_NAME);
            docName = QName.createValidLocalName(docName);
            QName docQName = QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, docName);

            nodeService.addChild(wfPackage, docRef, WorkflowModel.ASSOC_PACKAGE_CONTAINS, docQName);
        }

        workflowAttributes.put(WorkflowModel.ASSOC_PACKAGE, wfPackage);
        WorkflowPath path = workflowService.startWorkflow(definitionId, workflowAttributes);

        if (path != null && path.getInstance() != null) {
            return path.getInstance().getId();
        }
        return null;
    }

    private Serializable convertToNode(Object value) {
        if (value instanceof String && NodeRef.isNodeRef((String) value)) {
            return new NodeRef((String) value);
        } else if (value instanceof ArrayList) {
            ArrayList<NodeRef> refList = new ArrayList<>();
            for (Object listObject : (ArrayList) value) {
                if (listObject instanceof String && NodeRef.isNodeRef((String) listObject)) {
                    refList.add(new NodeRef((String) listObject));
                } else if (listObject instanceof NodeRef) {
                    refList.add((NodeRef) listObject);
                }
            }
            return refList;
        } else {
            return (Serializable) value;
        }
    }

    private boolean isNotEmptyValue(Object value) {
        if (value instanceof NodeRef) {
            return true;
        } else if (value instanceof ArrayList) {
            return ((ArrayList) value).size() > 0;
        }
        return false;
    }

    private static class WorkflowId {

        @Getter
        private final String engineId;
        @Getter
        private final String localId;

        WorkflowId(String workflowId) {
            int delimIdx = workflowId.indexOf('$');
            if (delimIdx == -1) {
                throw new IllegalArgumentException("Workflow id should has engine " +
                    "prefix. Workflow: '" + workflowId + "'");
            }
            this.engineId = workflowId.substring(0, delimIdx);
            this.localId = workflowId.substring(delimIdx + 1);
        }
    }

    public void register(String engine, EngineWorkflowService taskService) {
        serviceByEngine.put(engine, taskService);
    }
}
