package ru.citeck.ecos.notification.utils;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.repository.AssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang.StringUtils;
import ru.citeck.ecos.role.CaseRoleService;
import ru.citeck.ecos.utils.RepoUtils;

import java.util.*;

/**
 * @author Roman Makarskiy
 */
@Slf4j
public class RecipientsUtils {

    public static Set<String> getRecipientsFromRole(List<String> roles, NodeRef iCase, NodeService nodeService,
                                                                        DictionaryService dictionaryService,
                                                                        CaseRoleService caseRoleService) {
        Set<String> recipients = new HashSet<>();
        for (String recipientFromICaseRole : roles) {
            List<NodeRef> iCaseRoles = caseRoleService.getRoles(iCase);
            if (!iCaseRoles.isEmpty()) {
                NodeRef iCaseRole = getICaseRoleOrNullNotFound(recipientFromICaseRole, iCaseRoles, caseRoleService);
                if (iCaseRole != null) {
                    Set<NodeRef> recipientsRef = caseRoleService.getAssignees(iCaseRole);
                    if (!recipientsRef.isEmpty()) {
                        for (NodeRef recipientRef : recipientsRef) {
                            addRecipient(recipients, recipientRef, nodeService, dictionaryService);
                        }
                    } else {
                        if (log.isDebugEnabled()) {
                            log.debug("Cannot find recipients in case : " + iCaseRole);
                        }
                    }
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("Cannot find needed iCase role: " + recipientFromICaseRole
                                + " in document: " + iCase);
                    }
                }

            } else {
                if (log.isDebugEnabled()) {
                    log.debug("iCase Role is empty in document: " + iCase);
                }
            }
        }
        return recipients;
    }

    public static Set<String> getRecipientFromNodeAssoc(List<QName> assocs, NodeRef node, NodeService nodeService,
                                                                            DictionaryService dictionaryService) {
        Set<String> assocRecipientsNames = new HashSet<>();
        for (QName recipient : assocs) {
            List<AssociationRef> recipientList = nodeService.getTargetAssocs(node, recipient);
            if (!recipientList.isEmpty()) {
                addRecipient(assocRecipientsNames, recipientList.get(0).getTargetRef(), nodeService, dictionaryService);
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("Cannot find recipient: " + recipient + " for document: " + node);
                }
            }
        }
        return assocRecipientsNames;
    }

    public static Set<String> getRecipientsToExclude(List<String> ecludeRecipients, NodeRef node, ServiceRegistry serviceRegistry) {
        Set<String> recipientsToExclude = new HashSet<>();
        for (String recipient : ecludeRecipients) {
            if ("yourself".equals(recipient)) {
                String currentUser = serviceRegistry.getAuthenticationService().getCurrentUserName();
                addRecipient(recipientsToExclude, serviceRegistry.getPersonService().getPerson(currentUser),
                        serviceRegistry.getNodeService(), serviceRegistry.getDictionaryService());
            } else if (recipient.contains(":")) {
                List<QName> qNameList = Arrays.asList(QName.resolveToQName(serviceRegistry.getNamespaceService(), recipient));
                recipientsToExclude.addAll(
                        getRecipientFromNodeAssoc(
                                qNameList,
                                node,
                                serviceRegistry.getNodeService(),
                                serviceRegistry.getDictionaryService()
                        )
                );
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("Cannot find recipient: " + recipient + " for document: " + node);
                }
            }
        }
        return recipientsToExclude;
    }


    private static void addRecipient(Set<String> recipients, NodeRef recipientRef, NodeService nodeService,
                                                                                   DictionaryService dictionaryService) {

        String authorityName = RepoUtils.getAuthorityName(recipientRef, nodeService, dictionaryService);
        if (StringUtils.isNotBlank(authorityName)) {
            recipients.add(authorityName);
        }
    }

    private static NodeRef getICaseRoleOrNullNotFound(String roleName, List<NodeRef> iCaseRoles,
                                                      CaseRoleService caseRoleService) {
        for (NodeRef caseRole : iCaseRoles) {
            String foundRoleName = caseRoleService.getRoleId(caseRole);
            if (Objects.equals(foundRoleName, roleName)) {
                return caseRole;
            }
        }
        return null;
    }

}
