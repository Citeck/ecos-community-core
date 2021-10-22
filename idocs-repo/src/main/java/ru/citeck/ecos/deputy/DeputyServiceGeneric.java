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

import java.util.List;

/**
 * Deputy Service is a service for managing user deputies and role deputies.
 * It contains basic get/add/remove operations for user deputies and role deputies.
 * <p>
 * User deputies for user A are other users, that do work for user A, while he is absent (not available).
 * Role deputies for role A are users, that do work for role A, when it is necessary
 * (e.g. there are no available members of the role, or not enough of them).
 *
 * @author Sergey Tiunov
 */
public interface DeputyServiceGeneric<RoleList, UserList, UserNameList> {

    /////////////////////////////////////////////////////////////////
    //                      ADMIN INTERFACE                        //
    /////////////////////////////////////////////////////////////////

    /**
     * Get deputies (users) of specified user.
     *
     * @param userName - user, that is deputied
     * @return list of users, that are deputies for specified user
     */
    UserList getUserDeputies(String userName);

    /**
     * Get assistants (users) of specified user.
     *
     * @param userName - user, that have assistants
     * @return list of users, that are assistants for specified user
     */
    UserList getUserAssistants(String userName);

    /**
     * Get deputies and assistants (users) of specified user.
     *
     * @param userName - user, that have assistants and deputies
     * @return list of users, that are assistants and deputies for specified user
     */
    UserList getAllUserDeputies(String userName);

    /**
     * Add specified users to deputies of specified user.
     *
     * @param userName - user, that is deputied
     * @param deputies - list of users, that deputy
     */
    void addUserDeputies(String userName,
                         UserNameList deputies);

    /**
     * Add specified users to assistants of specified user.
     *
     * @param userName   - user, that is deputied
     * @param assistants - list of assistant users
     */
    void addUserAssistants(String userName,
                           UserNameList assistants);

    /**
     * Remove specified users from deputies of specified user.
     *
     * @param userName - user, that is deputied
     * @param deputies - list of users, that deputy
     */
    void removeUserDeputies(String userName,
                            UserNameList deputies);

    /**
     * Remove specified users from assistannts of specified user.
     *
     * @param userName - user, that is deputied
     * @param deputies - list of users, that assistants
     */
    void removeUserAssistants(String userName,
                              UserNameList deputies);


    /**
     * Get users who have specified user as deputy
     *
     * @param userName - user who is deputied
     * @return list of users, who have specified user as deputy
     */
    UserList getUsersWhoHaveThisUserDeputy(String userName);

    /**
     * Get deputies (users) of specified role.
     *
     * @param roleFullName - role, that is deputied
     * @return list of users, that are deputies for specified role
     */
    UserList getRoleDeputies(String roleFullName);

    /**
     * Get assistants (users) of specified role.
     *
     * @param roleFullName - role, that is assistant
     * @return list of users, that are assistants for specified role
     */
    UserList getRoleAssistants(String roleFullName);

    /**
     * Add specified users to deputies of specified role.
     *
     * @param roleFullName - role, that is deputied
     * @param deputies     - list of users, that deputy
     */
    void addRoleDeputies(String roleFullName,
                         UserNameList deputies);

    /**
     * Add specified users to assistants of specified role.
     *
     * @param roleFullName - role, that is assistant
     * @param deputies     - list of users, that assistant
     */
    void addRoleAssistants(String roleFullName,
                           UserNameList deputies);

    /**
     * Remove specified users from deputies of specified role.
     *
     * @param roleFullName - role, that is deputied
     * @param deputies     - list of users, that deputy
     */
    void removeRoleDeputies(String roleFullName,
                            UserNameList deputies);

    /**
     * Remove specified users from assistants of specified role.
     *
     * @param roleFullName - role, that is assistants
     * @param deputies     - list of users, that assistant
     */
    void removeRoleAssistants(String roleFullName,
                              UserNameList deputies);

