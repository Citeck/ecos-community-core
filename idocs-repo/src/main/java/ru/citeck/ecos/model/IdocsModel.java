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
 * @author: Alexander Nemerov
 * @date: 27.01.14
 */
public interface IdocsModel {

    // model
    String IDOCS_MODEL_PREFIX = "idocs";

    // namespace
    String IDOCS_NAMESPACE = "http://www.citeck.ru/model/content/idocs/1.0";

    // types
    QName TYPE_DOC = QName.createQName(IDOCS_NAMESPACE, "doc");
    QName TYPE_INTERNAL = QName.createQName(IDOCS_NAMESPACE, "internal");
    QName TYPE_ATTORNEY = QName.createQName(IDOCS_NAMESPACE, "powerOfAttorney");
    QName TYPE_LEGAL_ENTITY = QName.createQName(IDOCS_NAMESPACE, "legalEntity");
    QName TYPE_CURRENCY = QName.createQName(IDOCS_NAMESPACE, "currency");
    QName TYPE_CURRENCY_RATE_RECORD = QName.createQName(IDOCS_NAMESPACE, "currencyRateRecord");
    QName TYPE_CURRENCY_RATE_INTERNAL_RECORD = QName.createQName(IDOCS_NAMESPACE, "currencyRateInternalRecord");
    QName TYPE_CURRENCY_RATES_XML = QName.createQName(IDOCS_NAMESPACE, "currencyRatesXML");
    QName TYPE_ABSTRACT_ATTORNEY = QName.createQName(IDOCS_NAMESPACE, "abstractAttorney");
    QName TYPE_ABSTRACT_CONTRACTOR = QName.createQName(IDOCS_NAMESPACE, "abstractContractor");
    QName TYPE_CONTRACTOR = QName.createQName(IDOCS_NAMESPACE, "contractor");
    QName TYPE_COUNTRY = QName.createQName(IDOCS_NAMESPACE, "countryISO3166");
    QName TYPE_GROUP_OF_COUNTRIES = QName.createQName(IDOCS_NAMESPACE, "groupOfCountries");

    // aspects
    QName ASPECT_LIFECYCLE = QName.createQName(IDOCS_NAMESPACE, "lifeCycle");
    QName ASPECT_HAS_ATTORNEYS = QName.createQName(IDOCS_NAMESPACE, "hasAttorneys");
    QName ASPECT_HAS_CONTRACTOR = QName.createQName(IDOCS_NAMESPACE, "hasContractor");
    QName ASPECT_HAS_RESPONSIBILITIES = QName.createQName(IDOCS_NAMESPACE, "hasResponsibilities");
    QName ASPECT_HAS_CUSTOM_FORM_ACTION = QName.createQName(IDOCS_NAMESPACE, "hasCustomFormAction");

