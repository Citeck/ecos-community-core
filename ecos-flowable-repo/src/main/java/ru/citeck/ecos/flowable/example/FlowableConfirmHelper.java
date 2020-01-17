package ru.citeck.ecos.flowable.example;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.workflow.WorkflowModel;
import org.alfresco.repo.workflow.WorkflowQNameConverter;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.cmr.version.Version;
import org.alfresco.service.cmr.version.VersionService;
import org.alfresco.service.cmr.workflow.WorkflowService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.namespace.RegexQNamePattern;
import org.flowable.engine.delegate.DelegateExecution;
import ru.citeck.ecos.flowable.utils.FlowableListenerUtils;
import ru.citeck.ecos.model.ConfirmWorkflowModel;
import ru.citeck.ecos.workflow.confirm.ConfirmDecision;

import java.io.Serializable;
import java.util.*;

/**
 * Confirm helper
 */
public class FlowableConfirmHelper {

    private NodeService nodeService;
    private VersionService versionService;
    private WorkflowService workflowService;
    private WorkflowQNameConverter qNameConverter;
    private PersonService personService;
    private AuthorityService authorityService;

    public NodeRef getCurrentVersionRef(NodeRef nodeRef, boolean createIfUnversioned) {
        Version currentVersion = versionService.getCurrentVersion(nodeRef);
        if(currentVersion == null) {
            if(!createIfUnversioned) {
                return null;
            }

            Map<QName, Serializable> properties = new HashMap<>(2);
            properties.put(ContentModel.PROP_AUTO_VERSION, true);
            properties.put(ContentModel.PROP_AUTO_VERSION_PROPS, false);
            versionService.ensureVersioningEnabled(nodeRef, properties);

            currentVersion = versionService.getCurrentVersion(nodeRef);
            if(currentVersion == null) {
                return null;
            }
        }

        return currentVersion.getFrozenStateNodeRef();
    }

    public NodeRef getCurrentVersionRef(NodeRef nodeRef) {
        return getCurrentVersionRef(nodeRef, false);
    }

    public Set<NodeRef> getCurrentVersionRefs(NodeRef workflowPackage) {
        List<ChildAssociationRef> packageItems = nodeService.getChildAssocs(workflowPackage, WorkflowModel.ASSOC_PACKAGE_CONTAINS, RegexQNamePattern.MATCH_ALL);

        HashSet<NodeRef> versions = new HashSet<>();
        for(ChildAssociationRef item : packageItems) {
            NodeRef nodeRef = item.getChildRef();
            NodeRef versRef = getCurrentVersionRef(nodeRef, true);
            versions.add(versRef);
        }
        return versions;
    }

    public Set<NodeRef> getCurrentVersionRefs(DelegateExecution execution) {
        NodeRef workflowPackage = FlowableListenerUtils.getWorkflowPackage(execution);
        return getCurrentVersionRefs(workflowPackage);
    }

    public void saveConfirmableVersion(DelegateExecution execution) {
        Set<NodeRef> versions = getCurrentVersionRefs(execution);
        execution.setVariable(qNameConverter.mapQNameToName(ConfirmWorkflowModel.PROP_CONFIRMABLE_VERSION), versions);
    }

    public void saveCurrentVersion(DelegateExecution execution) {
        Set<NodeRef> versions = getCurrentVersionRefs(execution);
        execution.setVariable(qNameConverter.mapQNameToName(ConfirmWorkflowModel.PROP_CURRENT_VERSION), versions);
    }

    @SuppressWarnings("unchecked")
    public boolean isConfirmableVersion(DelegateExecution execution) {
        Set<NodeRef> currentVersions = getCurrentVersionRefs(execution);
        Set<NodeRef> confirmableVersions = (Set<NodeRef>) execution.getVariable(qNameConverter.mapQNameToName(ConfirmWorkflowModel.PROP_CONFIRMABLE_VERSION));
        return currentVersions.equals(confirmableVersions);
    }

    @SuppressWarnings("unchecked")
    public boolean isCurrentVersion(DelegateExecution execution) {
        Set<NodeRef> currentVersions = getCurrentVersionRefs(execution);
        Set<NodeRef> propCurrentVersions = (Set<NodeRef>) execution.getVariable(qNameConverter.mapQNameToName(ConfirmWorkflowModel.PROP_CURRENT_VERSION));
        return currentVersions.equals(propCurrentVersions);
    }

