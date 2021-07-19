package ru.citeck.ecos.behavior.role;

import org.alfresco.repo.node.NodeServicePolicies;
import org.alfresco.repo.policy.Behaviour;
import org.alfresco.service.cmr.repository.AssociationRef;
import ru.citeck.ecos.behavior.OrderedBehaviour;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import ru.citeck.ecos.model.ICaseModel;
import ru.citeck.ecos.model.ICaseRoleModel;
import ru.citeck.ecos.role.CaseRoleService;

import java.io.Serializable;
import java.util.Map;

/**
 * @author Pavel Simonov
 */
public class UpdateRoleBehaviour implements NodeServicePolicies.OnUpdatePropertiesPolicy,
                                            NodeServicePolicies.OnCreateAssociationPolicy,
                                            NodeServicePolicies.OnDeleteAssociationPolicy {

    private static final int ORDER = 150;

    private NodeService nodeService;
    private PolicyComponent policyComponent;
    private CaseRoleService caseRoleService;

    public void init() {
        this.policyComponent.bindClassBehaviour(
                NodeServicePolicies.OnUpdatePropertiesPolicy.QNAME,
                ICaseRoleModel.TYPE_ROLE,
                new OrderedBehaviour(this, "onUpdateProperties", Behaviour.NotificationFrequency.TRANSACTION_COMMIT, ORDER)
        );

        this.policyComponent.bindAssociationBehaviour(
                NodeServicePolicies.OnCreateAssociationPolicy.QNAME,
                ICaseRoleModel.TYPE_ROLE,
                new OrderedBehaviour(this, "onCreateAssociation",
                        Behaviour.NotificationFrequency.EVERY_EVENT, ORDER)
        );
        this.policyComponent.bindAssociationBehaviour(
                NodeServicePolicies.OnDeleteAssociationPolicy.QNAME,
                ICaseRoleModel.TYPE_ROLE,
                new OrderedBehaviour(this, "onDeleteAssociation",
                        Behaviour.NotificationFrequency.EVERY_EVENT, ORDER)
        );
    }

    @Override
    public void onUpdateProperties(NodeRef roleRef, Map<QName, Serializable> before, Map<QName, Serializable> after) {
        if (nodeService.exists(roleRef)) {
            NodeRef parentRef = nodeService.getPrimaryParent(roleRef).getParentRef();
            if (nodeService.hasAspect(parentRef, ICaseModel.ASPECT_CASE)
                    && !nodeService.hasAspect(parentRef, ICaseModel.ASPECT_LEGACY_EDITOR_TEMPLATE)) {
                caseRoleService.updateRole(roleRef);
            }
        }
    }

    @Override
    public void onCreateAssociation(AssociationRef nodeAssocRef) {
        roleChanged(nodeAssocRef.getSourceRef(), nodeAssocRef.getTargetRef(), null);
    }

    @Override
    public void onDeleteAssociation(AssociationRef nodeAssocRef) {
        roleChanged(nodeAssocRef.getSourceRef(), null, nodeAssocRef.getTargetRef());
    }

    private void roleChanged(NodeRef nodeRef, NodeRef added, NodeRef removed) {
        if (nodeRef != null && nodeService.exists(nodeRef)) {
            caseRoleService.roleChanged(nodeRef, added, removed);
        }
    }

    public void setPolicyComponent(PolicyComponent policyComponent) {
        this.policyComponent = policyComponent;
    }

    public void setCaseRoleService(CaseRoleService caseRoleService) {
        this.caseRoleService = caseRoleService;
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }
}
