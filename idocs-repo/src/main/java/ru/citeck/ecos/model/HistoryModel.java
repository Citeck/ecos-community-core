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

/**
 * @author Anton Fateev <anton.fateev@citeck.ru>
 */
public final class HistoryModel {

    // model
    public static final String MODEL_PREFIX = "history";

    // namespace
    public static final String ALFRESCO_NAMESPACE = "http://www.alfresco.org/model/content/1.0";
    public static final String HISTORY_NAMESPACE = "http://www.citeck.ru/model/history/1.0";
    public static final String EVENT_NAMESPACE = "http://www.citeck.ru/model/event/1.0";

    // types
    public static final QName TYPE_BASIC_EVENT = QName.createQName(HISTORY_NAMESPACE, "basicEvent");

    // aspects
    public static final QName ASPECT_DOCUMENT_EVENT = QName.createQName(HISTORY_NAMESPACE, "documentEvent");
    public static final QName ASPECT_WORKFLOW_EVENT = QName.createQName(HISTORY_NAMESPACE, "workflowEvent");
    public static final QName ASPECT_TASK_EVENT = QName.createQName(HISTORY_NAMESPACE, "taskEvent");
    public static final QName ASPECT_HISTORICAL = QName.createQName(HISTORY_NAMESPACE, "historical");

    // properties
    public static final QName PROP_DATE = QName.createQName(EVENT_NAMESPACE, "date");
    public static final QName PROP_NAME = QName.createQName(EVENT_NAMESPACE, "name");
    public static final QName PROP_DOCUMENT_VERSION = QName.createQName(EVENT_NAMESPACE, "documentVersion");

    public static final QName PROP_PROPERTY_NAME = QName.createQName(EVENT_NAMESPACE, "propertyName");
    public static final QName PROP_PROPERTY_VALUE = QName.createQName(EVENT_NAMESPACE, "propertyValue");
    public static final QName PROP_TARGET_NODE_TYPE = QName.createQName(EVENT_NAMESPACE, "targetNodeType");
    public static final QName PROP_TARGET_NODE_KIND = QName.createQName(EVENT_NAMESPACE, "targetNodeKind");

    public static final QName PROP_TASK_INSTANCE_ID = QName.createQName(EVENT_NAMESPACE, "taskInstanceId");
    public static final QName PROP_TASK_TYPE = QName.createQName(EVENT_NAMESPACE, "taskType");
    public static final QName PROP_TASK_OUTCOME = QName.createQName(EVENT_NAMESPACE, "taskOutcome");
    public static final QName PROP_TASK_OUTCOME_NAME = QName.createQName(EVENT_NAMESPACE, "taskOutcomeName");
    public static final QName PROP_TASK_COMMENT = QName.createQName(EVENT_NAMESPACE, "taskComment");
    public static final QName PROP_LAST_TASK_COMMENT = QName.createQName(EVENT_NAMESPACE, "lastTaskComment");
    public static final QName PROP_TASK_POOLED_ACTORS = QName.createQName(EVENT_NAMESPACE, "taskPooledActors");
    public static final QName PROP_TASK_ACTORS = QName.createQName(EVENT_NAMESPACE, "taskActors");
    public static final QName PROP_TASK_ATTACHMENTS = QName.createQName(EVENT_NAMESPACE, "taskAttachments");
    public static final QName PROP_TASK_ROLE = QName.createQName(EVENT_NAMESPACE, "taskRole");
    public static final QName PROP_CASE_TASK = QName.createQName(EVENT_NAMESPACE, "caseTask");
    public static final QName PROP_TASK_DUE_DATE = QName.createQName(EVENT_NAMESPACE, "taskDueDate");
    public static final QName PROP_TASK_TITLE = QName.createQName(EVENT_NAMESPACE, "taskTitle");
    public static final QName PROP_TASK_FORM_KEY = QName.createQName(EVENT_NAMESPACE, "taskFormKey");
    public static final QName PROP_TASK_DEFINITION_KEY = QName.createQName(EVENT_NAMESPACE, "taskDefinitionKey");
    public static final QName INITIATOR = QName.createQName(EVENT_NAMESPACE, "initiator_added");

    public static final QName PROP_DOC_TYPE = QName.createQName(EVENT_NAMESPACE, "docType");
    public static final QName PROP_DOC_STATUS_NAME = QName.createQName(EVENT_NAMESPACE, "docStatusName");
    public static final QName PROP_DOC_STATUS_TITLE = QName.createQName(EVENT_NAMESPACE, "docStatusTitle");

    public static final QName PROP_WORKFLOW_INSTANCE_ID = QName.createQName(EVENT_NAMESPACE, "workflowInstanceId");
    public static final QName PROP_WORKFLOW_TYPE = QName.createQName(EVENT_NAMESPACE, "workflowType");
    public static final QName PROP_WORKFLOW_DESCRIPTION = QName.createQName(EVENT_NAMESPACE, "workflowDescription");

    public static final QName PROP_ADDITIONAL_PROPERTIES = QName.createQName(EVENT_NAMESPACE, "additionalProperties");

    // associations
    public static final QName ASSOC_INITIATOR = QName.createQName(EVENT_NAMESPACE, "initiator");
    public static final QName ASSOC_DOCUMENT = QName.createQName(EVENT_NAMESPACE, "document");
    public static final QName ASSOC_CASE = QName.createQName(EVENT_NAMESPACE, "case");
    public static final QName ASSOC_EVENT_CONTAINED = QName.createQName(HISTORY_NAMESPACE, "eventContained");

    public static final QName MODIFIER_PROPERTY = QName.createQName(ALFRESCO_NAMESPACE, "modifier");
}
