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

import org.alfresco.service.cmr.repository.NodeRef;

/**
 * @author Alexander Nemerov <alexander.nemerov@citeck.ru>
 * date: 30.04.14
 */
public interface ContentFromTemplateGenerator {

    void generateContentByTemplate(NodeRef nodeRef);

    void generateContentByTemplate(NodeRef nodeRef, String historyDescriptionText);

    void generateContentByTemplate(NodeRef nodeRef, boolean majorVersion, String historyDescription);
}
