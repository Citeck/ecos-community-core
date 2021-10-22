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

public interface DeputyListener {

    int getPriority();

    void onRoleMemberAvailable(String roleFullName, String memberName);

    void onRoleMemberUnavailable(String roleFullName, String memberName);

    void onRoleDeputyAvailable(String roleFullName, String deputyName);

    void onRoleDeputyUnavailable(String roleFullName, String deputyName);

    void onUserDeputyAvailable(String userName, String deputyName);

    void onUserDeputyUnavailable(String userName, String deputyName);

    void onUserAvailable(String userName);

    void onUserUnavailable(String userName);

    void onAssistantAdded(String userName);

    void onAssistantRemoved(String userName, String deputyName);

    void onRoleAssistantAdded(String roleFullName, String assistantName);

    void onRoleAssistantRemoved(String roleFullName, String assistantName);
}
