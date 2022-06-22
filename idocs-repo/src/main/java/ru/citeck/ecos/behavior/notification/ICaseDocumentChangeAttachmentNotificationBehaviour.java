package ru.citeck.ecos.behavior.notification;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.node.NodeServicePolicies;
import org.alfresco.repo.policy.Behaviour;
import org.alfresco.repo.policy.JavaBehaviour;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.repo.version.VersionServicePolicies;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.version.Version;
import org.alfresco.service.namespace.QName;
import ru.citeck.ecos.behavior.OrderedBehaviour;
import ru.citeck.ecos.model.ClassificationModel;
import ru.citeck.ecos.model.ICaseModel;

import java.util.HashMap;

/**
 * @author Roman.Makarskiy on 10/21/2016.
 */
public class ICaseDocumentChangeAttachmentNotificationBehaviour extends AbstractICaseDocumentNotificationBehaviour
        implements NodeServicePolicies.OnCreateChildAssociationPolicy,
        NodeServicePolicies.BeforeDeleteNodePolicy,
        VersionServicePolicies.AfterCreateVersionPolicy {

    private PolicyComponent policyComponent;
    private String documentNamespace;

    private String documentType;
    private QName documentQName;
    private HashMap<String, Object> addition;

    private static final String PARAM_TYPE = "type";
    private static final String PARAM_KIND = "kind";
    private static final String PARAM_FILE_NAME = "fileName";
    private static final String PARAM_METHOD = "method";
    private static final String PARAM_METHOD_ON_DELETE = "onDelete";
    private static final String PARAM_METHOD_ON_CREATE = "onCreate";
    private static final String PARAM_METHOD_UPLOAD_NEW_VERSION = "uploadNewVersion";

    public void init() {
        documentQName = QName.createQName(documentNamespace, documentType);

        this.policyComponent.bindAssociationBehaviour(
                NodeServicePolicies.OnCreateChildAssociationPolicy.QNAME,
                documentQName, ICaseModel.ASSOC_DOCUMENTS,
                new OrderedBehaviour(this, "onCreateChildAssociation",
                        Behaviour.NotificationFrequency.TRANSACTION_COMMIT, order)
        );

        this.policyComponent.bindClassBehaviour(
                NodeServicePolicies.BeforeDeleteNodePolicy.QNAME,
                ContentModel.TYPE_CONTENT,
                new JavaBehaviour(this, "beforeDeleteNode", Behaviour.NotificationFrequency.EVERY_EVENT)
        );

        this.policyComponent.bindClassBehaviour(
                VersionServicePolicies.AfterCreateVersionPolicy.QNAME,
                ContentModel.TYPE_CONTENT,
                new OrderedBehaviour(this, "afterCreateVersion",
                        Behaviour.NotificationFrequency.TRANSACTION_COMMIT, order)
        );
    }

    @Override
    public void onCreateChildAssociation(ChildAssociationRef childAssociationRef, boolean b) {
        NodeRef attachmentRef = childAssociationRef.getChildRef();
        NodeRef caseRef;

        if (sender == null || !nodeService.exists(attachmentRef)) {
            return;
        } else {
            caseRef = childAssociationRef.getParentRef();
        }

        if (!nodeService.exists(caseRef) || !isActive(caseRef)) {
            return;
        }

        addition = new HashMap<>();
        addition = addTypeAndKind(attachmentRef, addition);
        addition.put(PARAM_METHOD, PARAM_METHOD_ON_CREATE);
        sender.setAdditionArgs(addition);
        sender.sendNotification(caseRef, attachmentRef, recipients,
                notificationType, subjectTemplate);
    }

    @Override
    public void beforeDeleteNode(NodeRef nodeRef) {
        if (sender == null || !nodeService.exists(nodeRef)
                || nodeService.hasAspect(nodeRef, ContentModel.ASPECT_WORKING_COPY)) {
            return;
        }

        ChildAssociationRef primaryParent = nodeService.getPrimaryParent(nodeRef);
        NodeRef caseRef = primaryParent.getParentRef();

        if (nodeService.exists(caseRef) && primaryParent.getTypeQName().equals(ICaseModel.ASSOC_DOCUMENTS)) {
            if (!isActive(caseRef)) {
                return;
            }
            QName parentQName = nodeService.getType(caseRef);
            if (parentQName.equals(documentQName)) {
                String fileName = (String) nodeService.getProperty(nodeRef, ContentModel.PROP_NAME);
                addition = new HashMap<>();
                addTypeAndKind(nodeRef, addition);
                addition.put(PARAM_METHOD, PARAM_METHOD_ON_DELETE);
                addition.put(PARAM_FILE_NAME, fileName);
                sender.setAdditionArgs(addition);
                sender.sendNotification(caseRef, nodeRef, recipients,
                    notificationType, subjectTemplate, true);
            }
        }
    }

    @Override
    public void afterCreateVersion(NodeRef nodeRef, Version version) {
        if (sender == null || !nodeService.exists(nodeRef)) {
            return;
        }

        ChildAssociationRef primaryParent = nodeService.getPrimaryParent(nodeRef);
        NodeRef caseRef = primaryParent.getParentRef();

        if (nodeService.exists(caseRef) && isActive(caseRef)
                && primaryParent.getTypeQName().equals(ICaseModel.ASSOC_DOCUMENTS)
                && nodeService.getType(caseRef).equals(documentQName)) {
            addition = new HashMap<>();
            addition = addTypeAndKind(nodeRef, addition);
            addition.put(PARAM_METHOD, PARAM_METHOD_UPLOAD_NEW_VERSION);
            sender.setAdditionArgs(addition);
            sender.sendNotification(caseRef, nodeRef, recipients,
                    notificationType, subjectTemplate);
        }
    }

    private HashMap<String, Object> addTypeAndKind(NodeRef nodeRef, HashMap<String, Object> addition) {
        if (nodeService.exists(nodeRef)) {
            NodeRef documentType = (NodeRef) nodeService.getProperty(nodeRef,
                    ClassificationModel.PROP_DOCUMENT_TYPE);
            if (documentType != null) {
                addition.put(PARAM_TYPE, documentType);
            }
            NodeRef documentKind = (NodeRef) nodeService.getProperty(nodeRef,
                    ClassificationModel.PROP_DOCUMENT_KIND);
            if (documentKind != null) {
                addition.put(PARAM_KIND, documentKind);
            }
        }
        return addition;
    }

    public void setDocumentNamespace(String documentNamespace) {
        this.documentNamespace = documentNamespace;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public void setPolicyComponent(PolicyComponent policyComponent) {
        this.policyComponent = policyComponent;
    }

}
