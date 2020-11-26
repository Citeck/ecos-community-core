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
package ru.citeck.ecos.attr;

import java.util.Collection;

import org.alfresco.repo.jscript.ScriptNode;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;

import org.springframework.beans.factory.annotation.Autowired;
import ru.citeck.ecos.utils.AlfrescoScopableProcessorExtension;
import ru.citeck.ecos.utils.JavaScriptImplUtils;

public class NodeAttributeServiceJS extends AlfrescoScopableProcessorExtension {

    private NodeAttributeService impl;
    private NodeService nodeService;

    public NodeRef getNodeRef(Object node) {
        NodeRef nodeRef = JavaScriptImplUtils.getNodeRef(node);
        if (nodeRef == null) {
            return null;
        }
        // case roles
        if (nodeRef.getStoreRef().getProtocol().startsWith("et-role")) {
            return nodeRef;
        }
        if (nodeService.exists(nodeRef)) {
            return nodeRef;
        }
        return null;
    }

    public String[] getPersisted(ScriptNode node) {
        return convert(impl.getPersistedAttributeNames(node.getNodeRef()));
    }

    public String[] getDefined(ScriptNode node) {
        return convert(impl.getDefinedAttributeNames(node.getNodeRef()));
    }

    public String[] getDefined(String className) {
        return convert(impl.getDefinedAttributeNames(convert(className)));
    }

    public String[] getDefined(String className, boolean inherit) {
        return convert(impl.getDefinedAttributeNames(convert(className), inherit));
    }

    public Object get(Object node, String attributeName) {
        NodeRef nodeRef = JavaScriptImplUtils.getNodeRef(node);
        return impl.getAttribute(nodeRef, convert(attributeName));
    }

    public Object get(ScriptNode node) {
        return impl.getAttributes(node.getNodeRef());
    }

    public void set(ScriptNode node, String attributeName, Object value) {
        impl.setAttribute(node.getNodeRef(), convert(attributeName), value);
    }

    public String getAttributeType(String attributeName) {
        return convert(impl.getAttributeType(convert(attributeName)));
    }

    public String getAttributeSubtype(String attributeName) {
        return convert(impl.getAttributeSubtype(convert(attributeName)));
    }

    private String[] convert(Collection<QName> qnames) {
        return JavaScriptImplUtils.convertQNames(qnames, serviceRegistry.getNamespaceService());
    }

    private QName convert(String qname) {
        return JavaScriptImplUtils.convertQName(qname, serviceRegistry.getNamespaceService());
    }

    private String convert(QName qname) {
        return JavaScriptImplUtils.convertQName(qname, serviceRegistry.getNamespaceService());
    }

    public void setImpl(NodeAttributeService impl) {
        this.impl = impl;
    }

    @Autowired
    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }
}
