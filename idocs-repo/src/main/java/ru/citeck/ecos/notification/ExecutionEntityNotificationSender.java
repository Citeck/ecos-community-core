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

import org.activiti.engine.impl.persistence.entity.ExecutionEntity;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.notification.EMailNotificationProvider;
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
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang3.StringUtils;
import ru.citeck.ecos.model.DmsModel;
import ru.citeck.ecos.notification.task.record.TaskExecutionRecord;
import ru.citeck.ecos.notifications.lib.Notification;
import ru.citeck.ecos.notifications.lib.NotificationType;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.security.NodeOwnerDAO;

import java.io.Serializable;
import java.util.*;

/**
 * Notification sender for tasks (ItemType = ExecutionEntity).
 *
 * The following implementation is used:
 * - subject line: default
 * - template: retrieved by key = process-definition
 * - template args:
 *   {
 *     "task": {
 *       "id": "task id",
 *       "name": "task name",
 *       "description": "task description",
 *       "priority": "task priority",
 *       "dueDate": "task dueDate",
 *       }
 *     },
 *     "workflow": {
 *       "id": "workflow id",
 *       "documents": [
 *         "nodeRef1",
 *         ...
 *       ]
 *     }
 *   }
 * - notification recipients - provided as parameters
 */
class ExecutionEntityNotificationSender extends AbstractNotificationSender<ExecutionEntity> {

    private static final String DOCS_INFO_KEY = ExecutionEntityNotificationSender.class.getName() + ".docsInfo";

    // template argument names:
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
    protected WorkflowQNameConverter qNameConverter;
    protected PersonService personService;
    protected AuthenticationService authenticationService;
    protected boolean sendToOwner;
    private NodeOwnerDAO nodeOwnerDAO;
    public static final String ARG_MODIFIER = "modifier";
    List<String> allowDocList;
    Map<String, Map<String,String>> subjectTemplates;
    private TemplateService templateService;
    private String nodeVariable;
    private String templateEngine;

