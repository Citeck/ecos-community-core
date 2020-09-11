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
package ru.citeck.ecos.role;

import org.alfresco.service.cmr.repository.NodeRef;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Maxim Strizhov
 * @author Pavel Simonov
 */
public interface CaseRoleService {

    /**
     * Get all roles in specified case or empty list if caseRef is null or not exists
     * @return list of roles
     */
    List<NodeRef> getRoles(NodeRef caseRef);

    /**
     * Get role by name in specified case
     * @return role or null if role not found
     * @throws IllegalArgumentException if name is null or empty
     */
    NodeRef getRole(NodeRef caseRef, String name);

    /**
     * Get roles by user in specified case
     * @return roles or empty list
     * @throws IllegalArgumentException if name is null or empty
     */
    List<String> getUserRoles(NodeRef caseRef, String userName);

    /**
     * Get role assignees by case reference and role name
     * @return role assignees
     * @throws IllegalArgumentException if role with specified name not found in case
     */
    Set<NodeRef> getAssignees(NodeRef caseRef, String roleName);

    /**
     * Get role assignees
     * @return role assignees
     */
    Set<NodeRef> getAssignees(NodeRef roleRef);

    /**
     * Check authority belongs to the role or not
     */
    boolean isRoleMember(NodeRef roleRef, NodeRef authorityRef);

    /**
     * Check authority belongs to the role or not
     * @param immediate if true, limit the depth to just immediate child, if false find authorities at any depth
     */
    boolean isRoleMember(NodeRef roleRef, NodeRef authorityRef, boolean immediate);

    /**
     * Check authority belongs to the role or not.
     * @throws IllegalArgumentException if role with specified name not found in case
     */
    boolean isRoleMember(NodeRef caseRef, String roleName, NodeRef authorityRef);

    /**
     * Check authority belongs to the role or not
     * @param immediate if true, limit the depth to just immediate child, if false find authorities at any depth
     * @throws IllegalArgumentException if role with specified name not found in case
     */
    boolean isRoleMember(NodeRef caseRef, String roleName, NodeRef authorityRef, boolean immediate);

    /**
     * Search role by name and set assignees
     * @throws IllegalArgumentException if role with specified name not found in case
     */
    void setAssignees(NodeRef caseRef, String roleName, Collection<NodeRef> assignees);

    /**
     * Set role assignees
     */
    void setAssignees(NodeRef roleRef, Collection<NodeRef> assignees);


    /**
     * Add role assignees
     */
    void addAssignees(NodeRef roleRef, NodeRef... assignees);

    /**
     * Add role assignees
     * @throws IllegalArgumentException if role with specified name not found in case
     */
    void addAssignees(NodeRef caseRef, String roleName, NodeRef... assignees);

    /**
     * Add role assignees
     */
    void addAssignees(NodeRef roleRef, Collection<NodeRef> assignees);

    /**
     * Add role assignees
     * @throws IllegalArgumentException if role with specified name not found in case
     */
    void addAssignees(NodeRef caseRef, String roleName, Collection<NodeRef> assignees);

    /**
     * Remove all assignees from role
     */
    void removeAssignees(NodeRef roleRef);

    /**
     * Search role by name and remove all assignees
     * @throws IllegalArgumentException if role with specified name not found in case
     */
    void removeAssignees(NodeRef caseRef, String roleName);

    /**
     * Recalculate assignees for all dynamic roles in case
     */
    void updateRoles(NodeRef caseRef);

    /**
     * Recalculate assignees for role
     */
    void updateRole(NodeRef roleRef);

    /**
     * Search role by name and recalculate assignees
     * @throws IllegalArgumentException if role with specified name not found in case
     */
    void updateRole(NodeRef caseRef, String roleName);

    void roleChanged(NodeRef roleRef, NodeRef added, NodeRef removed);

    /**
     * Set assignee delegate for role. This delegate would be set
     * as assignee in dynamic roles instead of specified assignee
     *
     * If result delegates is looped (e.g A->B->C->A),
     * then these delegates will be removed as unnecessary
     *
     * @param assignee authority for replacement
     * @param delegate authority which set instead of specified assignee
     */
    void setDelegate(NodeRef roleRef, NodeRef assignee, NodeRef delegate);

    /**
     * Set assignees delegates for role. This delegates would be set
     * as assignees in dynamic roles instead of specified assignees
     *
     * If result delegates is looped (e.g A->B->C->A),
     * then this delegates will removed as unnecessary
     *
     * @param delegates is a map where key is authority for replacement and value is
     *                  authority which set instead of the authority in key
     */
    void setDelegates(NodeRef roleRef, Map<NodeRef, NodeRef> delegates);

    /**
     * Remove delegate for authority
     * @param assignee authority
     */
    void removeDelegate(NodeRef roleRef, NodeRef assignee);

    /**
     * Remove all delegates for role
     */
    void removeDelegates(NodeRef roleRef);

    /**
     * Get delegates and fix corrupted data if needed
     */
    Map<NodeRef, NodeRef> getDelegates(NodeRef roleRef);
}
