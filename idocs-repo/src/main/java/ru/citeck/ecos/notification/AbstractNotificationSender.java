/*
 * Copyright (C) 2008-2020 Citeck LLC.
 *
 * This file is part of Citeck EcoS
 *
 * Citeck EcoS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Citeck EcoS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Citeck EcoS. If not, see <http://www.gnu.org/licenses/>.
 */
package ru.citeck.ecos.notification;

import lombok.Setter;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.node.NodeUtils;
import org.alfresco.repo.notification.EMailNotificationProvider;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.security.authentication.AuthenticationUtil.RunAsWork;
import org.alfresco.repo.transaction.AlfrescoTransactionSupport;
import org.alfresco.repo.transaction.RetryingTransactionHelper;
import org.alfresco.repo.workflow.WorkflowQNameConverter;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.notification.NotificationContext;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.AuthorityType;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.transaction.TransactionService;
import org.alfresco.util.transaction.TransactionListenerAdapter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import ru.citeck.ecos.model.DmsModel;
import ru.citeck.ecos.notification.task.record.services.EcosExecutionsTaskService;
import ru.citeck.ecos.notifications.lib.Notification;
import ru.citeck.ecos.notifications.lib.NotificationType;
import ru.citeck.ecos.notifications.lib.service.NotificationService;
import ru.citeck.ecos.records.RecordsUtils;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.predicate.PredicateService;
import ru.citeck.ecos.records2.predicate.model.*;
import ru.citeck.ecos.records3.RecordsService;
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts;
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery;
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy;
import ru.citeck.ecos.search.ftsquery.FTSQuery;
import ru.citeck.ecos.utils.ReflectionUtils;
import ru.citeck.ecos.utils.RepoUtils;
import ru.citeck.ecos.utils.TransactionUtils;
import ru.citeck.ecos.utils.UrlUtils;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generic implementation of NotificationSender interface.
 * Concrete implementation should provide:
 * - template nodeRef (getNotificationTemplate method)
 * - template arguments (getNotificationArgs method)
 * <p>
 * Concrete implementations can provide:
 * - e-mail subject line (getNotificationSubject method) (if not, it is taken from 'subject' bean property or template 'cm:title' property)
 * - e-mail recipients (getNotificationRecipients method) (if not, it is taken from 'defaultRecipients' bean property)
 * <p>
 * Concrete implementations are provided with:
 * - getNotificationTemplate(key) method
 * - various protected properties
 * - various service references
 * <p>
 * Configuration should provide:
 * - templateRoot - xpath to folder, containing all notification templates
 * - defaultTemplate - name of default template
 * <p>
 * Configuration can provide:
 * - templates - map of key->templates
 * - subject - subject line
 *
 * @author Sergey Tiunov
 */
public abstract class AbstractNotificationSender<ItemType> implements NotificationSender<ItemType> {

    protected String from = null;
    private static final Log logger = LogFactory.getLog(AbstractNotificationSender.class);

    // subject of notification e-mail
    // take from template, if not set (null)
    protected String subject = null;

    // xpath to root folder of templates
    private String templateRoot;

    // templates of notification e-mail - by some key
    private Map<String, String> templates;

    // default template of notification e-mail
    private String defaultTemplate;

    // collection of default mail recipients
    protected Collection<String> defaultRecipients;

    // notification type
    private String notificationType;

    private boolean asyncNotification = true;

    // dependencies:
    protected ServiceRegistry services;
    protected NodeService nodeService;
    protected TransactionService transactionService;
    protected SearchService searchService;
    protected NamespaceService namespaceService;
    protected DictionaryService dictionaryService;
    protected AuthorityService authorityService;
    protected WorkflowQNameConverter qNameConverter;

    @Autowired @Setter
    protected RecordsService recordsService;

    @Autowired @Qualifier("ecosNotificationService") @Setter
    protected NotificationService notificationService;

    @Autowired @Setter
    protected EcosExecutionsTaskService executionsTaskService;

