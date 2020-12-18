/*
 * Copyright (C) 2008-2015 Citeck LLC.
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

import lombok.extern.slf4j.Slf4j;
import org.activiti.engine.delegate.DelegateTask;
import org.activiti.engine.impl.persistence.entity.ExecutionEntity;
import org.activiti.engine.impl.persistence.entity.IdentityLinkEntity;
import org.activiti.engine.impl.persistence.entity.TaskEntity;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.jscript.ScriptNode;
import org.alfresco.repo.notification.EMailNotificationProvider;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.AlfrescoTransactionSupport;
import org.alfresco.repo.workflow.WorkflowQNameConverter;
import org.alfresco.repo.workflow.activiti.ActivitiScriptNode;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.notification.NotificationContext;
import org.alfresco.service.cmr.repository.AssociationRef;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.TemplateService;
import org.alfresco.service.cmr.security.AuthenticationService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.cmr.workflow.WorkflowInstance;
import org.alfresco.service.cmr.workflow.WorkflowTask;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import ru.citeck.ecos.model.DmsModel;
import ru.citeck.ecos.notification.task.record.TaskExecutionRecord;
import ru.citeck.ecos.notification.utils.RecipientsUtils;
import ru.citeck.ecos.notifications.lib.Notification;
import ru.citeck.ecos.notifications.lib.NotificationType;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.role.CaseRoleService;
import ru.citeck.ecos.security.NodeOwnerDAO;

import java.io.Serializable;
import java.util.*;

/**
 * Notification sender for tasks (ItemType = DelegateTask).
 * <p>
 * The following implementation is used:
 * - subject line: default
 * - template: retrieved by key = process-definition
 * - template args:
 * {
 * "task": {
 * "id": "task id",
 * "name": "task name",
 * "description": "task description",
 * "priority": "task priority",
 * "dueDate": "task dueDate",
 * }
 * },
 * "workflow": {
 * "id": "workflow id",
 * "documents": [
 * "nodeRef1",
 * ...
 * ]
 * }
 * }
 * - notification recipients - provided as parameters
 */
@Slf4j
class StartDelegateTaskNotificationSender extends AbstractNotificationSender<DelegateTask> {

    // template argument names:
    private static final String DOCS_INFO_KEY = StartDelegateTaskNotificationSender.class.getName() + ".docsInfo";
    public static final String ARG_TASK = "task";
    public static final String ARG_TASK_ID = "id";
    public static final String ARG_TASK_NAME = "name";
    public static final String ARG_TASK_DESCRIPTION = "description";
    public static final String ARG_TASK_EDITOR = "editor";
    public static final String ARG_TASK_PROPERTIES = "properties";
    public static final String ARG_TASK_PROPERTIES_PRIORITY = "bpm_priority";
    public static final String ARG_TASK_PROPERTIES_DESCRIPTION = "bpm_description";
    public static final String ARG_TASK_PROPERTIES_DUEDATE = "bpm_dueDate";
    public static final String ARG_WORKFLOW = "workflow";
    public static final String ARG_WORKFLOW_ID = "id";
    public static final String ARG_WORKFLOW_PROPERTIES = "properties";
    public static final String ARG_WORKFLOW_DOCUMENTS = "documents";
    private static final String ARG_RECIPIENTS_FROM_ROLE = "iCase_Role";
    protected WorkflowQNameConverter qNameConverter;
    protected PersonService personService;
    protected AuthenticationService authenticationService;
    protected AuthorityService authorityService;
    protected CaseRoleService caseRoleService;
    protected boolean sendToOwner;
    protected boolean mandatoryFieldsFilled = true;
    private NodeOwnerDAO nodeOwnerDAO;
    private Map<String, Boolean> checkAssignee;
    private Map<String, List<String>> additionRecipients;
    private Map<String, List<String>> supervisors;
    private Map<String, List<String>> mandatoryFields;
    private List<String> allowDocList;
    private Map<String, Map<String, String>> subjectTemplates;
    private TemplateService templateService;
    private String nodeVariable;
    private String templateEngine = "freemarker";

