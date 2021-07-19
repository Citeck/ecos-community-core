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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.alfresco.service.cmr.dictionary.AssociationDefinition;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.namespace.QNamePattern;
import org.alfresco.service.namespace.RegexQNamePattern;

import ru.citeck.ecos.attr.AbstractAttributeProvider;
import ru.citeck.ecos.model.AttributeModel;
import ru.citeck.ecos.node.NodeInfo;
import ru.citeck.ecos.utils.DictionaryUtils;
import ru.citeck.ecos.utils.RepoUtils;

/**
 * Child association attribute provider.
 *
 * All associations, that are defined in the data dictionary for the types and aspects of node,
 * are considered as defined for this node.
 *
 * @author Sergey Tiunov
 */
public class ChildAssocAttributes extends AbstractAttributeProvider {

    private final Map<QName, Function<NodeRef, List<Object>>> assocResolvers = new ConcurrentHashMap<>();

    @Override
    public QNamePattern getAttributeNamePattern() {
        return RegexQNamePattern.MATCH_ALL;
    }

    @Override
    public boolean provides(QName attributeName) {
        return getDefinition(attributeName) != null;
    }

    @Override
    public Set<QName> getPersistedAttributeNames(NodeRef nodeRef, boolean justCreated) {
        List<ChildAssociationRef> childAssocs = nodeService.getChildAssocs(nodeRef);
        Set<QName> attributeNames = new HashSet<>();
        for(ChildAssociationRef assoc : childAssocs) {
            attributeNames.add(assoc.getTypeQName());
        }
        return attributeNames;
    }

    @Override
    public Set<QName> getDefaultAttributeNames(NodeRef nodeRef) {
        // child assocs are not exposed by default
        return Collections.emptySet();
    }

    @Override
    public Set<QName> getDefinedAttributeNames(NodeRef nodeRef) {
        return DictionaryUtils.getAllChildAssociationNames(nodeRef, nodeService, dictionaryService);
    }

    @Override
    public Set<QName> getDefinedAttributeNames(QName typeName, boolean inherit) {
        return inherit
                ? DictionaryUtils.getAllChildAssociationNames(Collections.singleton(typeName), dictionaryService)
                : DictionaryUtils.getDefinedChildAssociationNames(typeName, dictionaryService);
    }

    @Override
    public Object getAttribute(NodeRef nodeRef, QName attributeName) {
        Function<NodeRef, List<Object>> assocResolver = assocResolvers.get(attributeName);
        if (assocResolver != null) {
            return assocResolver.apply(nodeRef);
        }
        return RepoUtils.getChildrenByAssoc(nodeRef, attributeName, nodeService);
    }

    @Override
    public void setAttribute(NodeInfo nodeInfo, QName attributeName, Object value) {
        nodeInfo.setChildAssocs(RepoUtils.anyToNodeRefs(value), attributeName);
    }

    @Override
    public QName getAttributeType(QName attributeName) {
        return AttributeModel.TYPE_CHILD_ASSOCIATION;
    }

    @Override
    public QName getAttributeSubtype(QName attributeName) {
        return needDefinition(attributeName).getTargetClass().getName();
    }

    @Override
    public Class<?> getAttributeValueType(QName attributeName) {
        return NodeRef.class;
    }

    private AssociationDefinition getDefinition(QName attributeName) {
        AssociationDefinition assocDef = dictionaryService.getAssociation(attributeName);
        return assocDef == null || !assocDef.isChild() ? null : assocDef;
    }

    private AssociationDefinition needDefinition(QName attributeName) {
        AssociationDefinition assocDef = getDefinition(attributeName);
        if(assocDef == null)
            throw new IllegalArgumentException("Assocition " + attributeName + " does not exist");
        return assocDef;
    }

    public void registerAssocResolver(QName name, Function<NodeRef, List<Object>> resolver) {
        this.assocResolvers.put(name, resolver);
    }
}