    public void saveConfirmDecision(NodeRef workflowPackage, String confirmerRole, String confirmTaskId) {
        // create confirm decision object
        Set<NodeRef> confirmVersions = this.getCurrentVersionRefs(workflowPackage);
        ArrayList<NodeRef> confirmVersionsValue = new ArrayList<>(confirmVersions.size());
        confirmVersionsValue.addAll(confirmVersions);

        Map<QName,Serializable> confirmDecisionProperties = new HashMap<>();
        confirmDecisionProperties.put(ConfirmWorkflowModel.PROP_CONFIRM_TASK_ID, confirmTaskId.replace("activiti", "flowable"));
        confirmDecisionProperties.put(ConfirmWorkflowModel.PROP_CONFIRM_VERSIONS, confirmVersionsValue);
        confirmDecisionProperties.put(ConfirmWorkflowModel.PROP_CONFIRM_ROLE, confirmerRole);

        // decision is saved as decision-<confirmRole>
        QName assocName = getConfirmDecisionAssocName(confirmerRole);

        // if there are such decisions, they should be deleted first
        List<ChildAssociationRef> decisionAssocs = nodeService.getChildAssocs(workflowPackage, ConfirmWorkflowModel.ASSOC_CONFIRM_DECISIONS, assocName);
        for(ChildAssociationRef decisionAssoc : decisionAssocs) {
            nodeService.removeChildAssociation(decisionAssoc);
        }

        List<ChildAssociationRef> docAssocs = nodeService.getChildAssocs(workflowPackage, ContentModel.ASSOC_CONTAINS, RegexQNamePattern.MATCH_ALL);
        docAssocs.addAll(nodeService.getChildAssocs(workflowPackage, WorkflowModel.ASSOC_PACKAGE_CONTAINS, RegexQNamePattern.MATCH_ALL));
        for (ChildAssociationRef docAssoc : docAssocs) {
            if (!nodeService.hasAspect(docAssoc.getChildRef(), ConfirmWorkflowModel.ASPECT_CONFIRMED))
                nodeService.addAspect(docAssoc.getChildRef(), ConfirmWorkflowModel.ASPECT_CONFIRMED, null);
        }

        // finally save confirm decision
        nodeService.createNode(workflowPackage,
                ConfirmWorkflowModel.ASSOC_CONFIRM_DECISIONS,
                assocName,
                ConfirmWorkflowModel.TYPE_CONFIRM_DECISION,
                confirmDecisionProperties);
    }

    public ConfirmDecision getConfirmDecision(DelegateExecution execution, String confirmerRole) {
        NodeRef workflowPackage = FlowableListenerUtils.getWorkflowPackage(execution);

        QName assocName = getConfirmDecisionAssocName(confirmerRole);

        List<ChildAssociationRef> decisionAssocs = nodeService.getChildAssocs(workflowPackage, ConfirmWorkflowModel.ASSOC_CONFIRM_DECISIONS, assocName);
        if(decisionAssocs.isEmpty()) {
            return null;
        }

        NodeRef confirmDecision = decisionAssocs.get(0).getChildRef();
        return new ConfirmDecision(confirmDecision, nodeService, workflowService);
    }

    public List<ConfirmDecision> getConfirmDecisions(DelegateExecution execution) {
        NodeRef workflowPackage = FlowableListenerUtils.getWorkflowPackage(execution);
        return getConfirmDecisions(workflowPackage);
    }

    private List<ConfirmDecision> getConfirmDecisions(NodeRef workflowPackage) {
        List<ChildAssociationRef> decisionAssocs = nodeService.getChildAssocs(workflowPackage, ConfirmWorkflowModel.ASSOC_CONFIRM_DECISIONS, RegexQNamePattern.MATCH_ALL);
        List<ConfirmDecision> confirmDecisions = new ArrayList<>(decisionAssocs.size());
        for(ChildAssociationRef decisionAssoc : decisionAssocs) {
            NodeRef confirmDecision = decisionAssoc.getChildRef();
            confirmDecisions.add(new ConfirmDecision(confirmDecision, nodeService, workflowService));
        }
        return confirmDecisions;
    }