    // properties
    QName PROP_REGISTRATION_DATE = QName.createQName(IDOCS_NAMESPACE, "registrationDate");
    QName PROP_REGISTRATION_NUMBER = QName.createQName(IDOCS_NAMESPACE, "registrationNumber");
    QName PROP_DOCUMENT_STATUS = QName.createQName(IDOCS_NAMESPACE, "documentStatus");
    QName PROP_CODE = QName.createQName(IDOCS_NAMESPACE, "code");
    QName PROP_FULL_NAME = QName.createQName(IDOCS_NAMESPACE, "fullName");
    QName PROP_FULL_ORG_NAME = QName.createQName(IDOCS_NAMESPACE, "fullOrganizationName");
    QName PROP_LEGAL_ADDRESS = QName.createQName(IDOCS_NAMESPACE, "legalAddress");
    QName PROP_LEGAL_FORM_NAME = QName.createQName(IDOCS_NAMESPACE, "legalFormName");
    QName PROP_PHONE_NUMBER = QName.createQName(IDOCS_NAMESPACE, "phoneNumber");
    QName PROP_OKPO = QName.createQName(IDOCS_NAMESPACE, "okpo");
    QName PROP_OGRN = QName.createQName(IDOCS_NAMESPACE, "ogrn");
    QName PROP_INN = QName.createQName(IDOCS_NAMESPACE, "inn");
    QName PROP_KPP = QName.createQName(IDOCS_NAMESPACE, "kpp");
    QName PROP_SHORT_ORGANIZATION_NAME = QName.createQName(IDOCS_NAMESPACE, "shortOrganizationName");
    QName PROP_COUNTRY_NAME = QName.createQName(IDOCS_NAMESPACE, "countryName");
    QName PROP_POST_CODE = QName.createQName(IDOCS_NAMESPACE, "postCode");
    QName PROP_REGION_NAME = QName.createQName(IDOCS_NAMESPACE, "regionName");
    QName PROP_DISTRICT_NAME = QName.createQName(IDOCS_NAMESPACE, "districtName");
    QName PROP_CITY_NAME = QName.createQName(IDOCS_NAMESPACE, "cityName");
    QName PROP_STREET_NAME = QName.createQName(IDOCS_NAMESPACE, "streetName");
    QName PROP_HOUSE = QName.createQName(IDOCS_NAMESPACE, "house");
    QName PROP_ADDRESS_EXTRA_INFO = QName.createQName(IDOCS_NAMESPACE, "addressExtraInfo");
    QName PROP_CURRENCY_CODE = QName.createQName(IDOCS_NAMESPACE, "currencyCode");
    QName PROP_CURRENCY_NUMBER_CODE = QName.createQName(IDOCS_NAMESPACE, "currencyNumberCode");
    QName PROP_CURRENCY_RATE = QName.createQName(IDOCS_NAMESPACE, "currencyRate");
    QName PROP_CRR_VALUE = QName.createQName(IDOCS_NAMESPACE, "crrValue");
    QName PROP_CRR_DATE = QName.createQName(IDOCS_NAMESPACE, "crrDate");
    QName PROP_CRR_SYNC_DATE = QName.createQName(IDOCS_NAMESPACE, "crrSyncDate");
    QName PROP_CRR_INTERNAL_VALUE = QName.createQName(IDOCS_NAMESPACE, "crrInternalValue");
    QName PROP_CRR_INTERNAL_MONTH = QName.createQName(IDOCS_NAMESPACE, "crrInternalMonth");
    QName PROP_CRR_INTERNAL_YEAR = QName.createQName(IDOCS_NAMESPACE, "crrInternalYear");
    QName PROP_CURRENCY_NAME_RU = QName.createQName(IDOCS_NAMESPACE, "currencyNameRu");
    QName PROP_CURRENCY_CATALOG_CODE = QName.createQName(IDOCS_NAMESPACE, "currencyCatalogCode");
    @Deprecated
    QName PROP_DIADOC_BOX_ID = QName.createQName(IDOCS_NAMESPACE, "diadocBoxId");
    QName PROP_CONTRACTOR = QName.createQName(IDOCS_NAMESPACE, "contractor");
    QName PROP_SIGN_OUR_REQUIRED = QName.createQName(IDOCS_NAMESPACE, "signOurRequired");
    QName PROP_SIGN_CA_REQUIRED = QName.createQName(IDOCS_NAMESPACE, "signCARequired");
    QName PROP_TOTAL_SUM = QName.createQName(IDOCS_NAMESPACE, "totalSum");
    QName PROP_VAT_PART = QName.createQName(IDOCS_NAMESPACE, "vatPart");
    QName PROP_COMMENT = QName.createQName(IDOCS_NAMESPACE, "comment");
    QName PROP_DOCUMENT_CASE_COMPLETED = QName.createQName(IDOCS_NAMESPACE, "caseCompleted");
    QName PROP_CASE_MODELS_SENT = QName.createQName(IDOCS_NAMESPACE, "caseModelsSent");
    QName PROP_ATTACHMENT_STATE = QName.createQName(IDOCS_NAMESPACE, "attachmentState");
    QName PROP_ABSTRACT_CONTRACTOR = QName.createQName(IDOCS_NAMESPACE, "abstractContractor");
    QName PROP_CUSTOM_FORM_ACTION_DATA = QName.createQName(IDOCS_NAMESPACE, "customFormActionData");
    QName PROP_USE_NEW_HISTORY = QName.createQName(IDOCS_NAMESPACE, "useNewHistory");
    QName PROP_ENG_ORGANIZATION_NAME = QName.createQName(IDOCS_NAMESPACE, "engOrganizationName");
    QName PROP_ORGANIZATION_NAME = QName.createQName(IDOCS_NAMESPACE, "organizationName");
    QName PROP_MANAGER_GROUP = QName.createQName(IDOCS_NAMESPACE, "managerGroup");
    QName PROP_MANAGER_PERMISSION = QName.createQName(IDOCS_NAMESPACE, "managerPermission");
    QName PROP_COUNTRY_ISO3166_NAME = QName.createQName(IDOCS_NAMESPACE, "countryISO3166Name");
    QName PROP_COUNTRY_ISO3166_CODE = QName.createQName(IDOCS_NAMESPACE, "countryISO31661Code");
    QName PROP_GOC_NAME = QName.createQName(IDOCS_NAMESPACE, "gocName");
    QName PROP_GLN = QName.createQName(IDOCS_NAMESPACE, "gln");

