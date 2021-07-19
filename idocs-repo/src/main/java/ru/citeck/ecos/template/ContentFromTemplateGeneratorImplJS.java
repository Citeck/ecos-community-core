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

import org.alfresco.repo.jscript.BaseScopableProcessorExtension;
import org.alfresco.service.cmr.repository.NodeRef;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @author Alexander Nemerov
 * date: 29.04.2014
 */
public class ContentFromTemplateGeneratorImplJS extends BaseScopableProcessorExtension {

    private static Log logger = LogFactory.getLog(ContentFromTemplateGeneratorImplJS.class);

    private ContentFromTemplateGenerator contentFromTemplateGenerator;

    public void generateContentByTemplate(String persistedObject) {
        NodeRef persistedObjectRef = new NodeRef(persistedObject);

        if (logger.isDebugEnabled())
            logger.debug("Generating cm:content by dms:templateAssociation for node=" + persistedObject);

        contentFromTemplateGenerator.generateContentByTemplate(persistedObjectRef);
    }

    public void generateContentByTemplate(String nodeRef, String historyDescription) {
        NodeRef node = new NodeRef(nodeRef);

        if (logger.isDebugEnabled()) {
            logger.debug("Generating cm:content by dms:templateAssociation for node=" + nodeRef);
        }

        contentFromTemplateGenerator.generateContentByTemplate(node, historyDescription);
    }

    public void generateContentByTemplate(String nodeRef, boolean majorVersion, String historyDescription) {
        NodeRef node = new NodeRef(nodeRef);

        if (logger.isDebugEnabled()) {
            logger.debug("Generating cm:content by dms:templateAssociation for node=" + nodeRef);
        }

        contentFromTemplateGenerator.generateContentByTemplate(node, majorVersion, historyDescription);
    }

    public void setContentFromTemplateGenerator(ContentFromTemplateGenerator contentFromTemplateGenerator) {
        this.contentFromTemplateGenerator = contentFromTemplateGenerator;
    }
}
