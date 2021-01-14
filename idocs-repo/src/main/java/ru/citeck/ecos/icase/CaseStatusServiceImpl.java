package ru.citeck.ecos.icase;

import lombok.Setter;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.policy.ClassPolicyDelegate;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import ru.citeck.ecos.model.ICaseModel;
import ru.citeck.ecos.model.lib.role.service.StatusService;
import ru.citeck.ecos.model.lib.status.dto.StatusDef;
import ru.citeck.ecos.model.lib.type.service.TypeDefService;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.utils.DictionaryUtils;
import ru.citeck.ecos.utils.LazyNodeRef;
import ru.citeck.ecos.utils.RepoUtils;

import java.util.*;

/**
 * @author Roman Makarskiy
 * @author Pavel Simonov
 */
public class CaseStatusServiceImpl implements CaseStatusService {

    private NodeService nodeService;
    private PolicyComponent policyComponent;
    private LazyNodeRef caseStatusesPath;

    @Autowired @Setter
    private CaseStatusAssocDao caseStatusAssocDao;
    @Autowired @Setter
    private TypeDefService typeDefService;
    @Autowired @Setter
    private StatusService statusService;

    private ClassPolicyDelegate<CaseStatusPolicies.OnCaseStatusChangedPolicy> onCaseStatusChangedPolicyDelegate;

    public void init() {
        onCaseStatusChangedPolicyDelegate = policyComponent.registerClassPolicy(CaseStatusPolicies.OnCaseStatusChangedPolicy.class);
    }

    @Override
    public void setStatus(NodeRef caseRef, NodeRef caseStatusRef) {

        mandatoryNodeRef("Case", caseRef);
        mandatoryNodeRef("Case status", caseStatusRef);

        NodeRef currentCaseStatus = caseStatusAssocDao.getStatusByAssoc(caseRef, ICaseModel.ASSOC_CASE_STATUS);

        if (!Objects.equals(currentCaseStatus, caseStatusRef)) {
            if (currentCaseStatus != null) {
                clearBeforeCaseStatus(caseRef);
                setBeforeCaseStatus(caseRef, currentCaseStatus);
            }
            setCaseStatus(caseRef, caseStatusRef);
            nodeService.setProperty(caseRef, ICaseModel.PROP_CASE_STATUS_CHANGED_DATETIME, new Date());

            triggerStatusChanged(caseRef, caseStatusRef, currentCaseStatus);
        }
    }

    private void clearBeforeCaseStatus(NodeRef caseRef) {
        NodeRef beforeCaseStatus = caseStatusAssocDao.getStatusByAssoc(caseRef, ICaseModel.ASSOC_CASE_STATUS_BEFORE);
        if (beforeCaseStatus != null) {
            if (isAlfRef(beforeCaseStatus)) {
                nodeService.removeAssociation(caseRef, beforeCaseStatus, ICaseModel.ASSOC_CASE_STATUS_BEFORE);
            } else {
                nodeService.setProperty(caseRef, ICaseModel.ASSOC_CASE_STATUS_BEFORE_PROP, null);
            }
        }
    }

    private void setBeforeCaseStatus(NodeRef caseRef, NodeRef beforeCaseStatus) {
        if (isAlfRef(beforeCaseStatus)) {
            nodeService.createAssociation(caseRef, beforeCaseStatus, ICaseModel.ASSOC_CASE_STATUS_BEFORE);
            nodeService.removeAssociation(caseRef, beforeCaseStatus, ICaseModel.ASSOC_CASE_STATUS);
        } else {
            nodeService.setProperty(caseRef, ICaseModel.ASSOC_CASE_STATUS_BEFORE_PROP, beforeCaseStatus.getId());
        }
    }

    private void setCaseStatus(NodeRef caseRef, NodeRef caseStatusRef) {
        if (isAlfRef(caseStatusRef)) {
            nodeService.setProperty(caseRef, ICaseModel.ASSOC_CASE_STATUS_PROP, null);
            nodeService.createAssociation(caseRef, caseStatusRef, ICaseModel.ASSOC_CASE_STATUS);
        } else {
            nodeService.setProperty(caseRef, ICaseModel.ASSOC_CASE_STATUS_PROP, caseStatusRef.getId());
        }
    }

