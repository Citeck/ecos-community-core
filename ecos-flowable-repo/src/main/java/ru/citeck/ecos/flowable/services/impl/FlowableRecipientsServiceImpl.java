package ru.citeck.ecos.flowable.services.impl;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.AuthorityType;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import ru.citeck.ecos.flowable.services.FlowableRecipientsService;
import ru.citeck.ecos.model.lib.role.service.RoleService;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.role.CaseRoleService;
import ru.citeck.ecos.utils.AuthorityUtils;
import ru.citeck.ecos.utils.NodeUtils;
import ru.citeck.ecos.utils.RepoUtils;

import javax.xml.soap.Node;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Roman Makarskiy
 */
@Slf4j
public class FlowableRecipientsServiceImpl implements FlowableRecipientsService {

    @Autowired
    private CaseRoleService caseRoleService;
    @Autowired
    private AuthorityService authorityService;
    @Autowired
    private DictionaryService dictionaryService;
    @Autowired
    private PersonService personService;
    @Autowired
    private AuthorityUtils authorityUtils;
    @Autowired
    protected NodeService nodeService;
    @Autowired
    protected RoleService roleService;

    @Override
    public String getRoleEmails(NodeRef document, String caseRoleName) {
        if (document == null || !nodeService.exists(document)) {
            throw new IllegalArgumentException("Document does not exist: " + document);
        }

        if (StringUtils.isBlank(caseRoleName)) {
            throw new IllegalArgumentException("CaseRoleName must be specified");
        }

        Set<NodeRef> assignees = caseRoleService.getAssignees(document, caseRoleName);
        return getEmailsFromAuthorities(assignees);
    }

    @Override
    public String getRoleEmails(RecordRef document, String caseRoleName) {
        if (document.getId().startsWith(NodeUtils.WORKSPACE_PREFIX)) {
            return getRoleEmails(new NodeRef(document.getId()), caseRoleName);
        }
        return getEmailsFromAuthorities(getAssigneesRefs(document, caseRoleName));
    }

    @Override
    public String getAuthorityEmails(String authority) {
        NodeRef nodeRef = authorityUtils.getNodeRef(authority);
        return getEmailsFromAuthorities(Collections.singleton(nodeRef));
    }


    private String getEmailsFromAuthorities(Set<NodeRef> authorityRefs) {
        Set<String> emails = new HashSet<>();

        for (NodeRef ref : authorityRefs) {
            if (!nodeService.exists(ref)) {
                continue;
            }

            QName type = nodeService.getType(ref);
            if (dictionaryService.isSubClass(type, ContentModel.TYPE_PERSON)) {
                String email = RepoUtils.getProperty(ref, ContentModel.PROP_EMAIL, nodeService);
                if (StringUtils.isNotBlank(email)) {
                    emails.add(email);
                }
            } else if (dictionaryService.isSubClass(type, ContentModel.TYPE_AUTHORITY_CONTAINER)) {
                String groupName = (String) nodeService.getProperty(ref, ContentModel.PROP_AUTHORITY_NAME);
                Set<String> authorities = authorityService.getContainedAuthorities(AuthorityType.USER, groupName, false);
                for (String authority : authorities) {
                    NodeRef authorityRef = authorityService.getAuthorityNodeRef(authority);
                    String email = RepoUtils.getProperty(authorityRef, ContentModel.PROP_EMAIL, nodeService);
                    if (StringUtils.isNotBlank(email)) {
                        emails.add(email);
                    }
                }
            }
        }

        if (!emails.isEmpty()) {
            return String.join(EMAIL_SEPARATOR, emails);
        } else {
            return "";
        }
    }

    @Override
    public String getUserEmail(String username) {
        NodeRef person = personService.getPerson(username);
        return RepoUtils.getProperty(person, ContentModel.PROP_EMAIL, nodeService);
    }

    @Override
    public Set<String> getRoleGroups(NodeRef document, String caseRoleName) {
        return getRoleRecipients(document, caseRoleName, ContentModel.TYPE_AUTHORITY_CONTAINER,
                ContentModel.PROP_AUTHORITY_NAME);
    }

    @Override
    public Set<String> getRoleGroups(RecordRef document, String caseRoleName) {
        return getRoleRecipients(document, caseRoleName, ContentModel.TYPE_AUTHORITY_CONTAINER,
            ContentModel.PROP_AUTHORITY_NAME);
    }

    @Override
    public Set<String> getRoleUsers(NodeRef document, String caseRoleName) {
        return getRoleRecipients(
            document,
            caseRoleName,
            ContentModel.TYPE_PERSON,
            ContentModel.PROP_USERNAME
        );
    }

    @Override
    public Set<String> getRoleUsers(RecordRef document, String caseRoleName) {
        return getRoleRecipients(
            document,
            caseRoleName,
            ContentModel.TYPE_PERSON,
            ContentModel.PROP_USERNAME
        );
    }

    private Set<String> getRoleRecipients(RecordRef document,
                                          String caseRoleName,
                                          QName recipientType,
                                          QName recipientNameProp) {

        if (RecordRef.isEmpty(document)) {
            throw new IllegalArgumentException("Document does not exist: " + document);
        }
        if (StringUtils.isBlank(caseRoleName)) {
            throw new IllegalArgumentException("CaseRoleName must be specified");
        }
        if (log.isDebugEnabled()) {
            log.debug("Getting role recipients, document: " + document + ", caseRoleName: " + caseRoleName);
        }

        if (document.getId().startsWith(NodeUtils.WORKSPACE_PREFIX)) {
            return getRoleRecipients(new NodeRef(document.getId()), caseRoleName, recipientType, recipientNameProp);
        }

        Set<NodeRef> assignees = getAssigneesRefs(document, caseRoleName);
        Set<String> recipients = filterAndGetRecipients(assignees, recipientType, recipientNameProp);

        if (log.isDebugEnabled()) {
            log.debug("Return recipients: " + recipients);
        }

        return recipients;
    }

    private Set<String> getRoleRecipients(NodeRef document,
                                          String caseRoleName,
                                          QName recipientType,
                                          QName recipientNameProp) {

        if (document == null || !nodeService.exists(document)) {
            throw new IllegalArgumentException("Document does not exist: " + document);
        }

        if (StringUtils.isBlank(caseRoleName)) {
            throw new IllegalArgumentException("CaseRoleName must be specified");
        }

        if (log.isDebugEnabled()) {
            log.debug("Getting role recipients, document: " + document + ", caseRoleName: " + caseRoleName);
        }

        Set<NodeRef> assignees = caseRoleService.getAssignees(document, caseRoleName);
        Set<String> recipients = filterAndGetRecipients(assignees, recipientType, recipientNameProp);

        if (log.isDebugEnabled()) {
            log.debug("Return recipients: " + recipients);
        }

        return recipients;
    }

    private Set<String> filterAndGetRecipients(Set<NodeRef> assignees,
                                               QName recipientType,
                                               QName recipientNameProp) {

        Set<String> recipients = new HashSet<>();

        for (NodeRef assignee : assignees) {
            if (nodeService.exists(assignee)) {
                QName type = nodeService.getType(assignee);
                if (dictionaryService.isSubClass(type, recipientType)) {
                    String name = RepoUtils.getProperty(assignee, recipientNameProp, nodeService);
                    recipients.add(name);
                }
            }
        }
        return recipients;
    }

    private Set<NodeRef> getAssigneesRefs(RecordRef document, String roleName) {
        return roleService.getAssignees(document, roleName)
            .stream()
            .map(authorityUtils::getNodeRef)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }
}
