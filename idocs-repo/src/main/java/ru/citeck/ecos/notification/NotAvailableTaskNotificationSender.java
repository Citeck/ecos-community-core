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

import org.activiti.engine.delegate.DelegateTask;
import org.alfresco.repo.jscript.ScriptNode;
import org.alfresco.repo.notification.EMailNotificationProvider;
import org.alfresco.repo.workflow.WorkflowQNameConverter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.security.AuthorityType;
import org.apache.commons.lang3.StringUtils;
import ru.citeck.ecos.deputy.AvailabilityService;
import ru.citeck.ecos.model.CiteckWorkflowModel;
import ru.citeck.ecos.model.DmsModel;
import ru.citeck.ecos.records2.RecordRef;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

import static ru.citeck.ecos.utils.WorkflowConstants.VAR_TASK_ORIGINAL_OWNER;

/**
 * Notification sender for tasks(ItemType = DelegateTask).
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
 * - notification recipients - assignee or pooled actors, whichever present
 */
class NotAvailableTaskNotificationSender extends DelegateTaskNotificationSender {

    private AvailabilityService availabilityService;

    @Override
    public void sendNotification(DelegateTask task) {
        NodeRef template = getNotificationTemplate(task);
        boolean isNotSend = template != null && nodeService.exists(template)
            || isCheckOwner(task) && isSendToInitiator(template);

        if (isNotSend) {
            return;
        }

        send(task, template);
    }

    private boolean isCheckOwner(DelegateTask task) {
        WorkflowQNameConverter qNameConverter = new WorkflowQNameConverter(services.getNamespaceService());
        String lastTaskOwnerVar = qNameConverter.mapQNameToName(CiteckWorkflowModel.PROP_LAST_TASK_OWNER);
        String owner = task.getAssignee();
        String originalOwner = (String) task.getVariable(VAR_TASK_ORIGINAL_OWNER);

        String lastTaskOwner = (String) task.getVariable(lastTaskOwnerVar);
        boolean ownerIsOriginal = originalOwner != null && originalOwner.equals(owner);
        return ownerIsOriginal && (lastTaskOwner == null || originalOwner.equals(lastTaskOwner));
    }

    private void send(DelegateTask task, NodeRef template) {
        String notificationTemplate = (String) nodeService.getProperty(template, DmsModel.PROP_ECOS_NOTIFICATION_TEMPLATE);
        boolean isEcosNotify = StringUtils.isNotBlank(notificationTemplate);

        String initiator = getInitiator(task);
        Map<String, String> answerByUnavailableUser = new HashMap<>();
        Map<String, Object> assigneesNodesByName = new HashMap<>();
        Set<String> recipients = new HashSet<>();

        Map<String, Serializable> argsMap = getNotificationArgs(task);

        if (isSendToInitiator(template)) {
            Set<String> assignees = getAssignee(task);
            for (String assignee : assignees) {
                AuthorityType type = AuthorityType.getAuthorityType(assignee);
                if (AuthorityType.USER.equals(type)) {
                    String answer = availabilityService.getUserUnavailableAutoAnswer(assignee);
                    if (answer != null) {
                        answerByUnavailableUser.put(assignee, answer);
                        if (isEcosNotify) {
                            assigneesNodesByName.put(assignee, RecordRef.valueOf("people@" + assignee));
                        } else {
                            assigneesNodesByName.put(assignee, new ScriptNode(services.getPersonService().getPerson(assignee), services));
                        }
                    }
                }
            }
            if (isEcosNotify) {
                List<Serializable> collect = assigneesNodesByName.values().stream()
                    .map(obj -> (Serializable) obj)
                    .collect(Collectors.toList());
                argsMap.put("assignees", (Serializable) collect);
            } else {
                argsMap.put("assignees", (Serializable) assigneesNodesByName);
            }
            recipients.addAll(assigneesNodesByName.keySet());
            argsMap.put("isSendToInitiator", true);
        } else if (isSendToAssignee(template)) {
            String answer = availabilityService.getUserUnavailableAutoAnswer(initiator);
            if (answer != null) {
                answerByUnavailableUser.put(initiator, answer);
            }
            recipients.addAll(getAssignee(task));
            argsMap.put("isSendToAssignee", true);
        }

        if (!answerByUnavailableUser.isEmpty()) {
            argsMap.put("answerByUser", (Serializable) answerByUnavailableUser);
            send(recipients, initiator, argsMap, template, task, isSendToInitiator(template), notificationTemplate);
        }
    }

    @Override
    public NodeRef getNotificationTemplate(DelegateTask task) {
        return getNotificationTemplate(null, null, true);
    }

    public void setAvailabilityService(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    private void send(Set<String> recipients, String from, Map<String, Serializable> args, NodeRef template, DelegateTask task, boolean isSendFromAssegneesToInitiatior, String notificationTemplate) {
        String taskFormKey = (String) task.getVariableLocal("taskFormKey");
        if (recipients != null && !recipients.isEmpty() && template != null) {
            String notificationProviderName = EMailNotificationProvider.NAME;
            String subject = getSubject(task, args, template, taskFormKey);
            Boolean sendToInitiator = isSendToInitiator(template);
            for (String rec : recipients) {

                String finalFrom = sendToInitiator ? rec : from;
                String finalRec = sendToInitiator ? from : rec;

                if (StringUtils.isNotBlank(notificationTemplate)) {
                    Map<String, Object> newArgs = getEcosNotificationArgs(task);
                    newArgs.putAll(args);

                    sendEcosNotification(notificationProviderName, finalFrom, subject, notificationTemplate, newArgs, Collections.singletonList(finalRec), false);
                } else {
                    sendNotification(notificationProviderName, finalFrom, subject, template, args, Collections.singletonList(finalRec), false);
                }
            }
        }
    }
}
