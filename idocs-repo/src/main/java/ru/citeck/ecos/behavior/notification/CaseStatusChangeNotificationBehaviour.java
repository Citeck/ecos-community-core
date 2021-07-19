package ru.citeck.ecos.behavior.notification;


import org.alfresco.model.ContentModel;
import org.alfresco.repo.policy.Behaviour;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import ru.citeck.ecos.behavior.OrderedBehaviour;
import ru.citeck.ecos.icase.CaseStatusPolicies;
import ru.citeck.ecos.icase.activity.dto.ActivityRef;
import ru.citeck.ecos.icase.activity.dto.CaseActivity;
import ru.citeck.ecos.model.ICaseModel;

import java.util.List;
import java.util.Objects;

public class CaseStatusChangeNotificationBehaviour extends AbstractICaseDocumentNotificationBehaviour
        implements CaseStatusPolicies.OnCaseStatusChangedPolicy {

    private String documentNamespace;
    private String documentType;
    private String caseStatus;
    private String excludeStageName;

    private static final String ALL_STATUS_KEY = "AllStatus";

    public void init() {
        OrderedBehaviour statusChangeBehaviour = new OrderedBehaviour(
                this, "onCaseStatusChanged",
                Behaviour.NotificationFrequency.TRANSACTION_COMMIT, order
        );

        this.policyComponent.bindClassBehaviour(
                CaseStatusPolicies.OnCaseStatusChangedPolicy.QNAME,
                ICaseModel.TYPE_CASE_STATUS,
                statusChangeBehaviour
        );
    }

    @Override
    public void onCaseStatusChanged(NodeRef caseRef, NodeRef caseStatusBefore, NodeRef caseStatusAfter) {
        if (!nodeService.exists(caseRef)) {
            return;
        }

        if (!isActive(caseRef) || sender == null) {
            return;
        }

        if (excludeStageName != null && !excludeStageName.isEmpty()) {
            ActivityRef activityRef = activityCommonService.composeRootActivityRef(caseRef);
            List<CaseActivity> startedActivities = caseActivityService.getStartedActivities(activityRef);
            for (CaseActivity activity : startedActivities) {
                if (excludeStageName.equals(activity.getTitle())) {
                    return;
                }
            }
        }

        QName documentQName = nodeService.getType(caseRef);
        QName requiredQName = QName.createQName(documentNamespace, documentType);

        if (!Objects.equals(documentQName, requiredQName)) {
            return;
        }

        if (caseStatus.equals(ALL_STATUS_KEY)) {
            sender.sendNotification(caseRef, caseStatusAfter, recipients,
                    notificationType, subjectTemplate);
        } else {
            String currentStatus = (String) nodeService.getProperty(caseStatusAfter,
                    ContentModel.PROP_NAME);
            if (currentStatus.equals(caseStatus)) {
                sender.sendNotification(caseRef, caseStatusAfter, recipients,
                        notificationType, subjectTemplate);
            }
        }
    }

    public void setPolicyComponent(PolicyComponent policyComponent) {
        this.policyComponent = policyComponent;
    }

    public void setDocumentNamespace(String documentNamespace) {
        this.documentNamespace = documentNamespace;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public void setCaseStatus(String caseStatus) {
        this.caseStatus = caseStatus;
    }

    public void setExcludeStageName(String excludeStageName) {
        this.excludeStageName = excludeStageName;
    }
}
