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
 * Alfresco content model for DMS project.
 *
 */
public final class DmsModel {

    // model
    public static final String DMS_MODEL_PREFIX = "dms";

	// namespace
	public static final String DMS_NAMESPACE = "http://www.citeck.ru/model/content/dms/1.0";

	// types
	public static final QName TYPE_AGREEMENT = QName.createQName(DMS_NAMESPACE, "agreement");
	public static final QName TYPE_DOC = QName.createQName(DMS_NAMESPACE, "generalDocument");
	public static final QName TYPE_TEMPLATE = QName.createQName(DMS_NAMESPACE, "template");

	// aspects
	public static final QName ASPECT_GENERAL_AGREEMENT = QName.createQName(DMS_NAMESPACE, "generalAgreement");
	// properties
    public static final QName PROP_APPROVAL_DATE = QName.createQName(DMS_NAMESPACE, "approvalDate");
	public static final QName PROP_AMOUNT_EUR = QName.createQName(DMS_NAMESPACE, "amountEUR");
	public static final QName PROP_AMOUNT = QName.createQName(DMS_NAMESPACE, "amount");
	public static final QName PROP_APPROVAL_STATUS = QName.createQName(DMS_NAMESPACE, "approval_status");
	public static final QName ASSOC_CURRENCY = QName.createQName(DMS_NAMESPACE, "currency");
	public static final QName ASSOC_CONTRACTOR = QName.createQName(DMS_NAMESPACE, "contractor_to_department");
	public static final QName ASSOC_SUPPLEMENTARY_TO_AGREEMENT = QName.createQName(DMS_NAMESPACE,
			"supplementaryAgreement_to_agreement");
	public static final QName AGREEMENT_TO_CONTRACTOR = QName.createQName(DMS_NAMESPACE, "agreement_to_contractor");
	public static final QName ASSOC_AGREEMENT_TO_FILES = QName.createQName(DMS_NAMESPACE,"agreement_to_files");
	public static final QName PROP_INN = QName.createQName(DMS_NAMESPACE, "INN");
	public static final QName PROP_KPP = QName.createQName(DMS_NAMESPACE, "KPP");
	public static final QName PROP_AGREEMENT_NUMBER = QName.createQName(DMS_NAMESPACE, "number");
	public static final QName PROP_ISAPPROVE = QName.createQName(DMS_NAMESPACE, "isApproved");
	public static final QName PROP_SUBJECT = QName.createQName(DMS_NAMESPACE, "subject");
	public static final QName PROP_TYPE = QName.createQName(DMS_NAMESPACE, "type");
	public static final QName PROP_STATUS = QName.createQName(DMS_NAMESPACE, "status");
	public static final QName PROP_SHAREHOLDERS_NEGOTIATION = QName.createQName(DMS_NAMESPACE, "shareholdersNegotiation");
	public static final QName PROP_CAPITAL_EXPENDITURE = QName.createQName(DMS_NAMESPACE, "capitalExpenditure");

	public static final QName PROP_AGREEMENT_NATURE = QName.createQName(DMS_NAMESPACE, "nature");

	public static final QName PROP_ASPECT = QName.createQName(DMS_NAMESPACE, "aspect");
	public static final QName PROP_ERROR_MESSAGE_CONFIG = QName.createQName(DMS_NAMESPACE, "errorMessageConfig");

	// Legacy properties
	public static final QName PROP_CONTRACT_AMOUNT = QName.createQName(DMS_NAMESPACE, "com_citeck_cms_contract_amount");
	public static final QName PROP_CONTRACT_NUMBER = QName.createQName(DMS_NAMESPACE, "com_citeck_cms_contract_number");
	public static final QName PROP_CONTRACT_ENDDATE = QName.createQName(DMS_NAMESPACE,
			"com_citeck_cms_contract_enddate");
	public static final QName PROP_CONTRACT_SUPPLIER = QName.createQName(DMS_NAMESPACE,
			"com_citeck_cms_contract_supplier");
	public static final QName PROP_CONTRACT_DATE = QName.createQName(DMS_NAMESPACE,
			"com_citeck_cms_contract_contractDate");
	public static final QName PROP_CONTRACT_STARTDATE = QName.createQName(DMS_NAMESPACE,
			"com_citeck_cms_contract_startdate");
	public static final QName PROP_CONTRACT_AUTO_PROLONG = QName.createQName(DMS_NAMESPACE,
			"com_citeck_cms_contract_autoprolongation");
	public static final QName PROP_CONTRACT_PROLONG_ENDDATE = QName.createQName(DMS_NAMESPACE,
			"com_citeck_cms_contract_prolongationenddate");

