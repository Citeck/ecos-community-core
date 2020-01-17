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

import java.util.Collections;
import java.util.Set;

import org.alfresco.service.cmr.dictionary.AssociationDefinition;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.namespace.QNamePattern;

import ru.citeck.ecos.attr.AbstractAttributeProvider;
import ru.citeck.ecos.model.AttributeModel;
import ru.citeck.ecos.node.NodeInfo;
import ru.citeck.ecos.utils.NamespaceMatch;
import ru.citeck.ecos.utils.RepoUtils;

/**
 * Source association attribute provider.
 *
 * No associations are considered as defined for types and nodes.
 * 
 * @author Sergey Tiunov
 */
public class SourceAssocAttributes extends AbstractAttributeProvider {

    @Override
    public QNamePattern getAttributeNamePattern() {
        return new NamespaceMatch(AttributeModel.NAMESPACE_SOURCE_ASSOC);
    }

    @Override
    public boolean provides(QName attributeName) {
        return getDefinition(attributeName) != null;
    }

    @Override
    public Set<QName> getPersistedAttributeNames(NodeRef nodeRef, boolean justCreated) {
        // source assocs are not persisted in specified node
        return Collections.emptySet();
    }

    @Override
    public Set<QName> getDefaultAttributeNames(NodeRef nodeRef) {
        // source assocs are not exposed by default
        return Collections.emptySet();
    }

    @Override
    public Set<QName> getDefinedAttributeNames(NodeRef nodeRef) {
        // for node only persisted associations are considered as defined
        return getPersistedAttributeNames(nodeRef, false);
    }

    @Override
    public Set<QName> getDefinedAttributeNames(QName typeName, boolean inherit) {
        // for type no source assocs are considered as defined
        return Collections.emptySet();
    }

    @Override
    public Object getAttribute(NodeRef nodeRef, QName attributeName) {
        QName associationName = getAssociationName(attributeName);
        return RepoUtils.getSourceNodeRefs(nodeRef, associationName, nodeService);
    }

    @Override
    public void setAttribute(NodeInfo nodeInfo, QName attributeName, Object value) {
        nodeInfo.setSourceAssocs(RepoUtils.anyToNodeRefs(value), attributeName);
    }

    @Override
    public QName getAttributeType(QName attributeName) {
        return AttributeModel.TYPE_SOURCE_ASSOCIATION;
    }

    @Override
    public QName getAttributeSubtype(QName attributeName) {
        return needDefinition(attributeName).getSourceClass().getName();
    }

    @Override
    public Class<?> getAttributeValueType(QName attributeName) {
        return NodeRef.class;
    }
    
    private QName getAssociationName(QName attributeName) {
        String prefixedName = attributeName.getLocalName();
        return QName.createQName(prefixedName, namespaceService);
    }

    private AssociationDefinition getDefinition(QName attributeName) {
        AssociationDefinition assocDef = dictionaryService.getAssociation(getAssociationName(attributeName));
        return assocDef == null || assocDef.isChild() ? null : assocDef;
    }

    private AssociationDefinition needDefinition(QName attributeName) {
        AssociationDefinition assocDef = getDefinition(attributeName);
        if(assocDef == null) 
            throw new IllegalArgumentException("Assocition " + attributeName + " does not exist");
        return assocDef;
    }

}
