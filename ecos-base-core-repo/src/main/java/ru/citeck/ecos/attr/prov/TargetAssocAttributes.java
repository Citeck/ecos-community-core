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
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.alfresco.service.cmr.dictionary.AssociationDefinition;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.namespace.QNamePattern;
import org.alfresco.service.namespace.RegexQNamePattern;

import org.springframework.beans.factory.annotation.Autowired;
import ru.citeck.ecos.attr.AbstractAttributeProvider;
import ru.citeck.ecos.model.AttributeModel;
import ru.citeck.ecos.node.NodeInfo;
import ru.citeck.ecos.utils.DictionaryUtils;
import ru.citeck.ecos.utils.NodeUtils;
import ru.citeck.ecos.utils.RepoUtils;

/**
 * Target association attribute provider.
 *
 * All associations, that are defined in the data dictionary for the types and aspects of node,
 * are considered as defined for this node.
 *
 * @author Sergey Tiunov
 */
public class TargetAssocAttributes extends AbstractAttributeProvider {

    private final Map<QName, Function<NodeRef, Object>> assocsReaders = new ConcurrentHashMap<>();
    private final Map<QName, BiConsumer<NodeInfo, Object>> assocsWriters = new ConcurrentHashMap<>();

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
        if(justCreated) {
            // all non-empty target associations are considered as persisted
            Map<QName, List<NodeRef>> persistedAssocs = RepoUtils.getTargetAssocs(nodeRef, nodeService);
            return new HashSet<>(persistedAssocs.keySet());
        } else {
            // all defined target associations are considered as persisted
            // for some association type there is no association, its value is empty list
            return getDefinedAttributeNames(nodeRef);
        }
    }

    @Override
    public Set<QName> getDefaultAttributeNames(NodeRef nodeRef) {
        // all target associations are default
        return getDefinedAttributeNames(nodeRef);
    }

    @Override
    public Set<QName> getDefinedAttributeNames(NodeRef nodeRef) {
        return DictionaryUtils.getAllAssociationNames(nodeRef, nodeService, dictionaryService);
    }

    @Override
    public Set<QName> getDefinedAttributeNames(QName typeName, boolean inherit) {
        return inherit
                ? DictionaryUtils.getAllAssociationNames(Collections.singleton(typeName), dictionaryService)
                : DictionaryUtils.getDefinedAssociationNames(typeName, dictionaryService);
    }

    @Override
    public Object getAttribute(NodeRef nodeRef, QName attributeName) {
        Function<NodeRef, Object> reader = assocsReaders.get(attributeName);
        if (reader != null) {
            return reader.apply(nodeRef);
        }
        return RepoUtils.getTargetNodeRefs(nodeRef, attributeName, nodeService);
    }

    @Override
    public void setAttribute(NodeInfo nodeInfo, QName attributeName, Object value) {
        BiConsumer<NodeInfo, Object> writer = assocsWriters.get(attributeName);
        if (writer != null) {
            writer.accept(nodeInfo, value);
            return;
        }
        nodeInfo.setTargetAssocs(RepoUtils.anyToNodeRefs(value), attributeName);
    }

    @Override
    public QName getAttributeType(QName attributeName) {
        return AttributeModel.TYPE_TARGET_ASSOCIATION;
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
        return assocDef == null || assocDef.isChild() ? null : assocDef;
    }

    private AssociationDefinition needDefinition(QName attributeName) {
        AssociationDefinition assocDef = getDefinition(attributeName);
        if (assocDef == null) {
            throw new IllegalArgumentException("Assocition " + attributeName + " does not exist");
        }
        return assocDef;
    }

    public void addAssocsReader(QName name, Function<NodeRef, Object> func) {
        assocsReaders.put(name, func);
    }

    public void addAssocsWriter(QName name, BiConsumer<NodeInfo, Object> func) {
        assocsWriters.put(name, func);
    }
}