    private void triggerStatusChanged(NodeRef caseRef, NodeRef caseStatusRef, NodeRef beforeCaseStatus) {
        Set<QName> classes;
        if (isAlfRef(caseStatusRef)) {
            classes = new HashSet<>(DictionaryUtils.getNodeClassNames(caseStatusRef, nodeService));
        } else {
            classes = Collections.singleton(ICaseModel.TYPE_CASE_STATUS);
        }
        CaseStatusPolicies.OnCaseStatusChangedPolicy changedPolicy;
        changedPolicy = onCaseStatusChangedPolicyDelegate.get(caseStatusRef, classes);
        changedPolicy.onCaseStatusChanged(caseRef, beforeCaseStatus, caseStatusRef);
    }

    @Override
    public NodeRef getStatusByName(NodeRef node, String statusName) {
        if (statusName == null) {
            return null;
        }
        if (node == null) {
            return getStatusByName(statusName);
        }

        RecordRef typeRef = typeDefService.getTypeRef(RecordRef.valueOf(node.toString()));
        return getStatusByNameAndType(statusName, typeRef);
    }

    @Override
    public NodeRef getStatusByNameAndType(String statusName, RecordRef etype) {
        if (statusName == null || etype == null) {
            return null;
        }

        StatusDef statusDef = statusService.getStatusDefByType(etype, statusName);
        if (statusDef == null) {
            return getStatusByName(statusName);
        }

        return caseStatusAssocDao.statusToNode(statusDef.getId());
    }

    @Override
    public NodeRef getStatusByName(String statusName) {
        if (statusName == null) {
            return null;
        }
        NodeRef root = caseStatusesPath.getNodeRef();
        if (root == null) {
            return null;
        }
        return nodeService.getChildByName(root, ContentModel.ASSOC_CONTAINS, statusName);
    }

    @Override
    public NodeRef getEcosStatusByName(String statusName) {
        return caseStatusAssocDao.statusToNode(statusName);
    }

    @Override
    public void setStatus(NodeRef document, String status) {
        NodeRef statusRef = getStatusByName(document, status);
        if (statusRef == null) {
            throw new IllegalArgumentException("Status " + status + " not found!");
        }
        setStatus(document, statusRef);
    }

    @Override
    public String getStatus(NodeRef caseRef) {
        NodeRef statusRef = getStatusRef(caseRef);
        return getStatusName(caseRef, statusRef);
    }

    @Override
    public String getStatusBefore(NodeRef caseRef) {
        NodeRef statusRef = getStatusBeforeRef(caseRef);
        return getStatusName(caseRef, statusRef);
    }

    @Nullable
    public String getStatusName(NodeRef caseRef, NodeRef statusRef) {
        if (statusRef == null) {
            return null;
        }

        if (isAlfRef(statusRef)) {
            return (String) nodeService.getProperty(statusRef, ContentModel.PROP_NAME);
        } else {
            StatusDef statusDef = getStatusDef(caseRef, statusRef.getId());
            // PROP_NAME in caseStatusAssoc equals ID in statusDef
            return statusDef != null ? statusDef.getId() : null;
        }
    }

    @Override
    public NodeRef getStatusRef(NodeRef caseRef) {
        return caseStatusAssocDao.getStatusByAssoc(caseRef, ICaseModel.ASSOC_CASE_STATUS);
    }

    @Override
    public StatusDef getStatusDef(NodeRef caseRef, String statusId) {
        if (StringUtils.isBlank(statusId)) {
            return null;
        }
        RecordRef recordRef = RecordRef.valueOf(caseRef.toString());
        return statusService.getStatusDefByDocument(recordRef, statusId);
    }

    @Override
    public NodeRef getStatusBeforeRef(NodeRef caseRef) {
        return caseStatusAssocDao.getStatusByAssoc(caseRef, ICaseModel.ASSOC_CASE_STATUS_BEFORE);
    }

    @Override
    public NodeRef getStatusRefFromPrimaryParent(NodeRef childRef) {
        NodeRef parent = RepoUtils.getPrimaryParentRef(childRef, nodeService);
        return caseStatusAssocDao.getStatusByAssoc(parent, ICaseModel.ASSOC_CASE_STATUS);
    }

    private void mandatoryNodeRef(String strParamName, NodeRef nodeRef) {
        if (nodeRef == null) {
            throw new IllegalArgumentException(strParamName + " is a mandatory parameter");
        }
        if (isAlfRef(nodeRef) && !nodeService.exists(nodeRef)) {
            throw new IllegalArgumentException(strParamName + " with nodeRef: " + nodeRef + " doesn't exist");
        }
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setPolicyComponent(PolicyComponent policyComponent) {
        this.policyComponent = policyComponent;
    }

    public void setCaseStatusesPath(LazyNodeRef caseStatusesPath) {
        this.caseStatusesPath = caseStatusesPath;
    }
}
