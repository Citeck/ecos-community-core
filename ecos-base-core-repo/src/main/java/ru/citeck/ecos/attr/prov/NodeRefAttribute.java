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
package ru.citeck.ecos.attr.prov;

import org.alfresco.service.cmr.repository.NodeRef;

import ru.citeck.ecos.attr.SingleAttributeProvider;
import ru.citeck.ecos.model.AttributeModel;
import ru.citeck.ecos.node.NodeInfo;

public class NodeRefAttribute extends SingleAttributeProvider<NodeRef> {

    public NodeRefAttribute() {
        super(AttributeModel.ATTR_NODEREF);
    }

    @Override
    public NodeRef getValue(NodeRef nodeRef) {
        return nodeRef;
    }

    @Override
    protected void setValue(NodeRef nodeRef, NodeRef newNodeRef) {
        if(newNodeRef == null) return;
        if(!nodeRef.equals(newNodeRef)) {
            throw new IllegalArgumentException("Can not change nodeRef of existing nodes");
        }
    }

    @Override
    protected void setValue(NodeInfo nodeInfo, NodeRef nodeRef) {
        nodeInfo.setNodeRef(nodeRef);
    }

}
