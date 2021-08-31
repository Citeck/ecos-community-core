package ru.citeck.ecos.workflow.activiti;

import org.alfresco.repo.workflow.WorkflowModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.workflow.WorkflowInstance;
import org.alfresco.service.cmr.workflow.WorkflowService;
import org.alfresco.service.namespace.RegexQNamePattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.workflow.EcosWorkflowService;
import ru.citeck.ecos.workflow.EngineWorkflowService;

import java.util.List;

@Component
public class ActivitiEngineWorkflowService implements EngineWorkflowService {

    public static final Logger logger = LoggerFactory.getLogger(ActivitiEngineWorkflowService.class);

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
        logger.error("Signal sending is not implemented. Skip it. Processes: " + processes + " signal: " + signalName);
    }

    @Override
    public String getRootProcessInstanceId(String processId) {

        //TODO The old code, used for activiti. May be it works, may be not. Need to refactor
        WorkflowInstance instanceById = workflowService.getWorkflowById(processId);
        if (instanceById == null) {
            return processId;
        }
        NodeRef instanceRefByTaskName = instanceById.getWorkflowPackage();
        if (instanceRefByTaskName == null) {
            return processId;
        }
        List<ChildAssociationRef> childrenTaskAssocRefs = nodeService.getChildAssocs(instanceRefByTaskName);
        if (childrenTaskAssocRefs.isEmpty()) {
            return processId;
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
        return processId;
        ////////////////////////////////////////////////////////
    }
}
