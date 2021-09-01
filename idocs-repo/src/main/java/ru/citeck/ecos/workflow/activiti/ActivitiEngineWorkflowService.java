package ru.citeck.ecos.workflow.activiti;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.repo.workflow.WorkflowModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.workflow.WorkflowInstance;
import org.alfresco.service.cmr.workflow.WorkflowService;
import org.alfresco.service.namespace.RegexQNamePattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.workflow.EcosWorkflowService;
import ru.citeck.ecos.workflow.EngineWorkflowService;

import java.util.List;

@Slf4j
@Component
public class ActivitiEngineWorkflowService implements EngineWorkflowService {

    private static final String ENGINE_PREFIX = "activiti$";

    private final WorkflowService workflowService;
    private final NodeService nodeService;

    @Autowired
    public ActivitiEngineWorkflowService(EcosWorkflowService ecosWorkflowService,
                                         @Qualifier("WorkflowService") WorkflowService workflowService,
                                         NodeService nodeService) {
        ecosWorkflowService.register("activiti", this);
        this.workflowService = workflowService;
        this.nodeService = nodeService;
    }

    public void sendSignal(List<String> processes, String signalName) {
        log.error("Signal sending is not implemented. Skip it. Processes: " + processes + " signal: " + signalName);
    }

    @Override
    public String getRootProcessInstanceId(String processId) {

        //TODO The old code, used for activiti. May be it works, may be not. Need to refactor
        String fullProcessId = ENGINE_PREFIX + processId;
        WorkflowInstance instanceById = workflowService.getWorkflowById(fullProcessId);
        if (instanceById == null) {
            return fullProcessId;
        }
        NodeRef instanceRefByTaskName = instanceById.getWorkflowPackage();
        if (instanceRefByTaskName == null) {
            return fullProcessId;
        }
        List<ChildAssociationRef> childrenTaskAssocRefs = nodeService.getChildAssocs(instanceRefByTaskName);
        if (childrenTaskAssocRefs.isEmpty()) {
            return fullProcessId;
        }
        NodeRef childrenTaskAssocRef = childrenTaskAssocRefs.get(0).getChildRef();
        List<ChildAssociationRef> parentAssocRefs = nodeService
            .getParentAssocs(childrenTaskAssocRef, WorkflowModel.ASSOC_PACKAGE_CONTAINS,
                RegexQNamePattern.MATCH_ALL);
        for (ChildAssociationRef parentAssocRef : parentAssocRefs) {
            NodeRef rootWorkflowPackage = parentAssocRef.getParentRef();
            String rootWorkflowId = (String) nodeService.getProperty(
                rootWorkflowPackage, WorkflowModel.PROP_WORKFLOW_INSTANCE_ID);
            if (rootWorkflowId != null) {
                return rootWorkflowId;
            }
        }
        return fullProcessId;
        ////////////////////////////////////////////////////////
    }
}
