package ru.citeck.ecos.notification;


import org.alfresco.service.cmr.repository.*;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.collections.CollectionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.extensions.surf.util.I18NUtil;
import ru.citeck.ecos.icase.CaseStatusService;
import ru.citeck.ecos.notification.utils.RecipientsUtils;

import java.io.Serializable;
import java.util.*;

/**
 * @author Roman Makarskiy
 */
public class ICaseDocumentNotificationSender extends DocumentNotificationSender {

    protected NodeService nodeService;
    private NamespaceService namespaceService;
    private TemplateService templateService;
    private CaseStatusService caseStatusService;

    private String nodeVariable;
    private String templateEngine;
    private String subjectTemplate;
    private String notificationType;
    private NodeRef targetRef;
    private Map<String, List<String>> recipients;
    private Map<String, List<String>> iCaseAspectConditions;
    private List<String> excludeStatuses;
    private List<String> allowedStatuses;
    private Locale priorityLocale;

    private static final String ARG_TARGET_REF = "targetRef";
    private static final String ARG_NOTIFICATION_TYPE = "notificationType";
    private static final String ASSOC_RECIPIENTS_KEY = "assocRecipients";
    private static final String RECIPIENTS_FROM_ROLE_KEY = "recipientsFromRole";
    private static final String EXCLUDE_RECIPIENTS = "excludeRecipients";
    private static final String INCLUDE_KEY = "include";
    private static final String EXCLUDE_KEY = "exclude";

    private static Log logger = LogFactory.getLog(ICaseDocumentNotificationSender.class);

    @Override
    protected Collection<String> getNotificationRecipients(NodeRef item) {
        List<QName> assocRecipients = convertStringToQNameList(recipients.get(ASSOC_RECIPIENTS_KEY));
        List<String> assocRecipientsFromICaseRole = recipients.get(RECIPIENTS_FROM_ROLE_KEY);
        List<String> excludeRecipient = recipients.get(EXCLUDE_RECIPIENTS);
        Set<String> finalRecipients = new HashSet<>(super.getNotificationRecipients(item));

        if (assocRecipients != null) {
            Set<String> assocRecipient = RecipientsUtils.getRecipientFromNodeAssoc(assocRecipients, item,
                                                                                   nodeService, dictionaryService);
            if (!assocRecipient.isEmpty()) {
                finalRecipients.addAll(assocRecipient);
            }
        }

        if (assocRecipientsFromICaseRole != null) {
            Set<String> roleRecipients = RecipientsUtils.getRecipientsFromRole(assocRecipientsFromICaseRole, item,
                                                                               nodeService, dictionaryService);
            if (!roleRecipients.isEmpty()) {
                finalRecipients.addAll(roleRecipients);
            }
        }

        if (excludeRecipient != null) {
            Set<String> excludeRecipients = RecipientsUtils.getRecipientsToExclude(excludeRecipient, item, services);
            if (!excludeRecipients.isEmpty()) {
                finalRecipients.removeAll(excludeRecipients);
            }
        }

        return finalRecipients;
    }

    @Override
    protected String getNotificationSubject(NodeRef item) {
        String subject = "";
        if (subjectTemplate != null) {
            HashMap<String, Object> model = new HashMap<>(1);
            model.put(nodeVariable, item);
            subject = templateService.processTemplateString(templateEngine, subjectTemplate, model);
        } else {
            subject = this.subject;
        }
        return subject;
    }

    public void sendNotification(NodeRef sourceRef, NodeRef targetRef, Map<String, List<String>> recipients,
                                 String notificationType, String subjectTemplate, boolean afterCommit) {
        if (!aspectConditionIsFulfilled(sourceRef) || !isStatusAllowed(sourceRef)) {
            return;
        }

        if (priorityLocale != null) {
            I18NUtil.setLocale(priorityLocale);
        }

        this.targetRef = targetRef;
        this.recipients = recipients;
        this.notificationType = notificationType;
        this.subjectTemplate = subjectTemplate;
        super.sendNotification(sourceRef, afterCommit);

        if (logger.isDebugEnabled()) {
            logger.debug("\nSend notification - "
                    + "\nsource nodeRef: " + sourceRef
                    + "\ntarget nodeRef: " + targetRef
                    + "\nrecipients: " + recipients
                    + "\nnotification type: " + notificationType
                    + "\nsubject template: " + subjectTemplate
            );
        }
    }