    @Override
    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        super.setServiceRegistry(serviceRegistry);
        this.qNameConverter = new WorkflowQNameConverter(namespaceService);
        this.authenticationService = serviceRegistry.getAuthenticationService();
        this.personService = serviceRegistry.getPersonService();
    }

    /**
    * Recipients provided as parameter taskSubscribers: "task name"-{"doc type1"-"recepient field1", ...}
    * @param task subscribers
    */
    @Deprecated
    public void setTaskSubscribers(Map<String, Map<String,List<String>>> taskSubscribers) {
        // not used
    }

  // get notification template arguments for the task
    protected Map<String, Serializable> getNotificationArgs(ExecutionEntity task) {
        Map<String, Serializable> args = new HashMap<>();
        args.put(ARG_WORKFLOW, getWorkflowInfo(task));
        String userName = authenticationService.getCurrentUserName();
        NodeRef person = personService.getPerson(userName);
        String lastName = (String)nodeService.getProperty(person,ContentModel.PROP_FIRSTNAME);
        String firstName = (String)nodeService.getProperty(person,ContentModel.PROP_LASTNAME);
        args.put(ARG_MODIFIER, lastName +" "+ firstName);
        return args;
    }

    private Serializable getWorkflowInfo(ExecutionEntity task) {
        HashMap<String, Object> workflowInfo = new HashMap<>();
        workflowInfo.put(ARG_WORKFLOW_ID, task.getId());
        HashMap<String, Serializable> properties = new HashMap<>();
        workflowInfo.put(ARG_WORKFLOW_PROPERTIES, properties);
        for (Map.Entry<String, Object> entry : task.getVariables().entrySet()) {
            if (entry.getValue() != null) {
                properties.put(entry.getKey(), entry.getValue().toString());
            } else {
                properties.put(entry.getKey(), null);
            }
        }
        workflowInfo.put(ARG_WORKFLOW_DOCUMENTS, getDocsInfo());
        return workflowInfo;
    }

    /**
    * Method send notificatiion about start task to notification recipients.
    * Mail sends to each document to subscriber because task can containls a lot of different documents
    * and these documents can contains different subscriber.
    * @param Delegate Task
    */
    @Override
    public void sendNotification(ExecutionEntity task) {

        NodeRef workflowPackage = null;
        ActivitiScriptNode scriptNode = (ActivitiScriptNode) task.getVariable("bpm_package");
        if (scriptNode != null) {
            workflowPackage = scriptNode.getNodeRef();
        }
        if (workflowPackage != null && nodeService.exists(workflowPackage)) {
            send(task, workflowPackage);
        }

    }

    private void send(ExecutionEntity task, NodeRef workflowPackage) {
        setWorkflowDocs(workflowPackage);
        NodeRef docsInfo = getDocsInfo();
        if (docsInfo == null || !nodeService.exists(docsInfo)) {
            return;
        }

        NodeRef template = getNotificationTemplate(task);
        if (template == null || !nodeService.exists(template)) {
            return;
        }

        String notificationTemplate = (String) nodeService.getProperty(template, DmsModel.PROP_ECOS_NOTIFICATION_TEMPLATE);

        String subject = getSubject(task, docsInfo, template);
        Set<String> recipients = getRecipients(task, template, docsInfo);

        if (StringUtils.isNotBlank(notificationTemplate)) {
            send(task, notificationTemplate, subject, recipients);
        } else {
            sendDeprecated(task, template, subject, recipients);
        }
    }

    private void send(ExecutionEntity task, String template, String subject, Set<String> recipients) {
        Map<String, Object> additionalMeta = super.getEcosNotificationArgs(task);
        additionalMeta.put("subject", subject);

        RecordRef templateRef = RecordRef.valueOf(template);
        TaskExecutionRecord taskExecutionRecord = executionsTaskService
            .getExecutionRecord(ExecutionEntity.class, task)
            .orElse(null);

        Notification notification = new Notification.Builder()
            .record(taskExecutionRecord)
            .templateRef(templateRef)
            .notificationType(NotificationType.EMAIL_NOTIFICATION)
            .recipients(getEmailFromAuthorityNames(recipients))
            .additionalMeta(additionalMeta)
            .build();

        notificationService.send(notification);
    }

    private void sendDeprecated(ExecutionEntity task, NodeRef template, String subject, Set<String> recipients) {
        String notificationProviderName = EMailNotificationProvider.NAME;
        NotificationContext notificationContext = new NotificationContext();
        for (String to : recipients) {
            notificationContext.addTo(to);
        }
        notificationContext.setSubject(subject);
        setBodyTemplate(notificationContext, template);
        notificationContext.setTemplateArgs(getNotificationArgs(task));
        notificationContext.setAsyncNotification(getAsyncNotification());
        services.getNotificationService().sendNotification(notificationProviderName, notificationContext);
    }

    private String getSubject(ExecutionEntity task, NodeRef docsInfo, NodeRef template) {
        if (subjectTemplates == null) {
            return (String) nodeService.getProperty(template, ContentModel.PROP_TITLE);
        }

        String processDef = task.getProcessDefinitionId();
        String wfkey = "activiti$" + processDef.substring(0, processDef.indexOf(':'));
        if (!subjectTemplates.containsKey(wfkey)) {
            return (String) nodeService.getProperty(template, ContentModel.PROP_TITLE);
        }

        Map<String, String> taskSubjectTemplate = subjectTemplates.get(wfkey);
        if (taskSubjectTemplate.containsKey(qNameConverter.mapQNameToName(nodeService.getType(docsInfo)))) {
            HashMap<String, Object> model = new HashMap<>(1);
            model.put(nodeVariable, docsInfo);

            return templateService.processTemplateString(
                templateEngine,
                taskSubjectTemplate.get(qNameConverter.mapQNameToName(nodeService.getType(docsInfo))),
                model);
        }

        return null;
    }

    private void setWorkflowDocs(NodeRef workflowPackage) {
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

    public NodeRef getNotificationTemplate(ExecutionEntity task) {
        String processDef = task.getProcessDefinitionId();
        NodeRef docsInfo = getDocsInfo();
        String wfkey = "activiti$" + processDef.substring(0, processDef.indexOf(':'));
        String tkey = (String) task.getVariableLocal("taskFormKey");
        String etype = getEtype(docsInfo);

        return getNotificationTemplate(wfkey, tkey, nodeService.getType(docsInfo), etype);
    }

    private String getEtype(NodeRef node) {
        if (node == null) {
            return null;
        }

        return recordsService.getAtt(RecordRef.valueOf(node.toString()), "etype?id").asText();
    }

    /**
    * Include initiator of process to recipients
    * @param true or false
    */
    public void setSendToOwner(Boolean sendToOwner) {
        this.sendToOwner = sendToOwner;
    }

    public void setNodeOwnerDAO(NodeOwnerDAO nodeOwnerDAO) {
        this.nodeOwnerDAO = nodeOwnerDAO;
    }

    protected void sendToAssignee(ExecutionEntity task, Set<String> authorities) {
        // empty
    }

    protected void sendToInitiator(ExecutionEntity task, Set<String> authorities) {
        NodeRef initiator = ((ActivitiScriptNode) task.getVariable("initiator")).getNodeRef();
        String initiatorName = (String) nodeService.getProperty(initiator, ContentModel.PROP_USERNAME);
        authorities.add(initiatorName);
    }

    protected void sendToOwner(Set<String> authorities, NodeRef node) {
        String owner = nodeOwnerDAO.getOwner(node);
        authorities.add(owner);
    }

    public void setAllowDocList(List<String> allowDocList) {
        this.allowDocList = allowDocList;
    }


    protected void sendToSubscribers(ExecutionEntity task, Set<String> authorities, List<String> taskSubscribers) {
        for (String subscriber : taskSubscribers) {
            if (StringUtils.isBlank(subscriber)) {
                continue;
            }
            QName sub = qNameConverter.mapNameToQName(subscriber);
            NodeRef workflowPackage = null;
            ActivitiScriptNode scriptNode = (ActivitiScriptNode) task.getVariable("bpm_package");
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

    public void setTemplateService(TemplateService templateService) {
        this.templateService = templateService;
    }

    public void setTemplateEngine(String templateEngine) {
        this.templateEngine = templateEngine;
    }

    public void setSubjectTemplates(Map<String, Map<String,String>> subjectTemplates) {
        this.subjectTemplates = subjectTemplates;
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
}
