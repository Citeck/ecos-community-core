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

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import org.alfresco.service.cmr.dictionary.DataTypeDefinition;
import org.alfresco.service.cmr.dictionary.PropertyDefinition;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.namespace.QNamePattern;
import org.alfresco.service.namespace.RegexQNamePattern;

import org.alfresco.util.ISO8601DateFormat;
import ru.citeck.ecos.attr.AbstractAttributeProvider;
import ru.citeck.ecos.model.AttributeModel;
import ru.citeck.ecos.node.NodeInfo;
import ru.citeck.ecos.utils.ConvertUtils;
import ru.citeck.ecos.utils.DictionaryUtils;

public class PropertyAttributes extends AbstractAttributeProvider {

    private final Map<String, BiFunction<NodeRef, QName, Serializable>> attsResolvers = new ConcurrentHashMap<>();

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
        return nodeService.getProperties(nodeRef).keySet();
    }

    @Override
    public Set<QName> getDefaultAttributeNames(NodeRef nodeRef) {
        // all persisted properties are shown by default
        return getPersistedAttributeNames(nodeRef, false);
    }

    @Override
    public Set<QName> getDefinedAttributeNames(NodeRef nodeRef) {
        return DictionaryUtils.getAllPropertyNames(nodeRef, nodeService, dictionaryService);
    }

    @Override
    public Set<QName> getDefinedAttributeNames(QName typeName, boolean inherit) {
        return inherit
                ? DictionaryUtils.getAllPropertyNames(Collections.singleton(typeName), dictionaryService)
                : DictionaryUtils.getDefinedPropertyNames(typeName, dictionaryService);
    }

    @Override
    public Object getAttribute(NodeRef nodeRef, QName attributeName) {
        BiFunction<NodeRef, QName, Serializable> resolver = attsResolvers.get(nodeRef.getStoreRef().getProtocol());
        if (resolver != null) {
            return resolver.apply(nodeRef, attributeName);
        }
        return nodeService.getProperty(nodeRef, attributeName);
    }

    @Override
    public void setAttribute(NodeInfo nodeInfo, QName attributeName, Object value) {

        PropertyDefinition propDef = needDefinition(attributeName);

        QName typeName = propDef.getDataType().getName();

        if (DataTypeDefinition.CONTENT.equals(typeName)) {
            // do not convert content properties
            // it is now done by NodeInfo
        } else if (DataTypeDefinition.DATE.equals(typeName)) {
            if (value instanceof String) {
                value = ISO8601DateFormat.parseDayOnly((String) value, TimeZone.getTimeZone("GMT"));
            } else if (value instanceof Date) {
                long millisInDay = TimeUnit.DAYS.toMillis(1);
                long daysCount = TimeUnit.MILLISECONDS.toDays(((Date) value).getTime());
                value = new Date(millisInDay * daysCount);
            }
        } else {
            value = ConvertUtils.convertValue(value, getValueClass(propDef), propDef.isMultiValued());
        }
        nodeInfo.setProperty(attributeName, (Serializable) value);
    }

    @Override
    public QName getAttributeType(QName attributeName) {
        return AttributeModel.TYPE_PROPERTY;
    }

    @Override
    public QName getAttributeSubtype(QName attributeName) {
        return needDefinition(attributeName).getDataType().getName();
    }

    @Override
    public Class<?> getAttributeValueType(QName attributeName) {
        PropertyDefinition propDef = needDefinition(attributeName);
        return getValueClass(propDef);
    }

    private Class<?> getValueClass(PropertyDefinition propDef) {
        String className = propDef.getDataType().getJavaClassName();
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Could not find property datatype class: " + className);
        }
    }

    private PropertyDefinition getDefinition(QName attributeName) {
        return dictionaryService.getProperty(attributeName);
    }

    private PropertyDefinition needDefinition(QName attributeName) {
        PropertyDefinition propDef = getDefinition(attributeName);
        if(propDef == null)
            throw new IllegalArgumentException("Property " + attributeName + " does not exist");
        return propDef;
    }

    public void registerAttsResolver(String protocol, BiFunction<NodeRef, QName, Serializable> resolver) {
        this.attsResolvers.put(protocol, resolver);
    }
}
