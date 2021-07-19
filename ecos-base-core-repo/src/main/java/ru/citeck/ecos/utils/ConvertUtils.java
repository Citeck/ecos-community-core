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
package ru.citeck.ecos.utils;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.alfresco.repo.jscript.ScriptNode;
import org.alfresco.repo.template.TemplateNode;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.datatype.DefaultTypeConverter;
import org.springframework.beans.BeansException;
import org.springframework.beans.SimpleTypeConverter;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.support.AbstractBeanFactory;

public class ConvertUtils implements BeanFactoryAware {

    private static SimpleTypeConverter converter = new SimpleTypeConverter();

    @SuppressWarnings("unchecked")
    public static <T> T convertSingleValue(Object value, Class<T> requiredClass) {

        if (value == null) {
            return null;
        }
        if (requiredClass.isInstance(value)) {
            return (T) value;
        }

        RuntimeException exception = new IllegalStateException("Could not convert from " + value.getClass() + " to " + requiredClass);

        if (NodeRef.class.equals(requiredClass)) {
            if (value instanceof ScriptNode) {
                return (T) ((ScriptNode) value).getNodeRef();
            }
            if (value instanceof TemplateNode) {
                return (T) ((TemplateNode) value).getNodeRef();
            }
        }

        // using spring type converter:
        try {
            return converter.convertIfNecessary(value, requiredClass);
        } catch(Exception e) {
            exception.addSuppressed(e);
        }
        // using alfresco type converter:
        try {
            return DefaultTypeConverter.INSTANCE.convert(requiredClass, value);
        } catch(Exception e) {
            exception.addSuppressed(e);
        }
        // use constructors:
        try {
            Constructor<T> constructor = requiredClass.getConstructor(value.getClass());
            return constructor.newInstance(value);
        } catch (Exception e) {
            exception.addSuppressed(e);
        }

        throw exception;
    }

    public static <T> List<T> convertMultipleValues(Collection<?> values, Class<T> requiredClass) {
        if(values == null || values.isEmpty()) return Collections.emptyList();
        List<T> convertedList = new ArrayList<>(values.size());
        for(Object value : values) {
            convertedList.add(convertSingleValue(value, requiredClass));
        }
        return convertedList;
    }

    public static <T> List<T> convertMultipleValues(Object[] values, Class<T> requiredClass) {
        return convertMultipleValues(Arrays.asList(values), requiredClass);
    }

    public static Object convertValue(Object value, Class<?> requiredClass, boolean multiple) {
        if(value == null) {
            return multiple ? Collections.emptyList() : null;
        }
        if(value instanceof Collection) {
            Collection<?> values = (Collection<?>) value;
            return multiple ? convertMultipleValues(values, requiredClass)
                : values.isEmpty() ? null
                : convertSingleValue(values.iterator().next(), requiredClass);
        }
        if(value instanceof Object[]) {
            Object[] values = (Object[]) value;
            return multiple ? convertMultipleValues(values, requiredClass)
                    : values.length == 0 ? null
                    : convertSingleValue(values[0], requiredClass);
        }
        Object convertedValue = convertSingleValue(value, requiredClass);
        return multiple ? Collections.singletonList(convertedValue) : convertedValue;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        if(beanFactory instanceof AbstractBeanFactory) {
            ((AbstractBeanFactory) beanFactory).copyRegisteredEditorsTo(converter);
        }
    }

}
