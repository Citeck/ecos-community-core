package ru.citeck.ecos.flowable.services;

import org.alfresco.service.cmr.repository.NodeRef;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.utils.AlfrescoScopableProcessorExtension;
import ru.citeck.ecos.utils.JavaScriptImplUtils;
import ru.citeck.ecos.utils.NodeUtils;

import java.util.Set;

/**
 * @author Roman Makarskiy
 */
public class FlowableRecipientsServiceJS extends AlfrescoScopableProcessorExtension {

    private FlowableRecipientsService flowableRecipientsService;

    public String getRoleEmails(Object document, String caseRoleName) {
        RecordRef ref = toRecordRef(document);
        if (RecordRef.isNotEmpty(ref)) {
            return flowableRecipientsService.getRoleEmails(ref, caseRoleName);
        }
        NodeRef docRef = JavaScriptImplUtils.getNodeRef(document);
        return flowableRecipientsService.getRoleEmails(docRef, caseRoleName);
    }

    public String getUserEmail(String username) {
        return flowableRecipientsService.getUserEmail(username);
    }

    public String getAuthorityEmails(String authority) {
        return flowableRecipientsService.getAuthorityEmails(authority);
    }

    public Set<String> getRoleGroups(Object document, String caseRoleName) {
        RecordRef ref = toRecordRef(document);
        if (RecordRef.isNotEmpty(ref)) {
            return flowableRecipientsService.getRoleGroups(ref, caseRoleName);
        }
        NodeRef docRef = JavaScriptImplUtils.getNodeRef(document);
        return flowableRecipientsService.getRoleGroups(docRef, caseRoleName);
    }

    public Set<String> getRoleUsers(Object document, String caseRoleName) {
        RecordRef ref = toRecordRef(document);
        if (RecordRef.isNotEmpty(ref)) {
            return flowableRecipientsService.getRoleUsers(ref, caseRoleName);
        }
        NodeRef docRef = JavaScriptImplUtils.getNodeRef(document);
        return flowableRecipientsService.getRoleUsers(docRef, caseRoleName);
    }

    private RecordRef toRecordRef(Object document) {
        if (document instanceof RecordRef) {
            return (RecordRef) document;
        }
        if (document instanceof String) {
            String docStr = (String) document;
            if (!docStr.startsWith(NodeUtils.WORKSPACE_PREFIX)) {
                return RecordRef.valueOf((String) document);
            }
        }
        return RecordRef.EMPTY;
    }

    public void setFlowableRecipientsService(FlowableRecipientsService flowableRecipientsService) {
        this.flowableRecipientsService = flowableRecipientsService;
    }
}
