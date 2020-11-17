package ru.citeck.ecos.utils;

import ecos.com.google.common.cache.CacheBuilder;
import ecos.com.google.common.cache.CacheLoader;
import ecos.com.google.common.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.repo.dictionary.constraint.ListOfValuesConstraint;
import org.alfresco.repo.i18n.MessageService;
import org.alfresco.repo.transaction.TransactionalResourceHelper;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.dictionary.*;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.Pair;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class DictUtils {

    public static final QName QNAME = QName.createQName("", "dictUtils");
    private static final int CACHE_AGE_SECONDS = 600;
    private static final String TXN_CONSTRAINTS_CACHE = DictUtils.class.getName();

    private final DictionaryService dictionaryService;
    private final NamespaceService namespaceService;
    private final MessageService messageService;

    private final LoadingCache<Pair<QName, QName>, Map<String, String>> cachedDisplayNameMappingWithChildren;

    @Autowired
    public DictUtils(ServiceRegistry serviceRegistry) {
        dictionaryService = serviceRegistry.getDictionaryService();
        messageService = serviceRegistry.getMessageService();
        namespaceService = serviceRegistry.getNamespaceService();

        this.cachedDisplayNameMappingWithChildren = CacheBuilder.newBuilder()
            .expireAfterWrite(CACHE_AGE_SECONDS, TimeUnit.SECONDS)
            .build(CacheLoader.from(this::configureCacheDisplayNameMapping));
    }

    private Map<String, String> configureCacheDisplayNameMapping(Pair<QName, QName> key) {
        Map<String, String> result = new HashMap<>();

        QName field = key.getSecond();
        if (field == null) {
            log.warn("Return empty mapping because 'field' was null");
            return Collections.emptyMap();
        }

        QName container = key.getFirst();
        if (container == null) {
            PropertyDefinition propertyDefinition = dictionaryService.getProperty(field);
            if (propertyDefinition == null) {
                log.warn("Return empty mapping because 'propertyDefinition' was null. 'field' value: " + field);
                return Collections.emptyMap();
            }
            ClassDefinition classDefinition = propertyDefinition.getContainerClass();
            if (classDefinition == null) {
                log.warn("Return empty mapping because 'classDefinition' was null. 'field' value: " + field);
                return Collections.emptyMap();
            } else {
                container = classDefinition.getName();
            }
        }

        Collection<QName> subClasses = this.getSubClasses(container, false);
        for (QName subClass : subClasses) {
            Map<String, String> childMapping = this.getPropertyDisplayNameMapping(subClass, field);
            if (childMapping != null) {
                result.putAll(childMapping);
            }
        }

        Map<String, String> fieldMapping = getPropertyDisplayNameMapping(field);
        if (fieldMapping != null) {
            result.putAll(fieldMapping);
        }

        return result;
    }

    public PropertyDefinition getPropDef(String containerName, QName propertyName) {

        if (StringUtils.isBlank(containerName) || propertyName == null) {
            return null;
        }

        QName containerQName = QName.resolveToQName(namespaceService, containerName);

        if (containerQName == null) {
            return null;
        }

        return getPropDef(containerQName, propertyName);
    }

    /**
     * Search property definition in specified container or associated default aspects
     *
     * @param containerName aspect or type name. If null then default property definition will be returned
     * @param propertyName  property name. Must be not null
     * @return property definition or null if definition not found
     */
    public PropertyDefinition getPropDef(QName containerName, QName propertyName) {

        if (propertyName == null) {
            return null;
        }

        PropertyDefinition propDef = dictionaryService.getProperty(propertyName);

        if (propDef == null) {
            return null;
        }

        ClassDefinition containerClass = null;
        if (containerName != null) {
            containerClass = dictionaryService.getClass(containerName);
        }

        ClassDefinition propContainerClass = propDef.getContainerClass();
        if (containerClass != null && !propContainerClass.equals(containerClass)) {
            if (dictionaryService.isSubClass(containerClass.getName(), propContainerClass.getName())) {

                propContainerClass = containerClass;

            } else if (propContainerClass.isAspect()) {

                for (ClassDefinition aspectDef : containerClass.getDefaultAspects(true)) {
                    if (dictionaryService.isSubClass(aspectDef.getName(), propContainerClass.getName())) {
                        propContainerClass = aspectDef;
                    }
                }
            }

            propDef = dictionaryService.getProperty(propContainerClass.getName(), propertyName);
        }

        return propDef;
    }

    public String getTypeTitle(QName typeName) {
        TypeDefinition type = dictionaryService.getType(typeName);
        return type.getTitle(messageService);
    }

    public ClassAttributeDefinition getAttDefinition(String name) {

        if (StringUtils.isBlank(name)) {
            return null;
        }

        QName field = QName.resolveToQName(namespaceService, name);
        if (field == null) {
            return null;
        }

        AssociationDefinition assocDef = dictionaryService.getAssociation(field);
        if (assocDef != null) {
            return assocDef;
        }
        return dictionaryService.getProperty(field);
    }

    public String getPropertyDisplayName(QName name, String value) {
        return getPropertyDisplayName(null, name, value);
    }

    public String getPropertyDisplayName(QName scope, QName name, String value) {
        Map<String, String> mapping = getPropertyDisplayNameMapping(scope, name);
        return mapping != null && mapping.containsKey(value) ? mapping.get(value) : value;
    }

    public Map<String, String> getPropertyDisplayNameMapping(QName name) {
        return getPropertyDisplayNameMapping(null, name);
    }

    public Map<String, String> getPropertyDisplayNameMapping(QName scope, QName name) {

        Map<Pair<QName, QName>, Map<String, String>> cache = TransactionalResourceHelper.getMap(TXN_CONSTRAINTS_CACHE);
        Pair<QName, QName> key = new Pair<>(scope, name);

        return cache.computeIfAbsent(key, n -> {

            PropertyDefinition propDef = getPropDef(scope, name);

            ListOfValuesConstraint constraint = getListOfValuesConstraint(propDef);
            if (constraint == null) {
                return null;
            }

            Map<String, String> result = new HashMap<>();

            for (String value : constraint.getAllowedValues()) {

                String display = constraint.getDisplayLabel(value, messageService);
                result.put(value, display);
            }

            return result;
        });
    }

    public Map<String, String> getPropertyDisplayNameMappingWithChildren(QName container, QName field) {
        try {
            return cachedDisplayNameMappingWithChildren.get(new Pair<>(container, field));
        } catch (ExecutionException e) {
            log.error("Cannot get cached 'displayName' mapping. \n" +
                "Args: 'container': " + container + " 'field': " + field, e);
            return Collections.emptyMap();
        }
    }

    /**
     * Returns a list of constraints for the specified property
     *
     * @param propertyName property name. Must be not null
     * @return list of values constraint or null
     * @throws NullPointerException if propertyName is null
     */
    public ListOfValuesConstraint getListOfValuesConstraint(QName propertyName) {

        return getListOfValuesConstraint(dictionaryService.getProperty(propertyName));
    }

    public ListOfValuesConstraint getListOfValuesConstraint(PropertyDefinition propDef) {

        if (propDef != null) {
            List<ConstraintDefinition> constraintDefinitions = propDef.getConstraints();

            for (ConstraintDefinition constraintDefinition : constraintDefinitions) {
                Constraint constraint = constraintDefinition.getConstraint();

                if (constraint instanceof ListOfValuesConstraint) {
                    return (ListOfValuesConstraint) constraint;
                }
            }
        }

        return null;
    }

    public TypeDefinition getTypeDefinition(QName typeName) {
        if (typeName == null) {
            return null;
        }
        return dictionaryService.getType(typeName);
    }

    public Collection<QName> getSubClasses(QName className, boolean recursive) {
        ClassDefinition classDef = dictionaryService.getClass(className);
        if (classDef == null) {
            log.warn("Class is not registered: " + className);
            return Collections.singletonList(className);
        } else if (classDef.isAspect()) {
            Collection<QName> subAspects = dictionaryService.getSubAspects(className, recursive);
            Set<QName> subAspectsSet = new HashSet<>(subAspects);
            subAspectsSet.add(className);
            return subAspectsSet;
        } else {
            return dictionaryService.getSubTypes(className, recursive);
        }
    }
}
