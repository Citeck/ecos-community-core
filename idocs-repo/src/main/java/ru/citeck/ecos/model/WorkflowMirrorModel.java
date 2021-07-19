/*
 * Copyright (C) 2008-2018 Citeck LLC.
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

public interface WorkflowMirrorModel {

    String NAMESPACE = "http://www.citeck.ru/model/workflow-mirror/1.0";

    QName ASPECT_ATTRIBUTES = QName.createQName(NAMESPACE, "attributes");
    QName PROP_TASK_TYPE = QName.createQName(NAMESPACE, "taskType");
    QName PROP_WORKFLOW_ID = QName.createQName(NAMESPACE, "workflowId");
    QName PROP_ACTORS = QName.createQName(NAMESPACE, "actors");
    QName PROP_ASSIGNEE = QName.createQName(NAMESPACE, "assignee");
    QName PROP_DOCUMENT = QName.createQName(NAMESPACE, "document");
    QName PROP_DOCUMENT_TYPE = QName.createQName(NAMESPACE, "documentType");
    QName PROP_DOCUMENT_TYPE_TITLE = QName.createQName(NAMESPACE, "documentTypeTitle");
    QName PROP_DOCUMENT_KIND = QName.createQName(NAMESPACE, "documentKind");
    QName PROP_DOCUMENT_KIND_TITLE = QName.createQName(NAMESPACE, "documentKindTitle");
    QName ASPECT_MIRROR_TASKS = QName.createQName(NAMESPACE, "mirrorTasks");
    QName ASSOC_MIRROR_TASK = QName.createQName(NAMESPACE, "mirrorTask");
    QName PROP_ASSIGNEE_MANAGER = QName.createQName(NAMESPACE, "assigneeManager");
    QName PROP_WORKFLOW_NAME = QName.createQName(NAMESPACE, "workflowName");
    QName PROP_WORKFLOW_INITIATOR = QName.createQName(NAMESPACE, "workflowInitiator");
    QName PROP_COUNTERPARTY = QName.createQName(NAMESPACE, "counterparty");
    QName PROP_CASE_STATUS = QName.createQName(NAMESPACE, "caseStatus");
    QName PROP_CASE_STATUS_PROP = QName.createQName(NAMESPACE, "caseStatus-prop");
    QName PROP_DOCUMENT_ECOS_TYPE = QName.createQName(NAMESPACE, "documentEcosType");

    QName ASSOC_ASSIGNEE_MIRROR = QName.createQName(NAMESPACE, "assigneeMirror");
}
