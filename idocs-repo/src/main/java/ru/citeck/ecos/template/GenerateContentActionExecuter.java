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
package ru.citeck.ecos.template;

import org.alfresco.repo.action.ParameterDefinitionImpl;
import org.alfresco.repo.action.executer.ActionExecuterAbstractBase;
import org.alfresco.service.cmr.action.Action;
import org.alfresco.service.cmr.action.ParameterDefinition;
import org.alfresco.service.cmr.dictionary.DataTypeDefinition;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;

import java.util.List;

/**
 * @author Alexander Nemerov <alexander.nemerov@citeck.ru>
 * date: 30.04.14
 */
public class GenerateContentActionExecuter extends ActionExecuterAbstractBase {

    public static final String NAME = "generate-content";
    public static final String PARAM_HISTORY_MAJOR_VERSION = "history-major-version";
    public static final String PARAM_HISTORY_DESCRIPTION = "history-description";

    private ContentFromTemplateGenerator contentFromTemplateGenerator;
    private NodeService nodeService;

    @Override
    protected void executeImpl(Action action, NodeRef nodeRef) {
        if (!nodeService.exists(nodeRef)) {
            return;
        }
        boolean majorVersion = Boolean.parseBoolean(String.valueOf(action.getParameterValue(PARAM_HISTORY_MAJOR_VERSION)));
        if (action.getParameterValue(PARAM_HISTORY_DESCRIPTION) != null) {
            String historyDescriptionText = (String) action.getParameterValue(PARAM_HISTORY_DESCRIPTION);
            contentFromTemplateGenerator.generateContentByTemplate(nodeRef, majorVersion, historyDescriptionText);
        } else {
            contentFromTemplateGenerator.generateContentByTemplate(nodeRef, majorVersion, null);
        }
    }

    @Override
    protected void addParameterDefinitions(List<ParameterDefinition> parameterDefinitions) {
        parameterDefinitions.add(new ParameterDefinitionImpl(
                PARAM_HISTORY_DESCRIPTION,
                DataTypeDefinition.TEXT,
                false,
                getParamDisplayLabel(PARAM_HISTORY_DESCRIPTION)));
    }

    public void setContentFromTemplateGenerator(ContentFromTemplateGenerator contentFromTemplateGenerator) {
        this.contentFromTemplateGenerator = contentFromTemplateGenerator;
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }
}