    //assocs
    QName ASSOC_GENERAL_DIRECTOR = QName.createQName(IDOCS_NAMESPACE, "generalDirector");
    QName ASSOC_ACCOUNTANT_GENERAL = QName.createQName(IDOCS_NAMESPACE, "accountantGeneral");
    QName ASSOC_DOC_ATTORNEYS = QName.createQName(IDOCS_NAMESPACE, "docAttorneys");
    QName ASSOC_SIGNER = QName.createQName(IDOCS_NAMESPACE, "signatory");
    QName ASSOC_CURRENCY_DOCUMENT = QName.createQName(IDOCS_NAMESPACE, "currencyDocument");
    QName ASSOC_INITIATOR = QName.createQName(IDOCS_NAMESPACE, "initiator");
    QName ASSOC_LEGAL_FORM = QName.createQName(IDOCS_NAMESPACE, "legalForm");
    QName ASSOC_LEGAL_ENTITY = QName.createQName(IDOCS_NAMESPACE, "legalEntity");
    QName ASSOC_ORG_COUNTRY = QName.createQName(IDOCS_NAMESPACE, "orgCountry");
    QName ASSOC_ORG_CURRENCY = QName.createQName(IDOCS_NAMESPACE, "orgCurrency");

    QName ASSOC_CRR_BASE_CURRENCY = QName.createQName(IDOCS_NAMESPACE, "crrBaseCurrency");
    QName ASSOC_CRR_TARGET_CURRENCY = QName.createQName(IDOCS_NAMESPACE, "crrTargetCurrency");
    QName ASSOC_CRR_INTERNAL_BASE_CURRENCY = QName.createQName(IDOCS_NAMESPACE, "crrInternalBaseCurrency");
    QName ASSOC_CRR_INTERNAL_TARGET_CURRENCY = QName.createQName(IDOCS_NAMESPACE, "crrInternalTargetCurrency");
    QName ASSOC_GOC_COUNTRY = QName.createQName(IDOCS_NAMESPACE, "gocCountries");

    QName ASSOC_ATTACHMENT_RKK_CREATED_FROM = QName.createQName(IDOCS_NAMESPACE, "attachmentRkkCreatedFrom");
    QName ASSOC_CONTRACTOR = QName.createQName(IDOCS_NAMESPACE, "contractor");
    QName ASSOC_RESPONSIBILITIES = QName.createQName(IDOCS_NAMESPACE, "responsibilities");

    // constraints
    String CONSTR_REPEAL_BY_COUNTERPARTY_REQUESTED = "REPEAL_BY_COUNTERPARTY_REQUESTED";
    String CONSTR_REPEALED_BY_COUNTERPARTY = "REPEALED_BY_COUNTERPARTY";
    String CONSTR_CLARIFICATION_REQUESTED = "CLARIFICATION_REQUESTED";
    String CONSTR_SIGN_REQUIRED = "SIGN_REQUIRED";
    String CONSTR_RECEIVED = "RECEIVED";
    String CONSTR_SIGNED = "SIGNED";
    String CONSTR_COUNTERPARTY_SIGNED = "COUNTERPARTY_SIGNED";
    String CONSTR_COUNTERPARTY_SIGN_REQUESTED = "COUNTERPARTY_SIGN_REQUESTED";
    String DELIVERY_FAILED = "DELIVERY_FAILED";
    String REQUESTS_MY_REVOCATION = "REQUESTS_MY_REVOCATION";
    String REVOCATION_IS_REQUESTED_BY_ME = "REVOCATION_IS_REQUESTED_BY_ME";
    String REVOCATION_ACCEPTED = "REVOCATION_ACCEPTED";
    String REVOCATION_REJECTED = "REVOCATION_REJECTED";
    String REJECTION_SENT = "REJECTION_SENT";
    String REJECTED = "REJECTED";
    String REVISIONED = "REVISIONED";
    String CORRECTED = "CORRECTED";
    String REVISION_CORRECTED = "REVISION_CORRECTED";
}
