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
import org.alfresco.repo.workflow.WorkflowModel;
import org.alfresco.repo.workflow.activiti.ActivitiScriptNode;
import org.alfresco.service.cmr.notification.NotificationContext;
import org.alfresco.service.cmr.repository.*;
import org.alfresco.service.cmr.search.ResultSet;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.namespace.RegexQNamePattern;
import org.apache.commons.lang3.StringUtils;
import ru.citeck.ecos.model.DmsModel;
import ru.citeck.ecos.notification.task.record.TaskExecutionRecord;
import ru.citeck.ecos.notifications.lib.Notification;
import ru.citeck.ecos.notifications.lib.NotificationType;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.security.NodeOwnerDAO;
import ru.citeck.ecos.server.utils.Utils;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Notification sender for tasks (ItemType = DelegateTask).
 * <p>
 * The following implementation is used:
 * - subject line: default
 * - template: retrieved by key = process-definition
 * - template args:
 * {
 *     "task": {
 *         "id": "task id",
 *         "name": "task name",
 *         "description": "task description",
 *         "priority": "task priority",
 *         "dueDate": "task dueDate",
 *     }
 * },
 * "workflow": {
 *     "id": "workflow id",
 *     "documents": [
 *         "nodeRef1",
 *         ...
 *     ]
 * }
 * - notification recipients - assignee or pooled actors, whichever present
 *
 * @author Elena Zaripova
 */
@Slf4j
class DelegateTaskNotificationSender extends AbstractNotificationSender<DelegateTask> {

    // template argument names:
    public static final String ARG_TASK = "task";
    public static final String ARG_TASK_ID = "id";
    public static final String ARG_TASK_NAME = "name";
    public static final String ARG_TASK_DESCRIPTION = "description";
    public static final String ARG_TASK_PROPERTIES = "properties";
    public static final String ARG_TASK_PROPERTIES_PRIORITY = "bpm_priority";
    public static final String ARG_TASK_PROPERTIES_DESCRIPTION = "bpm_description";
    public static final String ARG_TASK_PROPERTIES_DUEDATE = "bpm_dueDate";
    public static final String ARG_WORKFLOW = "workflow";
    public static final String ARG_WORKFLOW_ID = "id";
    public static final String ARG_WORKFLOW_PROPERTIES = "properties";
    public static final String ARG_WORKFLOW_DOCUMENTS = "documents";
    List<String> allowDocList;
    Map<String, Map<String, String>> subjectTemplates;
    Map<String, String> subjectTemplatesForWorkflow;
    private String nodeVariable;
    private String templateEngine = "freemarker";
    private NodeOwnerDAO nodeOwnerDAO;
    protected Map<String, Boolean> markResending;

    // get notification template arguments for the task
    protected Map<String, Serializable> getNotificationArgs(DelegateTask task) {
        Map<String, Serializable> args = new HashMap<>();
        args.put(ARG_TASK, getTaskInfo(task));
        args.put(ARG_WORKFLOW, getWorkflowInfo(task));
        return args;
    }