    /**
     * Check if role can be deputied by its members.
     * Note. If this flag is set to true, it means that
     * the full members of the role (non-deputies)
     * can set deputies for this role.
     * If this flag is set to false, it means that
     * only system administrator can do it.
     *
     * @param roleFullName - role to check
     * @return true, if role can be deputied by its members, false otherwise
     */
    boolean isRoleDeputiedByMembers(String roleFullName);

    /**
     * Check if the role can be deputied by specified user.
     * This means that specified user can set role deputies.
     *
     * @param roleFullName - role full name (e.g. "GROUP_citeck_director")
     * @param userName     - user name (e.g. "admin")
     * @return - true, if specified role can be deputied by specified user
     */
    boolean isRoleDeputiedByUser(String roleFullName,
                                 String userName);

    /**
     * Check if the role is deputied to specified user.
     *
     * @param roleFullName
     * @param userName
     * @return
     */
    boolean isRoleDeputiedToUser(String roleFullName,
                                 String userName);

    boolean isRoleAssistedToUser(String roleFullName,
                                 String userName);

    /**
     * Get own roles of user.
     * This means the roles, in which he is a full member (i.e. not deputied roles).
     *
     * @param userName
     * @return - list of user roles, in which he is a full member
     */
    RoleList getUserRoles(String userName);

    /**
     * Get own branches of user.
     * This means the branches, in which he is a member
     *
     * @param userName
     * @return - list of user branches, in which he is a member
     */
    RoleList getUserBranches(String userName);

    /**
     * Get full members of specified role.
     * Full member is a member, that is not deputy.
     *
     * @param roleFullName
     * @return - list of users, that are full members of specified role
     */
    UserList getRoleMembers(String roleFullName);

    /**
     * Get list of roles, that can be deputied by specified user.
     * This means that specified user can set role deputies.
     *
     * @param userName
     * @return - list of roles, that can be deputied by user.
     */
    RoleList getRolesDeputiedByUser(String userName);

    /**
     * Get list of roles, that are deputied to specified user.
     *
     * @param userName
     * @return list of roles, that are deputied to specified user.
     */
    RoleList getRolesDeputiedToUser(String userName);

    /////////////////////////////////////////////////////////////////
    //                  CURRENT USER INTERFACE                     //
    /////////////////////////////////////////////////////////////////

    /**
     * @see #getUserDeputies(String)
     */
    UserList getCurrentUserDeputies();

    UserList getCurrentUserAssistants();

    UserList getAllCurrentUserDeputies();

    /**
     * @see #addUserDeputies(String, List)
     */
    void addCurrentUserDeputies(UserNameList deputies);

    /**
     * @see #addUserAssistants(String, Object)
     */
    void addCurrentUserAssistants(UserNameList assistants);

    /**
     * Is user is assistant depute
     *
     * @param userName
     * @param assistantUserName
     * @return is user assistant depute
     */
    boolean isAssistantUserByUser(String userName, String assistantUserName);

    boolean isAssistantToCurrentUser(String assistantUserName);


    /**
     * @see #removeUserDeputies(String, List)
     */
    void removeCurrentUserDeputies(UserNameList deputies);

    void removeCurrentUserAssistants(UserNameList assistance);

    /**
     * @see #isRoleDeputiedByUser(String, String)
     */
    boolean isRoleDeputiedByCurrentUser(String roleFullName);

    /**
     * @see #isRoleDeputiedToUser(String, String)
     */
    boolean isRoleDeputiedToCurrentUser(String roleFullName);

    /**
     * @see #getUserRoles(String)
     */
    RoleList getCurrentUserRoles();

    /**
     * @see #getUserBranches(String)
     */
    RoleList getCurrentUserBranches();

    /**
     * @see #getRolesDeputiedByUser(String)
     */
    RoleList getRolesDeputiedByCurrentUser();

    /**
     * @see #getRolesDeputiedToUser(String)
     */
    RoleList getRolesDeputiedToCurrentUser();

    boolean isUserAvailable(String userName);

    boolean isCanDeleteDeputeOrAssistantFromRole(String roleFullName);
}
