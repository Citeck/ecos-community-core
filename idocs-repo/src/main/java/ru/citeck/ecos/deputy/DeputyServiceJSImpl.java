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

import org.alfresco.repo.security.authority.script.ScriptGroup;
import org.alfresco.repo.security.authority.script.ScriptUser;
import org.alfresco.service.ServiceRegistry;
import ru.citeck.ecos.service.CiteckServices;
import ru.citeck.ecos.utils.AlfrescoScopableProcessorExtension;

import java.util.Arrays;
import java.util.Collections;

import static ru.citeck.ecos.utils.JavaScriptImplUtils.wrapGroups;
import static ru.citeck.ecos.utils.JavaScriptImplUtils.wrapUsers;

public class DeputyServiceJSImpl extends AlfrescoScopableProcessorExtension
    implements DeputyServiceGeneric<ScriptGroup[], ScriptUser[], String[]> {

    private DeputyService deputyService;

    @Override
    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        super.setServiceRegistry(serviceRegistry);
        this.deputyService = (DeputyService) serviceRegistry.getService(CiteckServices.DEPUTY_SERVICE);
    }

    /////////////////////////////////////////////////////////////////
    //                      ADMIN INTERFACE                        //
    /////////////////////////////////////////////////////////////////

    @Override
    public ScriptUser[] getUserDeputies(String userName) {
        return wrapUsers(deputyService.getUserDeputies(userName), this);
    }

    @Override
    public ScriptUser[] getUserAssistants(String userName) {
        return wrapUsers(deputyService.getUserAssistants(userName), this);
    }

    @Override
    public ScriptUser[] getAllUserDeputies(String userName) {
        return wrapUsers(deputyService.getAllUserDeputies(userName), this);
    }

    @Override
    public void addUserDeputies(String userName, String[] deputies) {
        deputyService.addUserDeputies(userName, Arrays.asList(deputies));
    }

    @Override
    public void addUserAssistants(String userName, String[] assistants) {
        deputyService.addUserAssistants(userName, Arrays.asList(assistants));
    }

    public void addUserDeputy(String userName, String deputyName) {
        deputyService.addUserDeputies(userName, Collections.singletonList(deputyName));
    }

    @Override
    public void removeUserDeputies(String userName, String[] deputies) {
        deputyService.removeUserDeputies(userName, Arrays.asList(deputies));
    }

    @Override
    public void removeUserAssistants(String userName, String[] assistants) {
        deputyService.removeUserAssistants(userName, Arrays.asList(assistants));
    }

    @Override
    public ScriptUser[] getUsersWhoHaveThisUserDeputy(String userName) {
        return wrapUsers(deputyService.getUsersWhoHaveThisUserDeputy(userName), this);
    }

    public void removeUserDeputy(String userName, String deputyName) {
        deputyService.removeUserDeputies(userName, Collections.singletonList(deputyName));
    }

    @Override
    public ScriptUser[] getRoleDeputies(String roleFullName) {
        return wrapUsers(deputyService.getRoleDeputies(roleFullName), this);
    }

    @Override
    public ScriptUser[] getRoleAssistants(String roleFullName) {
        return wrapUsers(deputyService.getRoleAssistants(roleFullName), this);
    }

    @Override
    public void addRoleDeputies(String roleFullName, String[] deputies) {
        deputyService.addRoleDeputies(roleFullName, Arrays.asList(deputies));
    }

    @Override
    public void addRoleAssistants(String roleFullName, String[] deputies) {
        deputyService.addRoleAssistants(roleFullName, Arrays.asList(deputies));
    }

    public void addRoleDeputy(String roleFullName, String deputyName) {
        deputyService.addRoleDeputies(roleFullName, Collections.singletonList(deputyName));
    }

    @Override
    public void removeRoleDeputies(String roleFullName, String[] deputies) {
        deputyService.removeRoleDeputies(roleFullName, Arrays.asList(deputies));
    }

    @Override
    public void removeRoleAssistants(String roleFullName, String[] deputies) {
        deputyService.removeRoleAssistants(roleFullName, Arrays.asList(deputies));
    }

    public void removeRoleDeputy(String roleFullName, String deputyName) {
        deputyService.removeRoleDeputies(roleFullName, Collections.singletonList(deputyName));
    }

    @Override
    public boolean isRoleDeputiedByMembers(String roleFullName) {
        return deputyService.isRoleDeputiedByMembers(roleFullName);
    }

    @Override
    public boolean isRoleDeputiedByUser(String roleFullName, String userName) {
        return deputyService.isRoleDeputiedByUser(roleFullName, userName);
    }

    @Override
    public boolean isRoleDeputiedToUser(String roleFullName, String userName) {
        return deputyService.isRoleDeputiedToUser(roleFullName, userName);
    }

    @Override
    public boolean isRoleAssistedToUser(String roleFullName, String userName) {
        return deputyService.isRoleAssistedToUser(roleFullName, userName);
    }

    @Override
    public ScriptGroup[] getUserRoles(String userName) {
        return wrapGroups(deputyService.getUserRoles(userName), this);
    }

    @Override
    public ScriptGroup[] getUserBranches(String userName) {
        return wrapGroups(deputyService.getUserBranches(userName), this);
    }

    @Override
    public ScriptUser[] getRoleMembers(String roleFullName) {
        return wrapUsers(deputyService.getRoleMembers(roleFullName), this);
    }

    @Override
    public ScriptGroup[] getRolesDeputiedByUser(String userName) {
        return wrapGroups(deputyService.getRolesDeputiedByUser(userName), this);
    }

    @Override
    public ScriptGroup[] getRolesDeputiedToUser(String userName) {
        return wrapGroups(deputyService.getRolesDeputiedToUser(userName), this);
    }

    /////////////////////////////////////////////////////////////////
    //                  CURRENT USER INTERFACE                     //
    /////////////////////////////////////////////////////////////////

    @Override
    public ScriptUser[] getCurrentUserDeputies() {
        return wrapUsers(deputyService.getCurrentUserDeputies(), this);
    }

    @Override
    public ScriptUser[] getCurrentUserAssistants() {
        return wrapUsers(deputyService.getCurrentUserAssistants(), this);
    }

    @Override
    public ScriptUser[] getAllCurrentUserDeputies() {
        return wrapUsers(deputyService.getAllCurrentUserDeputies(), this);
    }

    @Override
    public void addCurrentUserDeputies(String[] deputies) {
        deputyService.addCurrentUserDeputies(Arrays.asList(deputies));
    }

    @Override
    public void addCurrentUserAssistants(String[] assistants) {
        deputyService.addCurrentUserAssistants(Arrays.asList(assistants));
    }

    @Override
    public boolean isAssistantUserByUser(String userName, String assistantUserName) {
        return deputyService.isAssistantUserByUser(userName, assistantUserName);
    }

    @Override
    public boolean isAssistantToCurrentUser(String assistantUserName) {
        return deputyService.isAssistantToCurrentUser(assistantUserName);
    }

    @Override
    public void removeCurrentUserDeputies(String[] deputies) {
        deputyService.removeCurrentUserDeputies(Arrays.asList(deputies));
    }

    @Override
    public void removeCurrentUserAssistants(String[] assistance) {
        deputyService.removeCurrentUserAssistants(Arrays.asList(assistance));
    }

    @Override
    public boolean isRoleDeputiedByCurrentUser(String roleFullName) {
        return deputyService.isRoleDeputiedByCurrentUser(roleFullName);
    }

    @Override
    public boolean isRoleDeputiedToCurrentUser(String roleFullName) {
        return deputyService.isRoleDeputiedToCurrentUser(roleFullName);
    }

    @Override
    public ScriptGroup[] getCurrentUserRoles() {
        return wrapGroups(deputyService.getCurrentUserRoles(), this);
    }

    @Override
    public ScriptGroup[] getCurrentUserBranches() {
        return wrapGroups(deputyService.getCurrentUserBranches(), this);
    }

    @Override
    public ScriptGroup[] getRolesDeputiedByCurrentUser() {
        return wrapGroups(deputyService.getRolesDeputiedByCurrentUser(), this);
    }

    @Override
    public ScriptGroup[] getRolesDeputiedToCurrentUser() {
        return wrapGroups(deputyService.getRolesDeputiedToCurrentUser(), this);
    }

    @Override
    public boolean isUserAvailable(String userName) {
        return deputyService.isUserAvailable(userName);
    }

    @Override
    public boolean isCanDeleteDeputeOrAssistantFromRole(String roleFullName) {
        return deputyService.isCanDeleteDeputeOrAssistantFromRole(roleFullName);
    }
}
