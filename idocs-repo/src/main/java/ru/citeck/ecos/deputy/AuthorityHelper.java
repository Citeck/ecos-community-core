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
package ru.citeck.ecos.deputy;

import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.AuthorityType;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.namespace.QName;

import ru.citeck.ecos.orgstruct.OrgStructService;

public class AuthorityHelper {

    private NodeService nodeService;
    private AuthorityService authorityService;
    private PersonService personService;
    private OrgStructService orgStructService;
    private String roleGroupType;

    public void setAuthorityService(AuthorityService authorityService) {
        this.authorityService = authorityService;
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setPersonService(PersonService personService) {
        this.personService = personService;
    }

    public void setOrgStructService(OrgStructService orgStructService) {
        this.orgStructService = orgStructService;
    }

    public void setRoleGroupType(String roleGroupType) {
        this.roleGroupType = roleGroupType;
    }

    public NodeRef getUser(String fullAuthorityName) {
        AuthorityType type = AuthorityType.getAuthorityType(fullAuthorityName);
        if (type.equals(AuthorityType.USER)) {
            return personService.getPerson(fullAuthorityName);
        } else {
            return null;
        }
    }

    public NodeRef getGroup(String fullAuthorityName) {
        AuthorityType type = AuthorityType.getAuthorityType(fullAuthorityName);
        if (type.equals(AuthorityType.GROUP)) {
            return authorityService.getAuthorityNodeRef(fullAuthorityName);
        } else {
            return null;
        }
    }

    public NodeRef getAuthority(String fullAuthorityName) {
        AuthorityType type = AuthorityType.getAuthorityType(fullAuthorityName);
        if (type.equals(AuthorityType.USER)) {
            return personService.getPerson(fullAuthorityName);
        } else {
            return authorityService.getAuthorityNodeRef(fullAuthorityName);
        }
    }

    public String getAuthorityName(NodeRef authority) {
        QName authorityType = nodeService.getType(authority);
        if (authorityType.equals(ContentModel.TYPE_PERSON)) {
            return (String) nodeService.getProperty(authority, ContentModel.PROP_USERNAME);
        } else if (authorityType.equals(ContentModel.TYPE_AUTHORITY_CONTAINER)) {
            return (String) nodeService.getProperty(authority, ContentModel.PROP_AUTHORITY_NAME);
        } else {
            return null;
        }
    }

    public NodeRef getRole(String fullAuthorityName) {
        NodeRef nodeRef = getGroup(fullAuthorityName);
        if (nodeRef == null) {
            return null;
        }

        // group type should be role
        String groupType = orgStructService.getGroupType(fullAuthorityName);
        if (!roleGroupType.equals(groupType)) {
            return null;
        }

        return nodeRef;
    }

    public boolean isUser(String fullAuthorityName) {
        return getUser(fullAuthorityName) != null;
    }

    public boolean isGroup(String fullAuthorityName) {
        return getGroup(fullAuthorityName) != null;
    }

    public boolean isRole(String fullAuthorityName) {
        return getRole(fullAuthorityName) != null;
    }

    public NodeRef needUser(String fullAuthorityName) {
        NodeRef person = getUser(fullAuthorityName);
        if (person == null) {
            throw new IllegalArgumentException("No such user: " + fullAuthorityName);
        }
        return person;
    }

    public NodeRef needGroup(String fullAuthorityName) {
        NodeRef group = getGroup(fullAuthorityName);
        if (group == null) {
            throw new IllegalArgumentException("No such group: " + fullAuthorityName);
        }
        return group;
    }

    public NodeRef needAuthority(String fullAuthorityName) {
        NodeRef group = getAuthority(fullAuthorityName);
        if (group == null) {
            throw new IllegalArgumentException("No such authority: " + fullAuthorityName);
        }
        return group;
    }

    public NodeRef needRole(String fullAuthorityName) {
        NodeRef role = getRole(fullAuthorityName);
        if (role == null) {
            throw new IllegalArgumentException("No such role: " + fullAuthorityName);
        }
        return role;
    }

}
