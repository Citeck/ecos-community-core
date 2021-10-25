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

public abstract class AbstractDeputyListener implements DeputyListener {

    private int priority;
    protected DeputyServiceImpl deputyService;

    public void init() {
        deputyService.addDeputyListener(this);
    }

    public void setDeputyService(DeputyServiceImpl deputyService) {
        this.deputyService = deputyService;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    @Override
    public void onRoleMemberAvailable(String roleFullName, String memberName) {
        // do nothing by default
    }

    @Override
    public void onRoleMemberUnavailable(String roleFullName, String memberName) {
        // do nothing by default
    }

    @Override
    public void onRoleDeputyAvailable(String roleFullName, String deputyName) {
        // do nothing by default
    }

    @Override
    public void onRoleDeputyUnavailable(String roleFullName,
                                        String deputyName) {
        // do nothing by default
    }

    @Override
    public void onUserDeputyAvailable(String userName, String deputyName) {
        // do nothing by default
    }

    @Override
    public void onUserDeputyUnavailable(String userName, String deputyName) {
        // do nothing by default
    }

    @Override
    public void onUserAvailable(String userName) {
        // do nothing by default
    }

    @Override
    public void onUserUnavailable(String userName) {
        // do nothing by default
    }

    @Override
    public void onAssistantAdded(String userName) {
        // do nothing by default
    }

    @Override
    public void onAssistantRemoved(String userName, String deputyUser) {
        // do nothing by default
    }

    @Override
    public void onRoleAssistantAdded(String roleFullName, String assistantName) {
        // do nothing by default
    }

    @Override
    public void onRoleAssistantRemoved(String roleFullName, String assistantName) {
        // do nothing by default
    }
}
