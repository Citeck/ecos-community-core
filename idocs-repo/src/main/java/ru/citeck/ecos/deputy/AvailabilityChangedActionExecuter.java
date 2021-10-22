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

import org.alfresco.repo.action.ParameterDefinitionImpl;
import org.alfresco.repo.action.executer.ActionExecuterAbstractBase;
import org.alfresco.service.cmr.action.Action;
import org.alfresco.service.cmr.action.ParameterDefinition;
import org.alfresco.service.cmr.dictionary.DataTypeDefinition;
import org.alfresco.service.cmr.repository.NodeRef;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.workflow.mirror.WorkflowMirrorService;

import java.util.List;

public class AvailabilityChangedActionExecuter extends ActionExecuterAbstractBase {

    public static final String NAME = "availability-changed";
    public static final String PARAM_USER_NAME = "userName";
    private DeputyService deputyService;

    @Override
    protected void executeImpl(Action action, NodeRef actionedUponNodeRef) {
        String userName = (String) action.getParameterValue(PARAM_USER_NAME);
        if (userName != null) {
            deputyService.userAvailabilityChanged(userName);
        }
    }

    @Override
    protected void addParameterDefinitions(List<ParameterDefinition> paramList) {
        paramList.add(new ParameterDefinitionImpl(PARAM_USER_NAME,
            DataTypeDefinition.TEXT,
            true, getParamDisplayLabel(PARAM_USER_NAME)));
    }

    @Required
    public void setDeputyService(DeputyService deputyService) {
        this.deputyService = deputyService;
    }
}
