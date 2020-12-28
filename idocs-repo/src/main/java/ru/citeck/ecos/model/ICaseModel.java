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
public interface ICaseModel {

    // model
    String MODEL_PREFIX = "icase";

    // namespace
    String NAMESPACE = "http://www.citeck.ru/model/icase/1.0";

    // types
    QName TYPE_ELEMENT_CONFIG = QName.createQName(NAMESPACE, "elementConfig");
    QName TYPE_CLASS_CONFIG = QName.createQName(NAMESPACE, "classConfig");
    QName TYPE_KEY_PROP_CONFIG = QName.createQName(NAMESPACE, "keyPropConfig");
    QName TYPE_ASSOC_CONFIG = QName.createQName(NAMESPACE, "assocConfig");

    // aspects
    QName ASPECT_CASE = QName.createQName(NAMESPACE, "case");
    QName ASPECT_CASE_TEMPLATE = QName.createQName(NAMESPACE, "caseTemplate");
    QName ASPECT_COPIED_FROM_TEMPLATE = QName.createQName(NAMESPACE, "copiedFromTemplate");
    QName ASPECT_HAS_DOCUMENTS = QName.createQName(NAMESPACE, "hasDocuments");

    // properties
    QName PROP_CASE_CLASS = QName.createQName(NAMESPACE, "caseClass");
    QName PROP_ELEMENT_TYPE = QName.createQName(NAMESPACE, "elementType");
    QName PROP_COPY_ELEMENTS = QName.createQName(NAMESPACE, "copyElements");
    QName PROP_ELEMENT_KEY = QName.createQName(NAMESPACE, "elementKey");
    QName PROP_CASE_KEY = QName.createQName(NAMESPACE, "caseKey");
    QName PROP_ASSOC_NAME = QName.createQName(NAMESPACE, "assocName");
    QName PROP_ASSOC_TYPE = QName.createQName(NAMESPACE, "assocType");
    QName PROP_CASE_FOLDER_ASSOC_TYPE = QName.createQName(NAMESPACE, "caseFolderAssocName");
    QName PROP_FOLDER_NAME = QName.createQName(NAMESPACE, "folderName");
    QName PROP_FOLDER_TYPE = QName.createQName(NAMESPACE, "folderType");
    QName PROP_FOLDER_ASSOC_TYPE = QName.createQName(NAMESPACE, "folderAssocName");
    QName PROP_ELEMENT_FOLDER = QName.createQName(NAMESPACE, "elementFolder");
    QName PROP_TYPE_KIND = QName.createQName(NAMESPACE, "typeKind");
    QName PROP_CASE_ECOS_KIND = QName.createQName(NAMESPACE, "caseEcosKind");
    QName PROP_CASE_ECOS_TYPE = QName.createQName(NAMESPACE, "caseEcosType");
    QName PROP_LAST_CHANGED_DATE = QName.createQName(NAMESPACE, "lastChangedDate");

    // icase:subcase
    QName ASPECT_SUBCASE = QName.createQName(NAMESPACE, "subcase");
    QName ASSOC_SUBCASE_ELEMENT = QName.createQName(NAMESPACE, "subcaseElement");
    QName ASSOC_SUBCASE_ELEMENT_CONFIG = QName.createQName(NAMESPACE, "subcaseElementConfig");
    QName ASSOC_PARENT_CASE = QName.createQName(NAMESPACE, "parentCase");

    // subcase configuration properties
    QName PROP_CREATE_SUBCASE = QName.createQName(NAMESPACE, "createSubcase");
    QName PROP_REMOVE_SUBCASE = QName.createQName(NAMESPACE, "removeSubcase");
    QName PROP_REMOVE_EMPTY_SUBCASE = QName.createQName(NAMESPACE, "removeEmptySubcase");
    QName PROP_SUBCASE_TYPE = QName.createQName(NAMESPACE, "subcaseType");
    QName PROP_SUBCASE_ASSOC = QName.createQName(NAMESPACE, "subcaseAssoc");

    // case element aspect
    QName ASPECT_ELEMENT = QName.createQName(NAMESPACE, "element");

    // category element config:
    QName TYPE_CATEGORY_CONFIG = QName.createQName(NAMESPACE, "categoryConfig");
    QName PROP_CATEGORY_PROPERTY = QName.createQName(NAMESPACE, "categoryProperty");

    QName TYPE_CASE_TEMPLATE = QName.createQName(NAMESPACE, "template");
    QName PROP_CASE_TYPE = QName.createQName(NAMESPACE, "caseType");
    QName PROP_CONDITION = QName.createQName(NAMESPACE, "condition");

    QName TYPE_CASE_STATUS = QName.createQName(NAMESPACE, "caseStatus");
    QName ASSOC_CASE_STATUS = QName.createQName(NAMESPACE, "caseStatusAssoc");
    QName ASSOC_CASE_STATUS_BEFORE = QName.createQName(NAMESPACE, "caseStatusBeforeAssoc");
    QName PROP_CASE_STATUS_CHANGED_DATETIME = QName.createQName(NAMESPACE, "caseStatusChangedDateTime");
    QName ASSOC_CASE_STATUS_PROP = QName.createQName(NAMESPACE, "caseStatusAssoc-prop");
    QName ASSOC_CASE_STATUS_BEFORE_PROP = QName.createQName(NAMESPACE, "caseStatusBeforeAssoc-prop");

    QName ASSOC_DOCUMENTS = QName.createQName(NAMESPACE, "documents");
}
