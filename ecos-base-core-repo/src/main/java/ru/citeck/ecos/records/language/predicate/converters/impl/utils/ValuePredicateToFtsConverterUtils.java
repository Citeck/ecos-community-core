package ru.citeck.ecos.records.language.predicate.converters.impl.utils;

import org.alfresco.service.cmr.dictionary.*;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import ru.citeck.ecos.records2.RecordRef;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ValuePredicateToFtsConverterUtils {

    public static boolean checkValueEqualsToKeyOrValue(Map.Entry<String, String> entry, String inputValue) {
        String inputInLowerCase = inputValue.toLowerCase();
        String key = entry.getKey().toLowerCase();
        String value = entry.getValue().toLowerCase();
        return key.contains(inputInLowerCase) || value.contains(inputInLowerCase);
    }

    public static boolean isTextOrMLText(PropertyDefinition def) {
        QName dataType = def.getDataType().getName();
        String namespaceURI = def.getName().getNamespaceURI();

        boolean isTextOrMLText = DataTypeDefinition.TEXT.equals(dataType) || DataTypeDefinition.MLTEXT.equals(dataType);
        boolean isSystemProperty = NamespaceService.SYSTEM_MODEL_1_0_URI.equals(namespaceURI);
        boolean isContentProperty = NamespaceService.CONTENT_MODEL_1_0_URI.equals(namespaceURI);

        return isTextOrMLText && !isSystemProperty && !isContentProperty;
    }

    public static boolean isNodeRefAtt(ClassAttributeDefinition attDef) {
        if (attDef == null) {
            return false;
        }

        if (attDef instanceof AssociationDefinition) {
            return true;
        }

        if (!(attDef instanceof PropertyDefinition)) {
            return false;
        }

        PropertyDefinition propDef = (PropertyDefinition) attDef;
        DataTypeDefinition dataType = propDef.getDataType();
        if (dataType == null) {
            return false;
        }

        return DataTypeDefinition.NODE_REF.equals(dataType.getName());
    }

    public static String toValidNodeRef(String value) {
        return RecordRef.valueOf(value).getId();
    }

    public static Map<QName, Serializable> getTargetTypeAttributes(TypeDefinition targetType, String assocVal) {
        List<PropertyDefinition> propertyDefinitions = getPropertyDefinitions(targetType);

        return propertyDefinitions.stream()
            .filter(ValuePredicateToFtsConverterUtils::isTextOrMLText)
            .flatMap(def -> Collections.singletonMap(def.getName(), assocVal).entrySet().stream())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static List<PropertyDefinition> getPropertyDefinitions(TypeDefinition targetType) {
        List<PropertyDefinition> propertyDefinitions = new ArrayList<>(targetType.getProperties().values());

        List<AspectDefinition> definitions = targetType.getDefaultAspects(true);
        definitions.forEach(a -> propertyDefinitions.addAll(a.getProperties().values()));

        return propertyDefinitions;
    }
}