	public static final QName PROP_CONTRACTOR_TITLE = QName.createQName(DMS_NAMESPACE, "juridicalTitle");
	public static final QName PROP_CONTRACTOR_POST_ADDRESS = QName.createQName(DMS_NAMESPACE, "postAddress");
	public static final QName PROP_CONTRACTOR_EMAIL = QName.createQName(DMS_NAMESPACE, "email");
	public static final QName PROP_CONTRACTOR_PHONEFAX = QName.createQName(DMS_NAMESPACE, "phonefax");

	// aspect hasSupplementaryFiles
	public static final QName ASPECT_HAS_SUPPLEMENTARY_FILES = QName.createQName(DMS_NAMESPACE, "hasSupplementaryFiles");
	public static final QName ASSOC_SUPPLEMENARY_FILES = QName.createQName(DMS_NAMESPACE, "supplementaryFiles");

	// aspect dms:templateable
	public static final QName ASPECT_TEMPLATEABLE = QName.createQName(DMS_NAMESPACE, "templateable");
	public static final QName ASSOC_TEMPLATE = QName.createQName(DMS_NAMESPACE, "templateAssociation");
	public static final QName PROP_UPDATE_CONTENT = QName.createQName(DMS_NAMESPACE, "updateContent");

	// type dms:cardType
	public static final QName TYPE_CARD_TEMPLATE = QName.createQName(DMS_NAMESPACE, "cardTemplate");
	public static final QName PROP_CARD_TYPE = QName.createQName(DMS_NAMESPACE, "cardType");
	public static final QName PROP_TEMPLATE_TYPE = QName.createQName(DMS_NAMESPACE, "templateType");

	// type dms:notificationTemplate
	public static final QName TYPE_NOTIFICATION_TEMPLATE = QName.createQName(DMS_NAMESPACE, "notificationTemplate");
	public static final QName PROP_NOTIFICATION_TYPE = QName.createQName(DMS_NAMESPACE, "notificationType");
	public static final QName PROP_TASK_NAME = QName.createQName(DMS_NAMESPACE, "taskName");
	public static final QName PROP_WORKFLOW_NAME = QName.createQName(DMS_NAMESPACE, "workflowName");
	public static final QName PROP_TITLE_FOR_RESENDING = QName.createQName(DMS_NAMESPACE, "titleForResending");
	public static final QName PROP_SUBJECT_TEMPLATE = QName.createQName(DMS_NAMESPACE, "subjectTemplate");
	public static final QName PROP_DOC_TYPE = QName.createQName(DMS_NAMESPACE, "documentType");
    public static final QName PROP_NOT_SEARCHABLE = QName.createQName(DMS_NAMESPACE, "notSearchable");
    public static final QName PROP_ECOS_TYPE = QName.createQName(DMS_NAMESPACE, "ecosType");
    public static final QName PROP_ECOS_NOTIFICATION_TEMPLATE = QName.createQName(DMS_NAMESPACE, "ecosNotificationTemplate");
    public static final QName PROP_NOTIFICATION_DISABLED = QName.createQName(DMS_NAMESPACE, "notificationDisabled");

	// aspect hasApplicationFiles
	public static final QName ASPECT_HAS_APPLICATION_FILES = QName.createQName(DMS_NAMESPACE, "hasApplicationFiles");
	public static final QName ASSOC_APPLICATION_FILES = QName.createQName(DMS_NAMESPACE, "applicationFiles");
	public static final QName ASSOC_VERSIONED_APPLICATION_FILES = QName.createQName(DMS_NAMESPACE, "versionedApplicationFiles");

    public static final QName ASPECT_HAS_PREDICATE = QName.createQName(DMS_NAMESPACE, "hasPredicate");
    public static final QName PROP_PREDICATE = QName.createQName(DMS_NAMESPACE, "predicate");
    public static final QName ASPECT_HAS_PRIORITY = QName.createQName(DMS_NAMESPACE, "hasPriority");
    public static final QName PROP_PRIORITY = QName.createQName(DMS_NAMESPACE, "priority");
}