    public void sendNotification(NodeRef sourceRef, NodeRef targetRef, Map<String, List<String>> recipients,
                                 String notificationType, String subjectTemplate) {
        this.sendNotification(sourceRef, targetRef, recipients, notificationType, subjectTemplate, false);
    }

    @Override
    protected Map<String, Serializable> getNotificationArgs(NodeRef item) {
        Map<String, Serializable> args = super.getNotificationArgs(item);
        if (targetRef != null) {
            args.put(ARG_TARGET_REF, targetRef);
        }
        args.put(ARG_NOTIFICATION_TYPE, notificationType);
        return args;
    }

    private List<QName> convertStringToQNameList(List<String> stringList) {
        List<QName> qNameList = new ArrayList<>();
        if (stringList != null) {
            for (String item : stringList) {
                qNameList.add(QName.resolveToQName(namespaceService, item));
            }
        }
        return qNameList;
    }

    private boolean aspectConditionIsFulfilled(NodeRef iCase) {
        if (iCaseAspectConditions == null || iCaseAspectConditions.isEmpty()) {
            return true;
        }
        if (!nodeService.exists(iCase)) {
            logger.error("Cannot check aspect condition, because node: " + iCase + " doesn't exists");
            return false;
        }

        List<QName> includeAspects = convertStringToQNameList(iCaseAspectConditions.get(INCLUDE_KEY));
        List<QName> excludeAspects = convertStringToQNameList(iCaseAspectConditions.get(EXCLUDE_KEY));

        if (includeAspects != null && !includeAspects.isEmpty()) {
            for (QName aspect : includeAspects) {
                if (!nodeService.hasAspect(iCase, aspect)) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Aspect condition failed. iCase: " + iCase + " don`t have aspect: " + aspect);
                    }
                    return false;
                }

            }
        }

        if (excludeAspects != null && !excludeAspects.isEmpty()) {
            for (QName aspect : excludeAspects) {
                if (nodeService.hasAspect(iCase, aspect)) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Aspect condition failed. iCase: " + iCase + " have aspect: " + aspect);
                    }
                    return false;
                }
            }
        }

        return true;
    }

    private boolean isStatusAllowed(NodeRef iCase) {
        if (!nodeService.exists(iCase)) {
            logger.error("Cannot check status, because node: " + iCase + " doesn't exists");
            return false;
        }

        String status = caseStatusService.getStatus(iCase);
        boolean allowed = allowedStatuses == null || allowedStatuses.contains(status);
        boolean excluded = excludeStatuses != null && excludeStatuses.contains(status);

        return allowed && !excluded;
    }

    public void setNodeVariable(String nodeVariable) {
        this.nodeVariable = nodeVariable;
    }

    public void setTemplateEngine(String templateEngine) {
        this.templateEngine = templateEngine;
    }

    public void setTemplateService(TemplateService templateService) {
        this.templateService = templateService;
    }

    public void setRecipients(Map<String, List<String>> recipients) {
        this.recipients = recipients;
    }

    public void setNamespaceService(NamespaceService namespaceService) {
        this.namespaceService = namespaceService;
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setiCaseAspectConditions(Map<String, List<String>> iCaseAspectConditions) {
        this.iCaseAspectConditions = iCaseAspectConditions;
    }

    public void setExcludeStatuses(List<String> excludeStatuses) {
        this.excludeStatuses = excludeStatuses;
    }

    public void setAllowedStatuses(List<String> allowedStatuses) {
        this.allowedStatuses = allowedStatuses;
    }

    public void setCaseStatusService(CaseStatusService caseStatusService) {
        this.caseStatusService = caseStatusService;
    }

    public void setPriorityLocale(Locale priorityLocale) {
        this.priorityLocale = priorityLocale;
    }
}
