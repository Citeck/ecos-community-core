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
package ru.citeck.ecos.workflow.security;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.workflow.WorkflowModel;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.cmr.workflow.WorkflowTask;
import org.alfresco.service.cmr.workflow.WorkflowTaskState;
import org.alfresco.service.namespace.QName;
import ru.citeck.ecos.utils.SimpleMethodInterceptor;

import java.io.Serializable;
import java.util.*;

public class WorkflowSecurityInterceptor extends SimpleMethodInterceptor {

    private PersonService personService;
    private AuthorityService authorityService;
    private NodeService nodeService;
    private Properties globalProperties;
    private String propertyFormat;
    private String allowedGroup;

    private Map<WorkflowPermission, Set<WorkflowUserStatus>> allowedPermissions;

    public boolean isTaskEditable(WorkflowTask task, String username) {
        return hasTaskPermission(task, username, WorkflowPermission.TASK_EDIT);
    }

    public boolean isTaskEditable(WorkflowTask task, String username, Boolean refreshTask)
    {
        return isTaskEditable(task, username);
    }

    public boolean isTaskReassignable(WorkflowTask task, String username) {
        // check reassignable flag
        Map<QName, Serializable> properties = task.getProperties();
        Boolean reassignable = (Boolean)properties.get(WorkflowModel.PROP_REASSIGNABLE);
        if (Boolean.FALSE.equals(reassignable)) {
            return false;
        }

        return hasTaskPermission(task, username, WorkflowPermission.TASK_REASSIGN);
    }

    public boolean isTaskReassignable(WorkflowTask task, String username, Boolean refreshTask)
    {
        return isTaskReassignable(task, username);
    }

    public boolean isTaskClaimable(WorkflowTask task, String username) {
        return hasTaskPermission(task, username, WorkflowPermission.TASK_CLAIM);
    }

    public boolean isTaskClaimable(WorkflowTask task, String username, Boolean refreshTask)
    {
        return isTaskClaimable(task, username);
    }


    public boolean isTaskReleasable(WorkflowTask task, String username) {
        return hasTaskPermission(task, username, WorkflowPermission.TASK_RELEASE);
    }

    public boolean isTaskReleasable(WorkflowTask task, String username, Boolean refreshTask)
    {
        return isTaskReleasable(task, username);
    }

    private boolean hasTaskPermission(WorkflowTask task, String username, WorkflowPermission permission) {
        if (task.getState() == WorkflowTaskState.COMPLETED) {
            return false;
        }

        if (allowedGroup != null && checkAllowedGroup(username)) {
            return true;
        }

        Set<WorkflowUserStatus> allowedStatuses = allowedPermissions.get(permission);
        return hasUserAnyStatus(username, task, allowedStatuses);
    }

    private boolean checkAllowedGroup(String username) {
        return authorityService.getAuthoritiesForUser(username).contains(allowedGroup);
    }

    private boolean hasUserAnyStatus(String username, WorkflowTask task, Set<WorkflowUserStatus> statuses) {
        Map<QName, Serializable> taskProperties = task.getProperties();
        String ownerName = (String) taskProperties.get(ContentModel.PROP_OWNER);

        if (statuses.contains(WorkflowUserStatus.OWNER) && username.equals(ownerName)) {
            return true;
        }

        if (statuses.contains(WorkflowUserStatus.ADMIN) && authorityService.isAdminAuthority(username)) {
            return true;
        }

        NodeRef userNodeRef = null;
        if (statuses.contains(WorkflowUserStatus.INITIATOR)) {
            userNodeRef = personService.getPerson(username);
            if (task.getPath().getInstance().getInitiator().equals(userNodeRef)) {
                return true;
            }
        }

        if(statuses.contains(WorkflowUserStatus.POOL_WITH_OWNER) && ownerName != null
        || statuses.contains(WorkflowUserStatus.POOL_WITHOUT_OWNER) && ownerName == null)
        {
            if(userNodeRef == null) userNodeRef = personService.getPerson(username);
            List<?> pooledActors = (List<?>) taskProperties.get(WorkflowModel.ASSOC_POOLED_ACTORS);
            if(pooledActors != null && !pooledActors.isEmpty()) {
                if(pooledActors.contains(userNodeRef)) {
                    return true;
                }
                Set<String> userAuthorities = authorityService.getAuthoritiesForUser(username);
                // in most cases there are less pooled actors, than user authorities
                if(!userAuthorities.isEmpty()) {
                    for(Object pooledActor : pooledActors) {
                        if(!(pooledActor instanceof NodeRef)) continue;
                        NodeRef pooledActorRef = (NodeRef) pooledActor;
                        if(!nodeService.exists(pooledActorRef)) continue;
                        String authorityName = (String) nodeService.getProperty(pooledActorRef, ContentModel.PROP_AUTHORITY_NAME);
                        if(authorityName == null) continue; // it could be user
                        if(userAuthorities.contains(authorityName)) return true;
                    }
                }
            }
        }

        return false;
    }

    public void setPersonService(PersonService personService) {
        this.personService = personService;
    }

    public void setAuthorityService(AuthorityService authorityService) {
        this.authorityService = authorityService;
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setGlobalProperties(Properties globalProperties) {
        this.globalProperties = globalProperties;
    }

    public void setPropertyFormat(String propertyFormat) {
        this.propertyFormat = propertyFormat;
    }

    public void init() {
        allowedPermissions = new EnumMap<>(WorkflowPermission.class);
        for(WorkflowPermission permission : WorkflowPermission.values()) {
            String propertyKey = String.format(propertyFormat, permission.toString().toLowerCase());
            String propertyValue = globalProperties.getProperty(propertyKey);

            EnumSet<WorkflowUserStatus> allowedStatuses = EnumSet.noneOf(WorkflowUserStatus.class);
            for(String statusString : propertyValue.split("[,]")) {
                allowedStatuses.add(WorkflowUserStatus.valueOf(statusString));
            }
            allowedPermissions.put(permission, allowedStatuses);
        }
        String propertyKey = String.format(propertyFormat, "group");
        allowedGroup = globalProperties.getProperty(propertyKey);
    }

}
