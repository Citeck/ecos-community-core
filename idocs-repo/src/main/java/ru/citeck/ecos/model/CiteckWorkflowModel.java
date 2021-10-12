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
package ru.citeck.ecos.model;

import org.alfresco.service.namespace.QName;

public interface CiteckWorkflowModel {

    String PREFIX = "cwf";
    String NAMESPACE = "http://www.citeck.ru/model/workflow/1.1";

    QName ASPECT_SENDER = QName.createQName(NAMESPACE, "sender");
    QName ASPECT_ATTACHED_DOCUMENT = QName.createQName(NAMESPACE, "attachedDocument");
    QName ASPECT_ORIGINAL_OWNER = QName.createQName(NAMESPACE, "originalOwner");
    QName ASPECT_URGENCY_CAUSE = QName.createQName(NAMESPACE, "urgencyCause");
    QName ASPECT_PRIORITY = QName.createQName(NAMESPACE, "priority");

    QName PROP_SENDER = QName.createQName(NAMESPACE, "sender");
    QName PROP_SENDER_NAME = QName.createQName(NAMESPACE, "senderName");
    QName PROP_ATTACHED_DOCUMENT = QName.createQName(NAMESPACE, "attachedDocument");
    QName PROP_TASK_ORIGINAL_OWNER = QName.createQName(NAMESPACE, "taskOriginalOwner");
    QName PROP_URGENCY_CAUSE = QName.createQName(NAMESPACE, "urgencyCause");
    QName PROP_PRIORITY = QName.createQName(NAMESPACE, "priority");
    QName PROP_LASTCOMMENT = QName.createQName(NAMESPACE, "lastcomment");
    QName PROP_LAST_TASK_OWNER = QName.createQName(NAMESPACE, "lastTaskOwner");
    QName PROP_IS_OPTIONAL_TASK = QName.createQName(NAMESPACE, "isOptionalTask");
    QName PROP_IS_WORKFLOW_ACTIVE = QName.createQName(NAMESPACE, "isWorkflowActive");
    QName PROP_IS_TASK_CLAIMABLE = QName.createQName(NAMESPACE, "isTaskClaimable");
    QName PROP_IS_TASK_RELEASABLE = QName.createQName(NAMESPACE, "isTaskReleasable");
    QName PROP_IS_TASK_REASSIGNABLE = QName.createQName(NAMESPACE, "isTaskReassignable");
    QName PROP_TASK_TITLE = QName.createQName(NAMESPACE, "taskTitle");
    QName PROP_LAST_COMPLETED_TASK_DATE = QName.createQName(NAMESPACE, "lastCompletedTaskDate");
    QName PROP_TIMER_ERROR_STATUS = QName.createQName(NAMESPACE, "timerErrorStatus");
    QName PROP_DOCUMENT_PROP = QName.createQName(NAMESPACE, "document-prop");

    QName ASSOC_TARGET_ITEMS = QName.createQName(NAMESPACE, "targetItems");

}
