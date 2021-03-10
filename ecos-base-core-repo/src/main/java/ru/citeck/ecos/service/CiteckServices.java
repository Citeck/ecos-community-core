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
package ru.citeck.ecos.service;

import org.alfresco.service.namespace.QName;

public interface CiteckServices {

    String CITECK_NAMESPACE = "http://www.citeck.ru";
    String STATUS_SERVICE_BEAN_NAME = "statusService";

    QName GRANT_PERMISSION_SERVICE = QName.createQName(CITECK_NAMESPACE, "GrantPermissionService");
    QName CONFISCATE_SERVICE = QName.createQName(CITECK_NAMESPACE, "ConfiscateService");
    QName AVAILABILITY_SERVICE = QName.createQName(CITECK_NAMESPACE, "AvailabilityService");
    QName DEPUTY_SERVICE = QName.createQName(CITECK_NAMESPACE, "DeputyService");
    QName ORG_STRUCT_SERVICE = QName.createQName(CITECK_NAMESPACE, "OrgStructService");
    QName ORG_META_SERVICE = QName.createQName(CITECK_NAMESPACE, "OrgMetaService");
    QName COUNTER_SERVICE = QName.createQName(CITECK_NAMESPACE, "CounterService");
    QName ENUMERATION_SERVICE = QName.createQName(CITECK_NAMESPACE, "EnumerationService");
    QName CONFIRM_SERVICE = QName.createQName(CITECK_NAMESPACE, "ConfirmService");
    QName CARD_TEMPLATE_SERVICE = QName.createQName(CITECK_NAMESPACE, "CardTemplateService");
    QName CARDLET_SERVICE = QName.createQName(CITECK_NAMESPACE, "CardletService");
    QName EXCEPTION_SERVICE = QName.createQName(CITECK_NAMESPACE, "ExceptionService");
    QName ADVANCED_WORKFLOW_SERVICE = QName.createQName(CITECK_NAMESPACE, "advancedWorkflowService");
    QName WORKFLOW_MIRROR_SERVICE = QName.createQName(CITECK_NAMESPACE, "workflowMirrorService");
    QName CASE_PERFORM_UTILS = QName.createQName(CITECK_NAMESPACE, "ecos.workflow.case-perform-utils");
    QName HISTORY_SERVICE = QName.createQName(CITECK_NAMESPACE, "historyService");
    QName CRITERIA_SEARCH_SERVICE = QName.createQName(CITECK_NAMESPACE, "criteriaSearchService");
    QName LIFECYCLE_SERVICE = QName.createQName(CITECK_NAMESPACE, "lifeCycleService");
    QName CASE_STATUS_SERVICE = QName.createQName(CITECK_NAMESPACE, "caseStatusService");
    QName CASE_STATUS_ASSOC_DAO = QName.createQName(CITECK_NAMESPACE, "caseStatusAssocDao");
    QName CASE_COMMANDS_SERVICE = QName.createQName(CITECK_NAMESPACE, "caseCommandsService");
    QName CASE_ACTIVITY_SERVICE = QName.createQName(CITECK_NAMESPACE, "caseActivityService");
    QName CASE_ACTIVITY_EVENT_SERVICE = QName.createQName(CITECK_NAMESPACE, "caseActivityEventService");
    QName ACTIVITY_COMMON_SERVICE = QName.createQName(CITECK_NAMESPACE, "activityCommonService");
    QName ALF_ACTIVITY_UTILS = QName.createQName(CITECK_NAMESPACE, "alfActivityUtils");
    QName JOURNAL_SERVICE = QName.createQName(CITECK_NAMESPACE, "journalService");
    QName CASE_COMPLETENESS_SERVICE = QName.createQName(CITECK_NAMESPACE, "caseCompletenessService");
    QName STATUS_SERVICE_SERVICE = QName.createQName("", STATUS_SERVICE_BEAN_NAME);

    QName NODE_ATTRIBUTE_SERVICE = QName.createQName(CITECK_NAMESPACE, "nodeAttributeService");
    QName INVARIANT_SERVICE = QName.createQName(CITECK_NAMESPACE, "invariantService");

    QName NODE_INFO_FACTORY = QName.createQName(CITECK_NAMESPACE, "NodeInfoFactory");
    QName CASE_ROLE_SERVICE = QName.createQName(CITECK_NAMESPACE, "caseRoleService");

    QName ITEMS_UPDATE_STATE = QName.createQName(CITECK_NAMESPACE, "ecos.itemsUpdateState");
    QName CASE_XML_SERVICE = QName.createQName(CITECK_NAMESPACE, "caseXmlService");

}