    @Override
    protected Map<String, Object> getEcosNotificationArgs(DelegateTask task) {
        Map<String, Object> args = super.getEcosNotificationArgs(task);

        TaskExecutionRecord taskExecutionRecord = executionsTaskService
            .getExecutionRecord(DelegateTask.class, task)
            .orElse(null);
        args.put("_record", taskExecutionRecord);

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
                }else if (entry.getValue() instanceof Serializable) {
                    properties.put(entry.getKey(), (Serializable) entry.getValue());
                } else {
                    properties.put(entry.getKey(), entry.getValue().toString());
                }
            } else {
                properties.put(entry.getKey(), null);
            }
        }
        return taskInfo;
    }

    private Serializable getWorkflowInfo(DelegateTask task) {
        HashMap<String, Object> workflowInfo = new HashMap<>();
        workflowInfo.put(ARG_WORKFLOW_ID, "activiti$" + task.getProcessInstanceId());
        workflowInfo.put(ARG_WORKFLOW_DOCUMENTS, getWorkflowDocuments(task));
        return workflowInfo;
    }

    public void sendNotification(DelegateTask task) {
        NodeRef template = getNotificationTemplate(task);

        if (template == null) {
            return;
        }

        Set<String> recipients = getRecipients(task, template, null);
        if (recipients == null || recipients.isEmpty()) {
            log.debug(
                "Skipped notification sending. Empty recipients list. " +
                "Template: " + template + " task: " + task.getId()
            );
            return;
        }
        String notificationTemplate = (String) nodeService.getProperty(template, DmsModel.PROP_ECOS_NOTIFICATION_TEMPLATE);
        if (StringUtils.isNotBlank(notificationTemplate)) {
            send(task, notificationTemplate, recipients);
        } else {
            sendDeprecated(task, template, recipients);
        }
    }

    private String getEtype(DelegateTask task) {
        ExecutionEntity executionEntity = ((ExecutionEntity) task.getExecution()).getProcessInstance();
        ActivitiScriptNode scriptNode = (ActivitiScriptNode) executionEntity.getVariable("bpm_package");
        NodeRef workflowPackage = scriptNode != null ? scriptNode.getNodeRef() : null;
        List<ChildAssociationRef> childAssocs = new LinkedList<>();
        if (workflowPackage != null && nodeService.exists(workflowPackage)) {
            childAssocs.addAll(nodeService.getChildAssocs(
                workflowPackage, WorkflowModel.ASSOC_PACKAGE_CONTAINS, RegexQNamePattern.MATCH_ALL));
            childAssocs.addAll(nodeService.getChildAssocs(
                workflowPackage, ContentModel.ASSOC_CONTAINS, RegexQNamePattern.MATCH_ALL));
        }

        List<RecordRef> recordRefs = childAssocs.stream()
            .map(childRef -> RecordRef.valueOf(childRef.getChildRef().toString()))
            .collect(Collectors.toList());

        return getFirstEtypeFromRecordRefs(recordRefs);
    }

    private void send(DelegateTask task, String template, Set<String> recipients) {
        Map<String, Object> additionalMeta = getEcosNotificationArgs(task);

        Object record = additionalMeta.get("_record");
        additionalMeta.remove("_record");

        Notification notification = new Notification.Builder()
            .record(record)
            .templateRef(RecordRef.valueOf(template))
            .notificationType(NotificationType.EMAIL_NOTIFICATION)
            .recipients(getEmailFromAuthorityNames(recipients))
            .additionalMeta(additionalMeta)
            .build();

        notificationService.send(notification);
    }

    // TODO delete on full migration. This is deprecated code
    private void sendDeprecated(DelegateTask task, NodeRef template, Set<String> recipients) {
        NotificationContext notificationContext = new NotificationContext();
        notificationContext.setTemplateArgs(getNotificationArgs(task));
        String notificationProviderName = EMailNotificationProvider.NAME;
        String subject = null;
        Set<String> authorities = new HashSet<>();
        if (template != null && nodeService.exists(template)) {
            setBodyTemplate(notificationContext, template);
            String taskFormKey = (String) task.getVariableLocal("taskFormKey");
            subject = getSubject(task, notificationContext.getTemplateArgs(), template, taskFormKey);
            authorities.addAll(recipients);
        }
        notificationContext.setSubject(subject);
        for (String to : authorities) {
            notificationContext.addTo(to);
        }
        notificationContext.setAsyncNotification(getAsyncNotification());
        services.getNotificationService().sendNotification(notificationProviderName, notificationContext);
    }

    protected String getSubject(DelegateTask task, Map<String, Serializable> templateArgs, NodeRef template, String taskFormKey) {
        String subject;
        if (markResending != null
                && markResending.containsKey(taskFormKey)
                && markResending.get(taskFormKey)
                && isResending(task)) {
            subject = (String) nodeService.getProperty(template, DmsModel.PROP_TITLE_FOR_RESENDING);
        } else {
            String subjectTemplate = (String) nodeService.getProperty(template, DmsModel.PROP_SUBJECT_TEMPLATE);
            if (subjectTemplate != null) {
                subject = (String) nodeService.getProperty(template, ContentModel.PROP_TITLE);

                subjectTemplate = Utils.restoreFreemarkerVariables(subjectTemplate);
                String processedSubjectLine = "";
                if (StringUtils.isNotBlank(subjectTemplate)) {
                    processedSubjectLine = services.getTemplateService().processTemplateString(templateEngine,
                        subjectTemplate, templateArgs);
                }

                if (subject == null) {
                    subject = processedSubjectLine;
                } else {
                    subject += ": " + processedSubjectLine;
                }
            } else {
                subject = (String) nodeService.getProperty(template, ContentModel.PROP_TITLE);
            }
            if (subject == null) {
                ArrayList<Object> docsInfo = getWorkflowDocuments(task);
                if (!docsInfo.isEmpty() && subjectTemplates != null && subjectTemplates.containsKey(taskFormKey)) {
                    Map<String, String> taskSubjectTemplate = subjectTemplates.get(taskFormKey);
                    if (taskSubjectTemplate.containsKey(qNameConverter.mapQNameToName(nodeService.getType((NodeRef) docsInfo.get(0))))) {
                        HashMap<String, Object> model = new HashMap<>(1);
                        model.put(nodeVariable, docsInfo.get(0));
                        subject = services.getTemplateService().processTemplateString(templateEngine,
                                taskSubjectTemplate.get(qNameConverter.mapQNameToName(nodeService.getType((NodeRef) docsInfo.get(0)))),
                                model);
                    }
                }
                if (subject == null) {
                    subject = task.getName();
                }
            }
        }
        return subject;
    }

    public void setAllowDocList(List<String> allowDocList) {
        this.allowDocList = allowDocList;
    }

    public void setSubjectTemplates(Map<String, Map<String, String>> subjectTemplates) {
        this.subjectTemplates = subjectTemplates;
    }

    @Deprecated
    public void setTemplateService(TemplateService templateService) {
        // not used
    }

    public void setTemplateEngine(String templateEngine) {
        this.templateEngine = templateEngine;
    }

    public void setNodeVariable(String nodeVariable) {
        this.nodeVariable = nodeVariable;
    }

    public void setSubjectTemplatesForWorkflow(Map<String, String> subjectTemplatesForWorkflow) {
        this.subjectTemplatesForWorkflow = subjectTemplatesForWorkflow;
    }

    public NodeRef getNotificationTemplate(DelegateTask task) {
        String processDef = task.getProcessDefinitionId();
        String wfkey = "activiti$" + processDef.substring(0, processDef.indexOf(':'));
        String tkey = (String) task.getVariableLocal("taskFormKey");
        String etype = getEtype(task);
        return getNotificationTemplateWithEtype(wfkey, tkey, etype);
    }

    /* Properties for tasks provided as map: "task name"-{"property1"-"value1", ...}
    * @param task subscribers
    */
    @Deprecated
    public void setTaskProperties(Map<String, Map<String, String>> taskProperties) {
        // not used
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
                ExecutionEntity executionEntity = ((ExecutionEntity) task.getExecution()).getProcessInstance();
                NodeRef initiator = ((ActivitiScriptNode) executionEntity.getVariable("initiator")).getNodeRef();
                String initiatorName = (String) nodeService.getProperty(initiator, ContentModel.PROP_USERNAME);
                String sender = (String) task.getVariable("cwf_sender");
                log.debug("!!!! user " + user);
                log.debug("!!!! sender " + sender);
                log.debug("!!!! initiatorName " + initiatorName);
                if (user != null && !user.equals(initiatorName) && !user.equals(sender)) {
                    authorities.add(user);
                }
            }
        } else {
            authorities.add(task.getAssignee());
        }
        log.debug("authorities " + authorities);
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

    protected void sendToOwner(Set<String> authorities, NodeRef node) {
        String owner = nodeOwnerDAO.getOwner(node);
        authorities.add(owner);
    }

    public void setNodeOwnerDAO(NodeOwnerDAO nodeOwnerDAO) {
        this.nodeOwnerDAO = nodeOwnerDAO;
    }

    /**
     * Flag for marking mail as resending
     *
     * @param markResending of values
     */
    public void setMarkResending(Map<String, Boolean> markResending) {
        this.markResending = markResending;
    }

    public ArrayList<Object> getWorkflowDocuments(DelegateTask task) {
        ArrayList<Object> docsInfo = new ArrayList<>();
        NodeRef workflowPackage = null;
        ExecutionEntity executionEntity = ((ExecutionEntity) task.getExecution()).getProcessInstance();
        ActivitiScriptNode scriptNode = (ActivitiScriptNode) executionEntity.getVariable("bpm_package");
        if (scriptNode != null) {
            workflowPackage = scriptNode.getNodeRef();
        }
        if (workflowPackage != null && nodeService.exists(workflowPackage)) {
            addAssocContains(docsInfo, nodeService.getChildAssocs(workflowPackage,
                    WorkflowModel.ASSOC_PACKAGE_CONTAINS, RegexQNamePattern.MATCH_ALL));
            addAssocContains(docsInfo, nodeService.getChildAssocs(workflowPackage,
                    ContentModel.ASSOC_CONTAINS, RegexQNamePattern.MATCH_ALL));
        }
        return docsInfo;
    }

    private void addAssocContains(ArrayList<Object> docsInfo, List<ChildAssociationRef> children) {
        for (ChildAssociationRef child : children) {
            if (allowDocList == null) {
                docsInfo.add(child.getChildRef());
            } else {
                if (child != null && qNameConverter != null && nodeService != null && allowDocList.contains(qNameConverter.mapQNameToName(nodeService.getType(child.getChildRef())))) {
                    docsInfo.add(child.getChildRef());
                }
            }
        }
    }

    public boolean isResending(DelegateTask task) {
        if (getWorkflowDocuments(task) != null && !getWorkflowDocuments(task).isEmpty()) {
            String query = "TYPE:\"wfgfam:familiarizeTask\" AND @wfm:document:\"" + getWorkflowDocuments(task).get(0) + "\"";
            ResultSet nodes = searchService.query(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, "fts-alfresco", query);
            return (nodes.length() > 0);
        }
        return false;
    }

}