    @Autowired @Setter
    protected UrlUtils urlUtils;

    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        this.services = serviceRegistry;
        this.nodeService = services.getNodeService();
        this.searchService = services.getSearchService();
        this.namespaceService = services.getNamespaceService();
        this.dictionaryService = services.getDictionaryService();
        this.authorityService = services.getAuthorityService();
        this.qNameConverter = new WorkflowQNameConverter(namespaceService);
    }

    /**
     * Set notification e-mail subject.
     * Subject line is taken from template's cm:title property, if not set explicitly.
     *
     * @param subject
     */
    public void setSubject(String subject) {
        this.subject = subject;
    }

    /**
     * Set e-mail template root folder in xpath.
     *
     * @param templateRoot
     */
    public void setTemplateRoot(String templateRoot) {
        if (templateRoot.endsWith("/")) {
            this.templateRoot = templateRoot;
        } else {
            this.templateRoot = templateRoot + "/";
        }
    }

    /**
     * Set map of templates: workflow-definition-name -> template-file-name.
     *
     * @param templates
     */
    public void setTemplates(Map<String, String> templates) {
        this.templates = templates;
    }

    /**
     * Set default e-mail notification template.
     *
     * @param defaultTemplate
     */
    public void setDefaultTemplate(String defaultTemplate) {
        this.defaultTemplate = defaultTemplate;
    }

    /**
     * Set default recipients of e-mail
     *
     * @param defaultRecipients
     */
    public void setDefaultRecipients(Collection<String> defaultRecipients) {
        this.defaultRecipients = defaultRecipients;
    }

    public void setAsyncNotification(boolean asyncNotification) {
        this.asyncNotification = asyncNotification;
        logger.debug("setAsyncNotification_asyncNotification: " + asyncNotification + " instance = " + toString());
    }

    @Override
    public void sendNotification(ItemType item) {
        sendNotification(item, false);
    }

    public void sendNotification(final ItemType item, final boolean afterCommit) {
        NodeRef template = getNotificationTemplate(item);

        String notificationTemplate = null;
        String subject = getNotificationSubject(item);
        if (template != null) {
            if (subject == null) {
                subject = (String) nodeService.getProperty(template, ContentModel.PROP_TITLE);
            }
            notificationTemplate = (String) nodeService.getProperty(template, DmsModel.PROP_ECOS_NOTIFICATION_TEMPLATE);
        }

        String finalNotificationTemplate = notificationTemplate;
        String finalSubject = subject;
        AuthenticationUtil.runAsSystem((RunAsWork<Void>) () -> {
            if (StringUtils.isNotBlank(finalNotificationTemplate)) {
                sendEcosNotification(
                    getNotificationProviderName(item),
                    getNotificationFrom(item),
                    finalSubject,
                    finalNotificationTemplate,
                    getEcosNotificationArgs(item),
                    getNotificationRecipients(item),
                    afterCommit
                );
            } else {
                sendNotification(
                    getNotificationProviderName(item),
                    getNotificationFrom(item),
                    finalSubject,
                    template,
                    getNotificationArgs(item),
                    getNotificationRecipients(item),
                    afterCommit
                );
            }
            return null;
        });
    }

    protected boolean getAsyncNotification() {
        logger.debug("getAsyncNotification_asyncNotification: " + asyncNotification + " instance = " + toString());
        return asyncNotification;
    }

    /**
     * Get notification subject line for specified item.
     *
     * @param item
     * @return subject line
     */
    protected String getNotificationSubject(ItemType item) {
        return subject;
    }

    protected String getNotificationFrom(ItemType item) {
        return from;
    }

    protected String getNotificationProviderName(ItemType item) {
        return EMailNotificationProvider.NAME; //default email notification provider
    }

    /**
     * Get notification template nodeRef for specified item.
     * Concrete implementations can use getNotificationTemplate(key) method for this purposes.
     *
     * @param item
     * @return
     */
    protected abstract NodeRef getNotificationTemplate(ItemType item);

    /**
     * Get notification template arguments for specified item.
     * Set of item names is item-type specific.
     *
     * @param item
     * @return
     */
    protected abstract Map<String, Serializable> getNotificationArgs(ItemType item);

    /**
     * Get ECOS notification template arguments for specified item.
     * Arguments must be support in recordsService.
     * Argument '_record' will be used as main record in Ecos notification
     * Set of item names is item-type specific.
     *
     * @param item
     * @return
     */
    protected Map<String, Object> getEcosNotificationArgs(ItemType item){
        return new HashMap<>();
    };

    /**
     * Get collection of notification recipients.
     * Recipient can be user name or group full name (e.g. GROUP_ALFRESCO_ADMINISTRATORS).
     *
     * @param item
     * @return
     */
    protected Collection<String> getNotificationRecipients(ItemType item) {
        return defaultRecipients;
    }

    /**
     * Get notification template by key.
     * Utility method to get notification template.
     * Key is dependent on item type, so concrete implementations may calculate key and use this implemenation.
     *
     * @param key
     * @return
     */
    protected NodeRef getNotificationTemplate(String key) {
        String template = null;

        // try to look template by key
        if (this.templates != null) {
            template = this.templates.get(key);
        }

        // if not found - get default template
        if (template == null) {
            template = defaultTemplate;
        }

        // now get this template in repository:
        return getTemplateNodeRef(template);
    }

    protected NodeRef getNotificationTemplate(String wfkey, String tkey) {
        return getNotificationTemplate(wfkey, tkey, false);
    }

    protected NodeRef getNotificationTemplate(String wfkey, String tkey, boolean findNotSearchable) {
        return getNotificationTemplateWithEtype(wfkey, tkey, null, findNotSearchable);
    }

    protected NodeRef getNotificationTemplate(String wfkey, String tkey, QName docType) {
        return getNotificationTemplate(wfkey, tkey, docType,  false);
    }

    protected NodeRef getNotificationTemplate(String wfkey, String tkey, QName docType, String etype) {
        return getNotificationTemplate(wfkey, tkey, docType, etype, false);
    }

    protected NodeRef getNotificationTemplate(String wfkey, String tkey, QName docType, boolean findNotSearchable) {
        return getNotificationTemplate(wfkey, tkey, docType, null, findNotSearchable);
    }

    protected NodeRef getNotificationTemplateWithEtype(String wfkey, String tkey, String etype) {
        return getNotificationTemplateWithEtype(wfkey, tkey, etype, false);
    }

    protected NodeRef getNotificationTemplate(String wfkey, String tkey, QName docType, String etype, boolean findNotSearchable) {

        Map<QName, Serializable> fields = new HashMap<>();

        fields.put(DmsModel.PROP_WORKFLOW_NAME, wfkey);
        fields.put(DmsModel.PROP_TASK_NAME, tkey);
        fields.put(DmsModel.PROP_DOC_TYPE, docType);
        fields.put(DmsModel.PROP_ECOS_TYPE, etype);

        return getWFNotificationTemplate(fields, findNotSearchable).orElseGet(() ->
            getNotificationTemplateWithEtype(wfkey, tkey, etype, findNotSearchable)
        );
    }

    protected NodeRef getNotificationTemplateWithEtype(String wfkey, String tkey, String etype, boolean findNotSearchable) {

        Map<QName, Serializable> fields = new HashMap<>();

        fields.put(DmsModel.PROP_WORKFLOW_NAME, wfkey);
        fields.put(DmsModel.PROP_TASK_NAME, tkey);
        fields.put(DmsModel.PROP_ECOS_TYPE, etype);

        return getWFNotificationTemplate(fields, findNotSearchable).orElseGet(() ->
            getTemplateNodeRef(this.defaultTemplate)
        );
    }

    protected Optional<NodeRef> getWFNotificationTemplate(Map<QName, Serializable> props,
                                                          boolean findNotSearchable) {

        Map<QName, Serializable> propsTmp = new HashMap<>(props);
        String wfkey = (String) propsTmp.get(DmsModel.PROP_WORKFLOW_NAME);
        propsTmp.remove(DmsModel.PROP_WORKFLOW_NAME);
        String tkey = (String) propsTmp.get(DmsModel.PROP_TASK_NAME);
        propsTmp.remove(DmsModel.PROP_TASK_NAME);
        String etype = (String) propsTmp.get(DmsModel.PROP_ECOS_TYPE);
        propsTmp.remove(DmsModel.PROP_ECOS_TYPE);

        AndPredicate predicate = Predicates.and(
            Predicates.eq("TYPE", DmsModel.TYPE_NOTIFICATION_TEMPLATE.toString()),
            Predicates.eq(DmsModel.PROP_NOTIFICATION_TYPE.toString(), this.notificationType),
            Predicates.or(
                Predicates.empty(DmsModel.PROP_WORKFLOW_NAME.toString()),
                !StringUtils.isBlank(wfkey) ? Predicates.eq(DmsModel.PROP_WORKFLOW_NAME.toString(), wfkey) : null
            ),
            Predicates.or(
                Predicates.empty(DmsModel.PROP_TASK_NAME.toString()),
                !StringUtils.isBlank(tkey) ? Predicates.eq(DmsModel.PROP_TASK_NAME.toString(), tkey) : null
            ),
            Predicates.or(
                Predicates.empty(DmsModel.PROP_ECOS_TYPE.toString()),
                !StringUtils.isBlank(etype) ? Predicates.eq(DmsModel.PROP_ECOS_TYPE.toString(), etype) : null
            )
        );

        propsTmp.forEach((att, value) -> {
            boolean isNull = value == null || ((value instanceof String) && StringUtils.isBlank((String) value));

            predicate.addPredicate(isNull
                ? Predicates.eq(att.toString(), value)
                : Predicates.empty(att.toString()));
        });

        if (!findNotSearchable) {
            predicate.addPredicate(Predicates.not(Predicates.eq(DmsModel.PROP_NOT_SEARCHABLE.toString(), true)));
        }

        RecordsQuery query = RecordsQuery.create()
            .withLanguage(PredicateService.LANGUAGE_PREDICATE)
            .withQuery(predicate)
            .withSortBy(Arrays.asList(
                new SortBy(DmsModel.PROP_TASK_NAME.toString(), false),
                new SortBy(DmsModel.PROP_WORKFLOW_NAME.toString(), false),
                new SortBy(DmsModel.PROP_ECOS_TYPE.toString(), false)
            )).build();

        RecordRef recordRef = recordsService.queryOne(query);
        if (RecordRef.isEmpty(recordRef) || StringUtils.isBlank(recordRef.getId())) {
            return Optional.empty();
        }
        return Optional.ofNullable(getEnabledTemplate(RecordsUtils.toNodeRef(recordRef)));
    }

    private NodeRef getEnabledTemplate(NodeRef nodeRef) {
        if (!NodeUtils.exists(nodeRef, nodeService)) {
            return null;
        }
        Boolean isDisabled = (Boolean) nodeService.getProperty(nodeRef, DmsModel.PROP_NOTIFICATION_DISABLED);
        return Boolean.TRUE.equals(isDisabled) ? null : nodeRef;
    }

    protected NodeRef getTemplateNodeRef(String templatePath) {
        String xpath = this.templateRoot + templatePath;
        List<NodeRef> results = this.searchService.query(
                StoreRef.STORE_REF_WORKSPACE_SPACESSTORE,
                SearchService.LANGUAGE_XPATH,
                xpath).getNodeRefs();
        if (results.isEmpty()) {
            return null;
        }
        return getEnabledTemplate(results.get(0));
    }

    /**
     * Set notification type.
     *
     * @param notificationType
     */
    public void setNotificationType(String notificationType) {
        this.notificationType = notificationType;
    }

    // send notification
    // subject can be null, then subject is taken from template's cm:title property
    protected void sendNotification(final String notificationProviderName, String from, String subject,
                                    NodeRef template, Map<String, Serializable> args, Collection<String> recipients,
                                    boolean afterCommit) {

        if (template == null) {
            return;
        }

        // create notification context
        final NotificationContext notificationContext = new NotificationContext();
        notificationContext.setSubject(subject);
        setBodyTemplate(notificationContext, template);
        notificationContext.setTemplateArgs(args);
        for (String to : recipients) {
            notificationContext.addTo(to);
        }
        notificationContext.setAsyncNotification(false);
        logger.debug("sendNotification_asyncNotification: " + asyncNotification + " instance = " + toString());
        if (null != from) {
            notificationContext.setFrom(from);
        }

        sendNotificationContext(() -> {
            sendNotificationContext(notificationProviderName, notificationContext);
        }, afterCommit);
    }

    protected void sendEcosNotification(final String notificationProviderName, String from, String subject,
                                      String template, Map<String, Object> args, Collection<String> recipients,
                                      boolean afterCommit) {

        if (recipients == null || recipients.isEmpty()) {
            if (logger.isDebugEnabled()) {
                logger.debug("Skipped notification sending. Empty recipients list");
            }
            return;
        }

        Object record = args.get("_record");
        if (record == null) {
            logger.warn("Skipped notification sending. Unable to determine _record param");
            return;
        }

        args.remove("_record");

        args.put("subject", subject);

        Notification notification = new Notification.Builder()
                .record(record)
                .templateRef(RecordRef.valueOf(template))
                .notificationType(NotificationType.EMAIL_NOTIFICATION)
                .recipients(getEmailFromAuthorityNames(recipients))
                .additionalMeta(args)
                .from(from)
                .build();

        sendNotificationContext(() -> notificationService.send(notification), afterCommit);
    }

    private void sendNotificationContext(Runnable runnable, boolean afterCommit) {
        if (asyncNotification) {
            TransactionUtils.doAfterCommit(() -> {
                runnable.run();
            });
        } else if (afterCommit) {
            AlfrescoTransactionSupport.bindListener(new TransactionListenerAdapter() {
                @Override
                public void afterCommit() {
                    RetryingTransactionHelper helper = transactionService.getRetryingTransactionHelper();
                    helper.doInTransaction(() -> {
                        runnable.run();
                        return null;
                    }, false, true);
                }
            });
        } else {
            runnable.run();
        }
    }

    private void sendNotificationContext(final String notificationProviderName, final NotificationContext notificationContext) {
        AuthenticationUtil.runAsSystem(() -> {
            services.getNotificationService().sendNotification(
                    notificationProviderName,
                    notificationContext
            );
            return null;
        });
    }

    protected void setBodyTemplate(NotificationContext notificationContext,
                                   NodeRef template) {
        // NOTE: for compatibility with Alfresco Community 4.2.c
        ReflectionUtils.callSetterIfDeclared(notificationContext, "setBodyTemplate", template);
        ReflectionUtils.callSetterIfDeclared(notificationContext, "setBodyTemplate", template.toString());
    }

    public Set<String> getRecipients(ItemType task, NodeRef template, NodeRef document) {
        Set<String> authorities = new HashSet<>();
        Boolean sendToAssigneeProp = isSendToAssignee(template);
        if (Boolean.TRUE.equals(sendToAssigneeProp)) {
            sendToAssignee(task, authorities);
        }
        Boolean sendToInitiatorProp = isSendToInitiator(template);
        if (Boolean.TRUE.equals(sendToInitiatorProp)) {
            sendToInitiator(task, authorities);
        }
        Boolean sendToOwnerProp = (Boolean) nodeService.getProperty(template,
                qNameConverter.mapNameToQName("dms_sendToOwner"));
        if (Boolean.TRUE.equals(sendToOwnerProp)
                && document != null && nodeService.exists(document)) {
            sendToOwner(authorities, document);
        }
        ArrayList<String> taskSubscribers = (ArrayList<String>) nodeService.getProperty(template,
                qNameConverter.mapNameToQName("dms_taskSubscribers"));
        if (taskSubscribers != null && taskSubscribers.size() > 0) {
            sendToSubscribers(task, authorities, taskSubscribers);
        }
        String additionRecipientsStr = (String) nodeService.getProperty(template,
                qNameConverter.mapNameToQName("dms_additionRecipients"));
        if (StringUtils.isNoneBlank(additionRecipientsStr)) {
            String[] additionRecipientsArr = additionRecipientsStr.split(",");
            ArrayList<String> additionRecipients = new ArrayList<>(Arrays.asList(additionRecipientsArr));

            if (additionRecipients.size() > 0) {
                authorities.addAll(additionRecipients);
            }
        }
        return authorities;
    }

    protected Boolean isSendToInitiator(NodeRef template) {
        return (Boolean) nodeService.getProperty(template,
                qNameConverter.mapNameToQName("dms_sendToInitiator"));
    }

    protected Boolean isSendToAssignee(NodeRef template) {
        return (Boolean) nodeService.getProperty(template,
                qNameConverter.mapNameToQName("dms_sendToAssignee"));
    }

    protected String getFirstEtypeFromNodeRefs(List<NodeRef> nodeRefs) {
        List<RecordRef> recordRefs = nodeRefs.stream()
            .map(node -> RecordRef.valueOf(node.toString()))
            .collect(Collectors.toList());

        return getFirstEtypeFromRecordRefs(recordRefs);
    }

    protected String getFirstEtypeFromRecordRefs(List<RecordRef> recordRefs) {
        List<RecordAtts> result = recordsService.getAtts(recordRefs, Collections.singletonMap("type", "_etype?id"));
        for (RecordAtts att : result) {
            String type = att.getAtt("type").asText();
            if (StringUtils.isNotBlank(type)) {
                return type;
            }
        }

        return null;
    }

    protected Set<String> getEmailFromAuthorityNames(Collection<String> authorities) {
        Set<Serializable> serializableSet = authorities.stream().map(item -> (Serializable) item)
            .collect(Collectors.toSet());

        List<NodeRef> authorityRefs = FTSQuery.create()
            .open().type(ContentModel.TYPE_AUTHORITY).and()
            .any(ContentModel.PROP_USERNAME, serializableSet)
            .close()
            .or()
            .open().type(ContentModel.TYPE_AUTHORITY_CONTAINER).and()
            .any(ContentModel.PROP_AUTHORITY_NAME, serializableSet)
            .close()
            .transactional()
            .query(searchService);

        return getEmailFromAuthorityRefs(authorityRefs);
    }

    protected Set<String> getEmailFromAuthorityRefs(Collection<NodeRef> authorityRefs) {
        Set<String> result = new HashSet<>();
        for (NodeRef ref : authorityRefs) {
            if (!NodeUtils.exists(ref, nodeService)) {
                continue;
            }

            QName type = nodeService.getType(ref);
            if (dictionaryService.isSubClass(type, ContentModel.TYPE_PERSON)) {
                String email = RepoUtils.getProperty(ref, ContentModel.PROP_EMAIL, nodeService);
                if (StringUtils.isNotBlank(email)) {
                    result.add(email);
                }
            } else if (dictionaryService.isSubClass(type, ContentModel.TYPE_AUTHORITY_CONTAINER)) {
                String groupName = (String) nodeService.getProperty(ref, ContentModel.PROP_AUTHORITY_NAME);
                Set<String> authorities = authorityService.getContainedAuthorities(AuthorityType.USER, groupName, false);
                for (String authority : authorities) {
                    NodeRef authorityRef = authorityService.getAuthorityNodeRef(authority);
                    String email = RepoUtils.getProperty(authorityRef, ContentModel.PROP_EMAIL, nodeService);
                    if (StringUtils.isNotBlank(email)) {
                        result.add(email);
                    }
                }
            }
        }

        return result;
    }

    protected abstract void sendToAssignee(ItemType task, Set<String> authorities);

    protected abstract void sendToInitiator(ItemType task, Set<String> authorities);

    protected abstract void sendToSubscribers(ItemType task, Set<String> authorities, List<String> taskSubscribers);

    protected abstract void sendToOwner(Set<String> authorities, NodeRef node);

    public void setTransactionService(TransactionService transactionService) {
        this.transactionService = transactionService;
    }
}
