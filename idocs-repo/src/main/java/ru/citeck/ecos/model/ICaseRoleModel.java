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
 * Created by maxim.strizhov on 12.10.2015.
 */
public class ICaseRoleModel {
    // model
    public static final String PREFIX = "icaseRole";

    // namespace
    public static final String NAMESPACE = "http://www.citeck.ru/model/icaseRole/1.0";

    // types
    public static final QName TYPE_ROLE = QName.createQName(NAMESPACE, "role");
    public static final QName TYPE_SCRIPT_ROLE = QName.createQName(NAMESPACE, "scriptRole");

    // aspects

    public static final QName ASPECT_HAS_ROLES = QName.createQName(NAMESPACE, "hasRoles");

    // properties
    public static final QName PROP_VARNAME = QName.createQName(NAMESPACE, "varName");
    public static final QName PROP_IS_REFERENCE_ROLE = QName.createQName(NAMESPACE, "isReferenceRole");
    public static final QName PROP_SCRIPT = QName.createQName(NAMESPACE, "script");
    public static final QName PROP_DELEGATES = QName.createQName(NAMESPACE, "delegates");
    public static final QName PROP_ROLES_ASSIGNEES = QName.createQName(NAMESPACE, "rolesAssignees");

    // association
    public static final QName ASSOC_ASSIGNEES = QName.createQName(NAMESPACE, "assignees");
    public static final QName ASSOC_ROLES = QName.createQName(NAMESPACE, "roles");
    public static final QName ASSOC_REFERENCE_ROLE = QName.createQName(NAMESPACE, "referenceRoleAssoc");

    // constraint
}
