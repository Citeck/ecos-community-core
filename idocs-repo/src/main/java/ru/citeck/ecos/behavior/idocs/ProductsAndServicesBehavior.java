package ru.citeck.ecos.behavior.idocs;

import org.alfresco.repo.node.NodeServicePolicies;
import org.alfresco.repo.policy.Behaviour;
import ru.citeck.ecos.behavior.JavaBehaviour;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.collections.CollectionUtils;
import ru.citeck.ecos.model.ProductsAndServicesModel;
import ru.citeck.ecos.utils.RepoUtils;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author Maxim Strizhov
 * @author Roman Makarskiy
 */
public class ProductsAndServicesBehavior implements NodeServicePolicies.OnCreateNodePolicy,
        NodeServicePolicies.OnUpdatePropertiesPolicy,
        NodeServicePolicies.BeforeDeleteNodePolicy {

    private NodeService nodeService;
    private PolicyComponent policyComponent;
    private QName type;

    public void init() {
        this.policyComponent.bindClassBehaviour(NodeServicePolicies.OnCreateNodePolicy.QNAME,
                type, new JavaBehaviour(this,
                        "onCreateNode",
                        Behaviour.NotificationFrequency.TRANSACTION_COMMIT));
        this.policyComponent.bindClassBehaviour(NodeServicePolicies.OnUpdatePropertiesPolicy.QNAME,
                type, new JavaBehaviour(this,
                        "onUpdateProperties",
                        Behaviour.NotificationFrequency.TRANSACTION_COMMIT));
        this.policyComponent.bindClassBehaviour(NodeServicePolicies.BeforeDeleteNodePolicy.QNAME,
                type, new JavaBehaviour(this,
                        "beforeDeleteNode",
                        Behaviour.NotificationFrequency.EVERY_EVENT));
    }

    @Override
    public void onCreateNode(ChildAssociationRef childAssociationRef) {
        NodeRef pasEntityRef = childAssociationRef.getChildRef();

        if (!nodeService.exists(pasEntityRef)) {
            return;
        }

        onCreateCalculateOrder(pasEntityRef);

        Double price = RepoUtils.getProperty(pasEntityRef, ProductsAndServicesModel.PROP_PRICE_PER_UNIT, nodeService);
        Double quantity = RepoUtils.getProperty(pasEntityRef, ProductsAndServicesModel.PROP_QUANTITY, nodeService);
        if (price != null && quantity != null) {
            calculateAndSetTotalProp(pasEntityRef, price, quantity);
        }
    }

    @Override
    public void onUpdateProperties(NodeRef nodeRef, Map<QName, Serializable> before, Map<QName, Serializable> after) {
        if (!nodeService.exists(nodeRef)) {
            return;
        }

        if (after.get(ProductsAndServicesModel.PROP_PRICE_PER_UNIT) != null
                && after.get(ProductsAndServicesModel.PROP_QUANTITY) != null) {
            Double priceBefore = (Double) before.get(ProductsAndServicesModel.PROP_PRICE_PER_UNIT);
            Double priceAfter = (Double) after.get(ProductsAndServicesModel.PROP_PRICE_PER_UNIT);
            Double quantityBefore = (Double) before.get(ProductsAndServicesModel.PROP_QUANTITY);
            Double quantityAfter = (Double) after.get(ProductsAndServicesModel.PROP_QUANTITY);
            if (!Objects.equals(priceBefore, priceAfter) || !Objects.equals(quantityBefore, quantityAfter)) {
                calculateAndSetTotalProp(nodeRef, priceAfter, quantityAfter);
            }
        }
    }

    @Override
    public void beforeDeleteNode(NodeRef nodeRef) {
        if (!nodeService.exists(nodeRef)) {
            return;
        }

        beforeDeleteCalculateOrder(nodeRef);
    }

    private void calculateAndSetTotalProp(NodeRef currentPasEntity, Double currentPrice, Double currentQuantity) {
        BigDecimal price = new BigDecimal(currentPrice, MathContext.DECIMAL64);
        BigDecimal quantity = new BigDecimal(currentQuantity, MathContext.DECIMAL64);
        nodeService.setProperty(currentPasEntity,
            ProductsAndServicesModel.PROP_TOTAL,
            price.multiply(quantity).doubleValue());
    }

    private void onCreateCalculateOrder(NodeRef currentPasEntity) {
        List<NodeRef> pasEntityRefs = getEntityRefs(currentPasEntity);
        int maxOrder = -1;

        if (CollectionUtils.isNotEmpty(pasEntityRefs)) {
            for (NodeRef entityRef : pasEntityRefs) {
                if (!currentPasEntity.equals(entityRef)) {
                    Integer order = (Integer) nodeService.getProperty(entityRef,
                            ProductsAndServicesModel.PROP_ORDER);
                    if (order != null && order > maxOrder) {
                        maxOrder = order;
                    }
                }
            }
        }

        if (maxOrder > 0) {
            nodeService.setProperty(currentPasEntity, ProductsAndServicesModel.PROP_ORDER, maxOrder + 1);
        } else {
            nodeService.setProperty(currentPasEntity, ProductsAndServicesModel.PROP_ORDER, 1);
        }
    }

    private void beforeDeleteCalculateOrder(NodeRef currentPasEntity) {
        List<NodeRef> pasEntityRefs = getEntityRefs(currentPasEntity);
        int pasEntityOrder = (Integer) nodeService.getProperty(currentPasEntity,
                ProductsAndServicesModel.PROP_ORDER);

        for (NodeRef entityRef : pasEntityRefs) {
            if (!currentPasEntity.equals(entityRef)) {
                Integer order = (Integer) nodeService.getProperty(entityRef, ProductsAndServicesModel.PROP_ORDER);
                if (order > pasEntityOrder) {
                    nodeService.setProperty(entityRef, ProductsAndServicesModel.PROP_ORDER, (order - 1));
                }
            }
        }
    }

    private List<NodeRef> getEntityRefs(NodeRef entityRef) {
        NodeRef source = RepoUtils.getPrimaryParentRef(entityRef,
                nodeService);

        List<NodeRef> pasEntityRefs = RepoUtils.getChildrenByAssoc(source,
                ProductsAndServicesModel.ASSOC_CONTAINS_PRODUCTS_AND_SERVICES, nodeService);

        return new ArrayList<>(pasEntityRefs);
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setPolicyComponent(PolicyComponent policyComponent) {
        this.policyComponent = policyComponent;
    }

    public void setType(QName type) {
        this.type = type;
    }
}