    @Override
    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        super.setServiceRegistry(serviceRegistry);
        this.qNameConverter = new WorkflowQNameConverter(namespaceService);
        this.authenticationService = serviceRegistry.getAuthenticationService();
        this.personService = serviceRegistry.getPersonService();
        this.authorityService = serviceRegistry.getAuthorityService();
    }

    /**
     * Recipients provided as parameter taskSubscribers: "task name"-{"doc type1"-"recepient field1", ...}
     *
     * @param taskSubscribers subscribers
     */
    @Deprecated
    public void setTaskSubscribers(Map<String, Map<String, List<String>>> taskSubscribers) {
        // not used
    }

    // get notification template arguments for the task
    @Override
    protected Map<String, Serializable> getNotificationArgs(DelegateTask task) {
        Map<String, Serializable> args = new HashMap<>();
        args.put(ARG_TASK, getTaskInfo(task));
        args.put(ARG_WORKFLOW, getWorkflowInfo(task, getDocsInfo()));
        return args;
    }

    @Override
    protected Map<String, Object> getEcosNotificationArgs(DelegateTask task) {
        Map<String, Object> args = super.getEcosNotificationArgs(task);

        TaskExecutionRecord executionRecord = executionsTaskService
            .getExecutionRecord(DelegateTask.class, task).orElse(null);
        if (executionRecord != null) {
            Map<String, Object> properties = executionRecord.getProperties();
            addWorkflowProperties(task, properties);
            mandatoryFieldsFill(task, properties);
        }
        args.put("_record", executionRecord);

        return args;
    }

    private Serializable getTaskInfo(DelegateTask task) {
        HashMap<String, Object> taskInfo = new HashMap<>();
        taskInfo.put(ARG_TASK_ID, task.getId());
        taskInfo.put(ARG_TASK_NAME, task.getName());
        taskInfo.put(ARG_TASK_DESCRIPTION, task.getDescription());
        HashMap<String, Serializable> properties = new HashMap<>();
        taskInfo.put(ARG_TASK_PROPERTIES, properties);
        ExecutionEntity executionEntity = ((ExecutionEntity) task.getExecution()).getProcessInstance();
        for (Map.Entry<String, Object> entry : executionEntity.getVariables().entrySet()) {
            if (entry.getValue() != null) {
                if (entry.getValue() instanceof ScriptNode) {
                    properties.put(entry.getKey(), (ScriptNode) entry.getValue());
                } else {
                    properties.put(entry.getKey(), entry.getValue().toString());
                }
            } else {
                properties.put(entry.getKey(), null);
            }
        }

        WorkflowTask wfTask = services.getWorkflowService().getTaskById("activiti$" + task.getId());
        if (wfTask != null && wfTask.getProperties() != null) {
            for (Map.Entry<QName, Serializable> entry : wfTask.getProperties().entrySet()) {
                properties.put(qNameConverter.mapQNameToName(entry.getKey()), entry.getValue());
            }
        }

        mandatoryFieldsFill(task, properties);

        String userName = authenticationService.getCurrentUserName();
        NodeRef person = personService.getPerson(userName);
        if (nodeService.exists(person)) {
            String lastName = (String) nodeService.getProperty(person, ContentModel.PROP_FIRSTNAME);
            String firstName = (String) nodeService.getProperty(person, ContentModel.PROP_LASTNAME);
            taskInfo.put(ARG_TASK_EDITOR, lastName + " " + firstName);
        }
        return taskInfo;
    }

    private void addWorkflowProperties(DelegateTask task, Map<String, Object> properties) {
        WorkflowTask wfTask = services.getWorkflowService().getTaskById("activiti$" + task.getId());
        if (wfTask != null && wfTask.getProperties() != null) {
            for (Map.Entry<QName, Serializable> entry : wfTask.getProperties().entrySet()) {
                properties.put(qNameConverter.mapQNameToName(entry.getKey()), entry.getValue());
            }
        }
    }

    private void mandatoryFieldsFill(DelegateTask task, Map<String, ?> properties) {
        if (mandatoryFields != null) {
            List<String> fields = mandatoryFields.get(task.getName());
            if (fields != null && fields.size() > 0) {
                for (String field : fields) {
                    if (field != null && properties.get(field) == null || "".equals(properties.get(field).toString().trim())) {
                        mandatoryFieldsFilled = false;
                    }
                }
            }
        }
    }

    private Serializable getWorkflowInfo(DelegateTask task, NodeRef docsInfo) {
        HashMap<String, Object> workflowInfo = new HashMap<>();
        WorkflowTask wfTask = services.getWorkflowService().getTaskById("activiti$" + task.getId());
        if (wfTask != null) {
            WorkflowInstance workflow = wfTask.getPath().getInstance();
            if (workflow != null) {
                workflowInfo.put(ARG_WORKFLOW_ID, workflow.getId());
            }
        }
        workflowInfo.put(ARG_WORKFLOW_DOCUMENTS, docsInfo);
        return workflowInfo;
    }

    /**
     * Method send notification about start task to notification recipients.
     * Mail sends to each document to subscriber because task can contains a lot of different documents
     * and these documents can contains different subscriber.
     *
     * @param task DelegateTask
     */
    @Override
    public void sendNotification(final DelegateTask task) {
        AuthenticationUtil.runAsSystem((AuthenticationUtil.RunAsWork<Void>) () -> {
            send(task);
            return null;
        });
    }

    private void send(DelegateTask task) {
        log.debug("Method send start...\ntask: " + task + "\ninstance: " + toString());
        Set<String> authorities = authorityService.getAuthorities();
        boolean sendBasedOnUser = true;

        if (checkAssignee != null && checkAssignee.containsKey(task.getName()) && checkAssignee.get(task.getName())) {
            sendBasedOnUser = isSendBasedOnUser(task, authorities);
        }
        NodeRef workflowPackage = getWorkflowPackage(task);

        log.debug("workflowPackage: " + workflowPackage + "\nsendBasedOnUser:" + sendBasedOnUser);
        if (workflowPackage == null || !sendBasedOnUser || !mandatoryFieldsFilled) {
            return;
        }

        calcDocsInfo(workflowPackage);
        NodeRef docsInfo = getDocsInfo();
        log.debug("docsInfo: " + docsInfo);
        if (docsInfo == null || !nodeService.exists(docsInfo)) {
            return;
        }

        log.debug("additionRecipients: " + additionRecipients);

        Set<String> recipients = getRecipients(task, docsInfo);
        NodeRef template = getNotificationTemplate(task);
        String notificationProviderName = EMailNotificationProvider.NAME;

        log.debug("template: " + template);
        if (template == null || !nodeService.exists(template)) {
            return;
        }

        String notifyTemplate = (String) nodeService.getProperty(template, DmsModel.PROP_ECOS_NOTIFICATION_TEMPLATE);

        List<String> recipient = new ArrayList<>(getRecipients(task, template, docsInfo));
        recipients.addAll(recipient);
        String subject = getSubject(task, template);
        recipients.add("admin");
        if (StringUtils.isNotBlank(notifyTemplate)) {
            send(task, recipients, notifyTemplate, subject);
        } else {
            sendDeprecated(task, recipients, template, notificationProviderName, subject);
        }
    }

    private void send(DelegateTask task, Set<String> recipients, String template, String subject) {
        Map<String, Object> additionalMeta = getEcosNotificationArgs(task);

        Object record = additionalMeta.get("_record");
        additionalMeta.remove("_record");
        additionalMeta.put("subject", subject);

        Notification notification = new Notification.Builder()
            .record(record)
            .templateRef(RecordRef.valueOf(template))
            .notificationType(NotificationType.EMAIL_NOTIFICATION)
            .recipients(getEmailFromAuthorityNames(recipients))
            .additionalMeta(additionalMeta)
            .build();

        notificationService.send(notification);
    }

    private void sendDeprecated(DelegateTask task, Set<String> recipients, NodeRef template,
                                String notificationProviderName, String subject) {
        NotificationContext notificationContext = new NotificationContext();
        setBodyTemplate(notificationContext, template);
        notificationContext.setSubject(subject);
        notificationContext.setTemplateArgs(getNotificationArgs(task));
        notificationContext.setAsyncNotification(getAsyncNotification());

        log.debug("recipients:" + recipients);
        if (!recipients.isEmpty()) {
            for (String rec : recipients) {
                notificationContext.addTo(rec);
            }
        }

        services.getNotificationService().sendNotification(notificationProviderName, notificationContext);
    }

    @NotNull
    private Set<String> getRecipients(DelegateTask task, NodeRef docsInfo) {
        Set<String> recipients = new HashSet<>();
        if (additionRecipients != null) {
            List<String> addition = additionRecipients.get(task.getName());
            if (addition != null && addition.size() > 0) {
                recipients.addAll(addition);
            }

            List<String> recipientsFromRole = additionRecipients.get(ARG_RECIPIENTS_FROM_ROLE);
            if (recipientsFromRole != null && recipientsFromRole.size() > 0) {
                Set<String> roleRecipients = RecipientsUtils.getRecipientsFromRole(recipientsFromRole,
                    docsInfo, nodeService, dictionaryService, caseRoleService);
                if (!roleRecipients.isEmpty()) {
                    recipients.addAll(roleRecipients);
                }
            }
        }
        return recipients;
    }

    private void calcDocsInfo(NodeRef workflowPackage) {
        List<ChildAssociationRef> children = services.getNodeService().getChildAssocs(workflowPackage);
        for (ChildAssociationRef child : children) {
            NodeRef node = child.getChildRef();
            if (node != null && nodeService.exists(node)) {
                if (allowDocList == null) {
                    setDocsInfo(node);
                    break;
                } else {
                    if (allowDocList.contains(qNameConverter.mapQNameToName(nodeService.getType(node)))) {
                        setDocsInfo(node);
                        break;
                    }
                }
            }
        }
    }

    private NodeRef getWorkflowPackage(DelegateTask task) {
        ExecutionEntity executionEntity = ((ExecutionEntity) task.getExecution()).getProcessInstance();
        ActivitiScriptNode scriptNode = (ActivitiScriptNode) executionEntity.getVariable("bpm_package");
        return scriptNode != null ? scriptNode.getNodeRef() : null;
    }

    private boolean isSendBasedOnUser(DelegateTask task, Set<String> authorities) {
        boolean sendBasedOnUser;
        sendBasedOnUser = false;
        if (authorities != null && authorities.size() > 0) {
            if (supervisors != null && supervisors.size() > 0) {
                List<String> taskSupervisors = supervisors.get(task.getName());
                if (taskSupervisors != null && taskSupervisors.size() > 0) {
                    for (String supervisor : taskSupervisors) {
                        if (authorities.contains(supervisor)) {
                            sendBasedOnUser = true;
                            break;
                        }
                    }
                }
            }
        }
        return sendBasedOnUser;
    }

    private String getSubject(DelegateTask task, NodeRef template) {
        String subject;
        subject = (String) nodeService.getProperty(template, ContentModel.PROP_TITLE);
        if (subject == null) {
            String taskFormKey = (String) task.getVariableLocal("taskFormKey");
            if (subjectTemplates != null && subjectTemplates.containsKey(taskFormKey)) {
                Map<String, String> taskSubjectTemplate = subjectTemplates.get(taskFormKey);
                if (taskSubjectTemplate.containsKey(qNameConverter.mapQNameToName(nodeService.getType(getDocsInfo())))) {
                    HashMap<String, Object> model = new HashMap<>(1);
                    model.put(nodeVariable, getDocsInfo());
                    subject = templateService.processTemplateString(templateEngine,
                            taskSubjectTemplate.get(qNameConverter.mapQNameToName(nodeService.getType(getDocsInfo()))),
                            model);
                }
            }
            if (subject == null) {
                subject = task.getName();
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("getSubject"
                    + "\ntask: " + task
                    + "\ntemplate: " + template
                    + "\ndocsInfo: " + getDocsInfo()
                    + "\nsubject: " + subject);
        }

        return subject;
    }

    public NodeRef getNotificationTemplate(DelegateTask task) {
        String processDef = task.getProcessDefinitionId();
        String wfkey = "activiti$" + processDef.substring(0, processDef.indexOf(':'));
        String tkey = (String) task.getVariableLocal("taskFormKey");
        String etype = getEtype();

        return getNotificationTemplateWithEtype(wfkey, tkey, etype);
    }

    private String getEtype() {
        NodeRef docsInfo = getDocsInfo();
        return getFirstEtypeFromNodeRefs(Collections.singletonList(docsInfo));
    }

    /**
     * Include initiator of process to recipients
     *
     * @param sendToOwner true or false
     */
    public void setSendToOwner(Boolean sendToOwner) {
        this.sendToOwner = sendToOwner;
    }

    public void setNodeOwnerDAO(NodeOwnerDAO nodeOwnerDAO) {
        this.nodeOwnerDAO = nodeOwnerDAO;
    }

    @Deprecated
    public void setTaskProperties(Map<String, Map<String, String>> taskProperties) {
        // not used
    }

    public void setAdditionRecipients(Map<String, List<String>> additionRecipients) {
        this.additionRecipients = additionRecipients;
    }

    public void setSupervisors(Map<String, List<String>> supervisors) {
        this.supervisors = supervisors;
    }

    public void setMandatoryFields(Map<String, List<String>> mandatoryFields) {
        this.mandatoryFields = mandatoryFields;
    }

    public void setCheckAssignee(Map<String, Boolean> checkAssignee) {
        this.checkAssignee = checkAssignee;
    }

    protected void sendToAssignee(DelegateTask task, Set<String> authorities) {
        authorities.addAll(getAssignee(task));
    }

    protected Set<String> getAssignee(DelegateTask task) {
        Set<String> authorities = new HashSet<>();
        if (task.getAssignee() == null) {
            List<IdentityLinkEntity> identities = ((TaskEntity) task).getIdentityLinks();
            for (IdentityLinkEntity item : identities) {
                String group = item.getGroupId();
                if (group != null) {
                    authorities.add(group);
                }
                String user = item.getUserId();
                if (user != null) {
                    authorities.add(user);
                }
            }
        } else {
            authorities.add(task.getAssignee());
        }
        return authorities;
    }

    protected void sendToInitiator(DelegateTask task, Set<String> authorities) {
        authorities.add(getInitiator(task));
    }

    protected String getInitiator(DelegateTask task) {
        ExecutionEntity executionEntity = ((ExecutionEntity) task.getExecution()).getProcessInstance();
        NodeRef initiator = ((ActivitiScriptNode) executionEntity.getVariable("initiator")).getNodeRef();
        return (String) nodeService.getProperty(initiator, ContentModel.PROP_USERNAME);
    }

    protected void sendToOwner(Set<String> authorities, NodeRef node) {
        String owner = nodeOwnerDAO.getOwner(node);
        authorities.add(owner);
    }

    public void setAllowDocList(List<String> allowDocList) {
        this.allowDocList = allowDocList;
    }

    protected void sendToSubscribers(DelegateTask task, Set<String> authorities, List<String> taskSubscribers) {
        for (String subscriber : taskSubscribers) {
            if (StringUtils.isBlank(subscriber)) {
                continue;
            }
            QName sub = qNameConverter.mapNameToQName(subscriber);
            NodeRef workflowPackage = null;
            ExecutionEntity executionEntity = ((ExecutionEntity) task.getExecution()).getProcessInstance();
            ActivitiScriptNode scriptNode = (ActivitiScriptNode) executionEntity.getVariable("bpm_package");
            if (scriptNode != null) {
                workflowPackage = scriptNode.getNodeRef();
            }
            if (workflowPackage != null) {
                List<ChildAssociationRef> children = nodeService.getChildAssocs(workflowPackage);
                for (ChildAssociationRef child : children) {
                    NodeRef node = child.getChildRef();
                    Collection<AssociationRef> assocs = nodeService.getTargetAssocs(node, sub);
                    for (AssociationRef assoc : assocs) {
                        NodeRef ref = assoc.getTargetRef();
                        if (nodeService.exists(ref)) {
                            String subName = (String) nodeService.getProperty(ref, ContentModel.PROP_USERNAME);
                            authorities.add(subName);
                        }
                    }
                }
            }
        }
    }

    public void setSubjectTemplates(Map<String, Map<String, String>> subjectTemplates) {
        this.subjectTemplates = subjectTemplates;
    }

    public void setTemplateService(TemplateService templateService) {
        this.templateService = templateService;
    }

    public void setTemplateEngine(String templateEngine) {
        this.templateEngine = templateEngine;
    }

    public void setNodeVariable(String nodeVariable) {
        this.nodeVariable = nodeVariable;
    }

    private void setDocsInfo(NodeRef docsInfo) {
        AlfrescoTransactionSupport.bindResource(DOCS_INFO_KEY, docsInfo);
    }

    private NodeRef getDocsInfo() {
        return AlfrescoTransactionSupport.getResource(DOCS_INFO_KEY);
    }

    @Autowired
    public void setCaseRoleService(CaseRoleService caseRoleService) {
        this.caseRoleService = caseRoleService;
    }
}