    public List<ConfirmDecision> getLatestConfirmDecisions(NodeRef document) {
        Map<String, ConfirmDecision> latestDecisions = new HashMap<>();
        List<ChildAssociationRef> workflowPackageRefs = nodeService.getParentAssocs(document, WorkflowModel.ASSOC_PACKAGE_CONTAINS, RegexQNamePattern.MATCH_ALL);
        for(ChildAssociationRef workflowPackageRef : workflowPackageRefs) {
            NodeRef workflowPackage = workflowPackageRef.getParentRef();
            List<ConfirmDecision> workflowDecisions = getConfirmDecisions(workflowPackage);
            for(ConfirmDecision workflowDecision : workflowDecisions) {
                ConfirmDecision latestDecision = latestDecisions.get(workflowDecision.getConfirmerRole());
                // NOTE: if confirm date is null, this task is not yet commited
                // and thus it is the latest one
                if(latestDecision == null
                        || workflowDecision.getConfirmDate() == null
                        || latestDecision.getConfirmDate() != null
                        && workflowDecision.getConfirmDate().after(latestDecision.getConfirmDate())) {
                    latestDecisions.put(workflowDecision.getConfirmerRole(), workflowDecision);
                }
            }
        }
        List<ConfirmDecision> decisions = new ArrayList<>(latestDecisions.size());
        decisions.addAll(latestDecisions.values());
        return decisions;
    }

    public Set<String> getLatestConfirmers(NodeRef document) {
        List<ConfirmDecision> decisions = getLatestConfirmDecisions(document);
        Set<String> confirmers = new HashSet<>(decisions.size());
        for(ConfirmDecision decision : decisions) {
            confirmers.add(decision.getConfirmerRole());
        }
        return confirmers;
    }

    public Set<String> getLatestConfirmers(NodeRef document, String confirmOutcome) {
        List<ConfirmDecision> decisions = getLatestConfirmDecisions(document);
        Set<String> confirmers = new HashSet<>(decisions.size());
        for(ConfirmDecision decision : decisions) {
            if(confirmOutcome.equals(decision.getConfirmOutcome())) {
                confirmers.add(decision.getConfirmerRole());
            }
        }
        return confirmers;
    }

    private QName getConfirmDecisionAssocName(String confirmerRole) {
        return QName.createQName(ConfirmWorkflowModel.NAMESPACE, "decision-" + confirmerRole);
    }

    public void setServiceRegistry(ServiceRegistry services) {
        this.nodeService = services.getNodeService();
        this.versionService = services.getVersionService();
        this.qNameConverter = new WorkflowQNameConverter(services.getNamespaceService());
        this.workflowService = services.getWorkflowService();
        this.personService = services.getPersonService();
        this.authorityService = services.getAuthorityService();
    }

    @SuppressWarnings("unchecked")
    public boolean isLatestVersionConfirmedByAll(DelegateExecution execution) {
        Set<NodeRef> currentVersions = getCurrentVersionRefs(execution);

        List<ConfirmDecision> confirmDecisions = getConfirmDecisions(execution);
        boolean versionIsLatest = true;
        ArrayList<NodeRef> confirmerForOldVersions = new ArrayList<>(confirmDecisions.size());
        for(int i=0; i< confirmDecisions.size(); i++)
        {
            ConfirmDecision confirmDecision = confirmDecisions.get(i);
            if(!currentVersions.containsAll(confirmDecision.getConfirmVersions()))
            {
                versionIsLatest = false;
                if(confirmDecision.getConfirmerRole()!=null)
                {
                    confirmerForOldVersions.add(authorityService.getAuthorityNodeRef(confirmDecision.getConfirmerRole()));
                }
                else
                {
                    confirmerForOldVersions.add(personService.getPerson(confirmDecision.getConfirmerUser()));
                }
            }

        }
        Boolean notAllConfirmed = (Boolean)execution.getVariable("wfcf_notAllConfirmed");
        if(!notAllConfirmed && !versionIsLatest)
            saveConfirmersForOldVersions(execution, confirmerForOldVersions);
        return versionIsLatest;
    }

    public void saveConfirmersForOldVersions(DelegateExecution execution, ArrayList<NodeRef> confirmerForOldVersions) {
        execution.setVariable(qNameConverter.mapQNameToName(ConfirmWorkflowModel.ASSOC_CONFIRMERS), confirmerForOldVersions);
        if (!confirmerForOldVersions.isEmpty()) {
            StringBuilder precedenceLine = new StringBuilder(confirmerForOldVersions.get(0).toString());
            for (int i = 1; i < confirmerForOldVersions.size(); i++) {
                precedenceLine.append("|").append(confirmerForOldVersions.get(i).toString());
            }
            execution.setVariable(qNameConverter.mapQNameToName(ConfirmWorkflowModel.PROP_PRECEDENCE), precedenceLine.toString());
        }
    }
}
