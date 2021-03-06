package ru.citeck.ecos.behavior.record.event;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.node.NodeServicePolicies;
import org.alfresco.repo.policy.Behaviour;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.service.cmr.repository.AssociationRef;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.namespace.RegexQNamePattern;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import ru.citeck.ecos.behavior.JavaBehaviour;
import ru.citeck.ecos.events.data.dto.record.RecordEventType;
import ru.citeck.ecos.history.RecordEventService;
import ru.citeck.ecos.utils.TransactionUtils;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Roman Makarskiy
 * <p>
 * TODO: optimization - after behaviour merge all atts in 1 collection?
 */
@Slf4j
public class RecordEventAttributesBehaviour implements NodeServicePolicies.OnCreateNodePolicy,
        NodeServicePolicies.OnUpdatePropertiesPolicy,
        NodeServicePolicies.OnCreateAssociationPolicy,
        NodeServicePolicies.OnDeleteAssociationPolicy,
        NodeServicePolicies.OnCreateChildAssociationPolicy,
        NodeServicePolicies.OnDeleteChildAssociationPolicy,
        NodeServicePolicies.OnDeleteNodePolicy {

    private static final String TXN_RECORD_EVENT_UPDATE = "RECORD_EVENT_ATTS_UPDATE";

    private static final Map<String, Long> createdNodes = new HashMap<>();

    private PolicyComponent policyComponent;
    private NodeService nodeService;
    private RecordEventService recordEventService;
    private NamespaceService namespaceService;

    private QName type;
    private List<QName> allowedAttributes = new ArrayList<>();

    public void init() {
        policyComponent.bindClassBehaviour(NodeServicePolicies.OnCreateNodePolicy.QNAME,
                type, new JavaBehaviour(this, "onCreateNode",
                        Behaviour.NotificationFrequency.FIRST_EVENT));

        policyComponent.bindClassBehaviour(NodeServicePolicies.OnUpdatePropertiesPolicy.QNAME, type,
                new JavaBehaviour(this, "onUpdateProperties",
                        Behaviour.NotificationFrequency.EVERY_EVENT));

        policyComponent.bindAssociationBehaviour(NodeServicePolicies.OnCreateAssociationPolicy.QNAME, type,
                new JavaBehaviour(this, "onCreateAssociation",
                        Behaviour.NotificationFrequency.TRANSACTION_COMMIT));

        policyComponent.bindAssociationBehaviour(NodeServicePolicies.OnDeleteAssociationPolicy.QNAME, type,
                new JavaBehaviour(this, "onDeleteAssociation",
                        Behaviour.NotificationFrequency.TRANSACTION_COMMIT)
        );
        policyComponent.bindAssociationBehaviour(NodeServicePolicies.OnCreateChildAssociationPolicy.QNAME, type,
                new JavaBehaviour(this, "onCreateChildAssociation",
                        Behaviour.NotificationFrequency.TRANSACTION_COMMIT)
        );
        policyComponent.bindAssociationBehaviour(NodeServicePolicies.OnDeleteChildAssociationPolicy.QNAME, type,
                new JavaBehaviour(this, "onDeleteChildAssociation")
        );
        policyComponent.bindClassBehaviour(NodeServicePolicies.OnDeleteNodePolicy.QNAME, ContentModel.TYPE_CONTENT,
                new JavaBehaviour(this, "onDeleteNode",
                        Behaviour.NotificationFrequency.EVERY_EVENT)
        );
    }

    //TODO: send delete event to facade?
    @Override
    public void onDeleteNode(ChildAssociationRef childAssocRef, boolean isNodeArchived) {
        log.debug("RecordEventPropertiesBehaviour onDeleteNode={}" +
                "\nchildAssocRef:{}", this, childAssocRef);

        assocUpdated(childAssocRef.getTypeQName(), childAssocRef.getParentRef());
    }

    @Override
    public void onCreateNode(ChildAssociationRef childAssocRef) {
        log.debug("RecordEventPropertiesBehaviour onCreateNode={}" +
                "\nchildAssocRef:{}", this, childAssocRef);

        NodeRef nodeRef = childAssocRef.getChildRef();
        NodeRef.Status status = nodeService.getNodeStatus(nodeRef);
        synchronized (createdNodes) {
            createdNodes.put(nodeRef.getId(), status.getDbTxnId());
        }
        log.debug("RecordEventPropertiesBehaviour onCreateNode=" + isNewNode(nodeRef));

        Set<String> atts = nodeService.getProperties(nodeRef).keySet()
                .stream()
                .filter(serializable -> allowedAttributes.contains(serializable))
                .map(serializable -> serializable.toPrefixString(namespaceService))
                .collect(Collectors.toSet());

        Set<String> assocAtts = nodeService.getTargetAssocs(nodeRef, RegexQNamePattern.MATCH_ALL)
                .stream()
                .map(AssociationRef::getTypeQName)
                .filter(qName -> allowedAttributes.contains(qName))
                .map(qName -> qName.toPrefixString(namespaceService))
                .collect(Collectors.toSet());

        atts.addAll(assocAtts);

        log.debug("RecordEventPropertiesBehaviour onCreateNode. Atts:{}", atts);

        if (CollectionUtils.isEmpty(atts)) {
            return;
        }

        TransactionUtils.processAfterBehaviours(TXN_RECORD_EVENT_UPDATE, atts, attributes ->
                recordEventService.emitAttrChanged(
                        RecordEventType.UPDATE,
                        nodeRef.toString(),
                        attributes
                ));
    }

    private boolean isNewNode(NodeRef nodeRef) {
        NodeRef.Status status = nodeService.getNodeStatus(nodeRef);
        synchronized (createdNodes) {
            Long createdDbTxnId = createdNodes.get(nodeRef.getId());
            if (createdDbTxnId == null) {
                return false;
            }

            if (createdDbTxnId.equals(status.getDbTxnId())) {
                return true;
            } else {
                // Remove from cache if not new
                createdNodes.remove(nodeRef.getId());
                return false;
            }
        }
    }

    @Override
    public void onCreateAssociation(AssociationRef nodeAssocRef) {
        log.debug("RecordEventPropertiesBehaviour onCreateAssociation={}" +
                "\nnodeAssocRef:{}", this, nodeAssocRef);

        assocUpdated(nodeAssocRef.getTypeQName(), nodeAssocRef.getSourceRef());
    }

    @Override
    public void onDeleteAssociation(AssociationRef nodeAssocRef) {
        log.debug("RecordEventPropertiesBehaviour onDeleteAssociation={}" +
                "\nnodeAssocRef:{}", this, nodeAssocRef);

        assocUpdated(nodeAssocRef.getTypeQName(), nodeAssocRef.getSourceRef());
    }

    @Override
    public void onCreateChildAssociation(ChildAssociationRef childAssocRef, boolean isNewNode) {
        log.debug("RecordEventPropertiesBehaviour onDeleteAssociation={}" +
                "\nchildAssocRef:{}", this, childAssocRef);

        assocUpdated(childAssocRef.getTypeQName(), childAssocRef.getParentRef());
    }


    @Override
    public void onDeleteChildAssociation(ChildAssociationRef childAssocRef) {
        log.debug("RecordEventPropertiesBehaviour onDeleteChildAssociation={}" +
                "\nchildAssocRef:{}", this, childAssocRef);

        assocUpdated(childAssocRef.getTypeQName(), childAssocRef.getParentRef());
    }

    private void assocUpdated(QName assocType, NodeRef source) {
        if (type == null || !nodeService.exists(source)) {
            return;
        }

        QName sourceNodeType = nodeService.getType(source);

        if (!type.equals(sourceNodeType) || !allowedAttributes.contains(assocType)) {
            return;
        }

        Set<String> atts = new HashSet<>(1);
        atts.add(assocType.toPrefixString(namespaceService));

        log.debug("assocUpdated, atts:{}", atts);

        TransactionUtils.processAfterBehaviours(TXN_RECORD_EVENT_UPDATE, atts, attributes ->
                recordEventService.emitAttrChanged(
                        RecordEventType.UPDATE,
                        source.toString(),
                        attributes
                ));
    }

    @Override
    public void onUpdateProperties(NodeRef nodeRef, Map<QName, Serializable> before, Map<QName, Serializable> after) {
        log.debug("RecordEventPropertiesBehaviour onUpdateProperties={}" +
                "\nnodeRef:{}, isNewNode:{}", this, nodeRef, isNewNode(nodeRef));

        if (type == null || !nodeService.exists(nodeRef)) {
            return;
        }

        QName currentType = nodeService.getType(nodeRef);
        if (!currentType.equals(type)) {
            return;
        }

        Serializable uuid = before.get(ContentModel.PROP_NODE_UUID);
        if (uuid == null) {
            return;
        }

        Set<String> atts = new HashSet<>();

        if (isNewNode(nodeRef)) {
            atts.addAll(allowedAttributes
                    .stream()
                    .map(qName -> qName.toPrefixString(namespaceService))
                    .collect(Collectors.toList()));
        } else {
            Map<QName, Serializable> properties = nodeService.getProperties(nodeRef);
            for (Map.Entry<QName, Serializable> entry : properties.entrySet()) {
                QName key = entry.getKey();
                if (!allowedAttributes.contains(key)) {
                    continue;
                }

                Object propBefore = before.get(key);
                Object propAfter = after.get(key);

                if (Objects.equals(propAfter, propBefore)) {
                    continue;
                }

                log.debug("RecordEventPropertiesBehaviour " +
                        "\npropBefore:{}," +
                        "\npropAfter:{}", propBefore, propAfter);

                atts.add(key.toPrefixString(namespaceService));
            }
        }

        log.debug("RecordEventPropertiesBehaviour onUpdateProperties, atts:{}", atts);

        if (CollectionUtils.isEmpty(atts)) {
            return;
        }

        TransactionUtils.processAfterBehaviours(TXN_RECORD_EVENT_UPDATE, atts, attributes ->
                recordEventService.emitAttrChanged(
                        RecordEventType.UPDATE,
                        nodeRef.toString(),
                        attributes
                ));
    }

    @Autowired
    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    @Autowired
    public void setRecordEventService(RecordEventService recordEventService) {
        this.recordEventService = recordEventService;
    }

    @Autowired
    public void setNamespaceService(NamespaceService namespaceService) {
        this.namespaceService = namespaceService;
    }

    @Autowired
    public void setPolicyComponent(PolicyComponent policyComponent) {
        this.policyComponent = policyComponent;
    }

    public void setType(QName type) {
        this.type = type;
    }

    public void setAllowedAttributes(List<QName> allowedAttributes) {
        this.allowedAttributes = allowedAttributes;
    }
}
