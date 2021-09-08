/*
 * Copyright (C) 2008-2017 Citeck LLC.
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
package ru.citeck.ecos.invariants;

import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import org.apache.commons.collections.map.CompositeMap;
import org.mozilla.javascript.Undefined;
import ru.citeck.ecos.attr.NodeAttributeService;
import ru.citeck.ecos.model.InvariantsModel;
import ru.citeck.ecos.utils.ConvertUtils;
import ru.citeck.ecos.utils.SingletonGetterMap;

import java.lang.reflect.Constructor;
import java.util.*;

class InvariantsRuntime {

    private static final int INVARIANTS_EXECUTIONS_LIMIT = 100;

    private NodeAttributeService nodeAttributeService;
    private Map<String, InvariantLanguage> languages;
    private Map<QName, InvariantAttributeType> attributeTypes;

    public Object evaluateInvariant(InvariantDefinition invariant, Map<String, Object> model) {

        InvariantLanguage language = languages.get(invariant.getLanguage());
        if (language == null) {
            throw new IllegalArgumentException("Invariant language is not supported: " + invariant.getLanguage());
        }

        Object expression = invariant.getValue();
        if (!language.isValueSupported(expression)) {
            throw new IllegalArgumentException(language + " does not support this value: " + expression);
        }

        return language.evaluate(expression, model);
    }

    public void executeInvariants(NodeRef nodeRef, List<InvariantDefinition> invariants) {
        executeInvariants(nodeRef, invariants, null, false);
    }

    public void executeInvariants(NodeRef nodeRef, List<InvariantDefinition> invariants, Map<String, Object> model,
                                  boolean justCreated) {

        Set<QName> attributes = nodeAttributeService.getDefinedAttributeNames(nodeRef);

        RuntimeNode node = new RuntimeNode(nodeRef, attributes, model);
        Boolean isDraft = (Boolean) nodeAttributeService.getAttribute(nodeRef, InvariantsModel.PROP_IS_DRAFT);
        if (isDraft != null && isDraft) {
            node.setInvariants(filterByFeatures(invariants, true, Feature.MANDATORY));
        } else {
            node.setInvariants(invariants);
        }

        node.setPersistedAttributes(nodeAttributeService.getPersistedAttributeNames(nodeRef, justCreated));

        // adjust values loop
        // the size of the history map is the expected maximum iterations count
        Set<Map<?, ?>> oldAttributes = new HashSet<>(10);
        while (oldAttributes.size() < INVARIANTS_EXECUTIONS_LIMIT) {
            node.reset();
            Map<QName, Object> newAttributes = node.getNewAttributeValues();
            nodeAttributeService.setAttributes(nodeRef, newAttributes);
            // we compare to all previous states of the node
            // and continue until we reach the state, that we already seen.
            // it is protection against infinite loops caused by mutually dependent invariants.
            if (oldAttributes.contains(newAttributes)) {
                break;
            } else {
                oldAttributes.add(newAttributes);
            }
        }

        if (oldAttributes.size() >= INVARIANTS_EXECUTIONS_LIMIT) {
            throw new InvariantsUnstableException(node, oldAttributes.size());
        }

        // validation:
        if (!node.isValid()) {
            RuntimeAttribute failedAttribute = node.getFailedAttribute();
            throw new InvariantValidationException(nodeRef, failedAttribute.getName(), failedAttribute.getFailedInvariant());
        }
    }

    private List<InvariantDefinition> filterByFeatures(List<InvariantDefinition> definitions,
                                                       boolean exclude, Feature... features) {

        List<InvariantDefinition> result = new ArrayList<>(definitions.size());
        for (InvariantDefinition def : definitions) {
            Feature invFeature = def.getFeature();
            boolean allowed = exclude;
            for (Feature feature : features) {
                if (feature.equals(invFeature)) {
                    allowed = !allowed;
                    break;
                }
            }
            if (allowed) {
                result.add(def);
            }
        }
        return result;
    }

    /*package*/ class RuntimeNode {

        private final NodeRef nodeRef;
        private final Map<QName, RuntimeAttribute> attributes;
        private final Map<String, Object> model;

        public RuntimeNode(NodeRef nodeRef, Set<QName> attributeNames, Map<String, Object> model) {
            this.nodeRef = nodeRef;
            this.model = model;
            this.attributes = new HashMap<>(attributeNames.size());
            for (QName attributeName : attributeNames) {
                QName attributeType = nodeAttributeService.getAttributeType(attributeName);
                if (attributeType == null) continue;
                InvariantAttributeType typeSupport = attributeTypes.get(attributeType);
                if (typeSupport == null) continue;
                this.attributes.put(attributeName, new RuntimeAttribute(this, attributeName, typeSupport));
            }
        }

        public void setPersistedAttributes(Set<QName> attributeNames) {
            for (QName attributeName : attributeNames) {
                RuntimeAttribute attribute = attributes.get(attributeName);
                if (attribute == null) continue;
                attribute.setPersisted(true);
            }
        }

        public boolean isValid() {
            for (RuntimeAttribute attribute : attributes.values()) {
                if (!attribute.isValid()) return false;
            }
            return true;
        }

        public RuntimeAttribute getFailedAttribute() {
            for (RuntimeAttribute attribute : attributes.values()) {
                if (!attribute.isValid()) return attribute;
            }
            return null;
        }

        public Map<QName, Object> getNewAttributeValues() {
            Map<QName, Object> attributeValues = new HashMap<>(attributes.size());
            for (RuntimeAttribute attribute : attributes.values()) {
                if (attribute.isRelevant()) {
                    boolean valueCanBeUpdated = !attribute.features.get(Feature.VALUE).invariants.isEmpty()
                            || (!attribute.persisted && !attribute.features.get(Feature.DEFAULT).invariants.isEmpty())
                            || !attribute.features.get(Feature.NONBLOCKING_VALUE).invariants.isEmpty();

                    if (valueCanBeUpdated)
                        attributeValues.put(attribute.name, attribute.getValue());
                }
            }
            return attributeValues;
        }

        public void reset() {
            for (RuntimeAttribute attribute : attributes.values()) {
                attribute.reset();
            }
        }

        public void setInvariants(List<InvariantDefinition> invariants) {
            for (RuntimeAttribute attribute : attributes.values()) {
                attribute.setInvariants(invariants);
            }
        }

        Object evaluateInvariant(InvariantDefinition invariant, Map<String, Object> model) {
            return InvariantsRuntime.this.evaluateInvariant(invariant, model);
        }

        Object getAttributeValue(QName attributeName) {
            return InvariantsRuntime.this.nodeAttributeService.getAttribute(nodeRef, attributeName);
        }

    }

    @SuppressWarnings("unused")
    private static class RuntimeAttribute {

        private final RuntimeNode node;
        private final QName name;
        private final QName subtype;
        private final InvariantAttributeType typeSupport;
        private boolean valueFetched;
        private boolean persisted;
        private Object value;
        private Map<String, Object> model;

        private EnumMap<Feature, RuntimeFeature> features;

        /*
         * Attribute initialization.
         */

        public RuntimeAttribute(RuntimeNode node, QName attributeName, InvariantAttributeType attributeType) {
            this.node = node;
            this.name = attributeName;
            this.typeSupport = attributeType;
            this.subtype = attributeType.getAttributeSubtype(attributeName);

            this.features = new EnumMap<>(Feature.class);
            for (Feature feature : Feature.values()) {
                features.put(feature, new RuntimeFeature(this, feature));
            }
        }

        public void setPersisted(boolean persisted) {
            this.persisted = persisted;
        }

        public void reset() {
            this.value = null;
            this.valueFetched = false;
            for (RuntimeFeature feature : features.values()) {
                feature.reset();
            }
        }

        public void setInvariants(List<InvariantDefinition> invariants) {
            InvariantScope attributeScope = typeSupport.getAttributeScope(name);
            InvariantScope attributeTypeScope = typeSupport.getAttributeTypeScope(subtype);

            List<InvariantDefinition> matchingInvariants = new LinkedList<>();
            for (InvariantDefinition invariant : invariants) {
                InvariantScope invariantScope = invariant.getScope();
                if (invariantScope.matches(attributeScope) || invariantScope.matches(attributeTypeScope)) {
                    matchingInvariants.add(invariant);
                }
            }

            for (RuntimeFeature feature : features.values()) {
                feature.setInvariants(matchingInvariants);
            }
        }

        /*
         * Attribute interface.
         */

        public QName getName() {
            return name;
        }

        @SuppressWarnings("unchecked")
        public Map<String, Object> getInvariantModel() {
            if (model == null) {
                CompositeMap combinedModel = new CompositeMap();
                combinedModel.addComposited(Collections.singletonMap(InvariantConstants.MODEL_NODE, node.nodeRef));
                // Considering invariant value may be not necessary,
                //   because invariant value will be persisted value on the next loop.
                // Moreover, considering invariant value here can create infinite loops,
                //   if the model is created for the value calculation.
                combinedModel.addComposited(new SingletonGetterMap<>(InvariantConstants.MODEL_VALUE, this::getPersistedValue));
                if (node.model != null && !node.model.isEmpty()) {
                    combinedModel.addComposited(node.model);
                }
                model = combinedModel;
            }
            return model;
        }

        /*
         * Attribute features.
         */

        public Object getPersistedValue() {
            if (!valueFetched) {
                value = node.getAttributeValue(name);
                valueFetched = true;
            }
            return value;
        }

        private Object getInvariantValue() {
            return getFeatureValue(Feature.VALUE, null);
        }

        private Object getInvariantNonblockingValue() {
            return getFeatureValue(Feature.NONBLOCKING_VALUE, null);
        }

        public Object getInvariantDefault() {
            return getFeatureValue(Feature.DEFAULT, null);
        }

        public Object getDefaultValue() {
            Object result = this.getInvariantDefault();
            if (result instanceof Undefined) {
                throw new IllegalStateException("Invariant on='default' return nothing. Attribute: " + name);
            }
            return this.convertValue(result, this.isMultiple());
        }

        private Object getRawValue() {
            Object invariantValue = getInvariantValue();
            if (invariantValue instanceof Undefined) {
                throw new IllegalStateException("Invariant on='value' return nothing. Attribute: " + name);
            }
            if (invariantValue != null) return invariantValue;
            if (persisted) return getPersistedValue();
            return getDefaultValue();
        }

        public Object getValue() {
            return this.convertValue(this.getRawValue(), this.isMultiple());
        }

        public void setValue(Object value) {
            this.value = value; /* convert to required type? */
        }

        public List<?> getInvariantOptions() {
            return getFeatureValue(Feature.OPTIONS, Collections.emptyList());
        }

        public List<?> getOptions() {
            return this.convertMultipleValues(this.getInvariantOptions());
        }

        public String getTitle() {
            return getFeatureValue(Feature.TITLE, "");
        }

        public String getDescription() {
            return getFeatureValue(Feature.DESCRIPTION, "");
        }

        public String getValueTitle() {
            return getFeatureValue(Feature.VALUE_TITLE, "");
        }

        public String getValueDescription() {
            return getFeatureValue(Feature.VALUE_DESCRIPTION, "");
        }

        public boolean isRelevant() {
            return getFeatureValue(Feature.RELEVANT, true);
        }

        public boolean isMultiple() {
            return getFeatureValue(Feature.MULTIPLE, false);
        }

        public boolean isMandatory() {
            return getFeatureValue(Feature.MANDATORY, false);
        }

        public boolean isMandatoryOnProtected() {
            return getFeatureValue(Feature.MANDATORY_ON_PROTECTED, false);
        }

        public boolean isProtected() {
            return getInvariantValue() != null || getFeatureValue(Feature.PROTECTED, false);
        }

        public boolean isEmpty() {
            Object value = getValue();
            return value == null
                    || value instanceof String && ((String) value).isEmpty()
                    || value instanceof Collection && ((Collection<?>) value).isEmpty();
        }

        private boolean invariantValid() {
            return getFeatureValue(Feature.VALID, false);
        }

        public boolean isValid() {
            if (!isRelevant()) return true;
            if (isEmpty()) return !isMandatory() || (!isMandatoryOnProtected() && isProtected());
            return invariantValid();
        }

        public InvariantDefinition getFailedInvariant() {
            if (!isRelevant()) return null;
            if (isEmpty()) {
                if (!isMandatory() || (!isMandatoryOnProtected() && isProtected())) return null;
                return features.get(Feature.MANDATORY).activeInvariant;
            }
            return features.get(Feature.VALID).activeInvariant;
        }

        /*
         * Helper methods
         */

        private Class<?> getRequiredType() {
            return typeSupport.getAttributeValueType(name);
        }

        private Object convertValue(Object value, boolean multiple) {
            return ConvertUtils.convertValue(value, getRequiredType(), multiple);
        }

        private List<?> convertMultipleValues(Collection<?> values) {
            return ConvertUtils.convertMultipleValues(values, this.getRequiredType());
        }

        @SuppressWarnings("unchecked")
        private <T> T getFeatureValue(Feature feature, T defaultValue) {
            RuntimeFeature runtimeFeature = features.get(feature);
            if (runtimeFeature == null) return defaultValue;
            Object value = runtimeFeature.getValue();
            if (value == null) return defaultValue;
            Class<?> type = feature.getType();
            if (type.isInstance(value)) return (T) value;
            try {
                Constructor<?> constructor = type.getConstructor(value.getClass());
                return (T) constructor.newInstance(value);
            } catch (Exception e) {
                return defaultValue;
            }
        }


    }

    private static class RuntimeFeature {

        private final RuntimeAttribute attribute;
        private final Feature name;
        private List<InvariantDefinition> invariants = new LinkedList<>();
        private boolean evaluated = false;
        private Object evaluatedValue = null;
        private InvariantDefinition activeInvariant = null;

        public RuntimeFeature(RuntimeAttribute attribute, Feature name) {
            this.attribute = attribute;
            this.name = name;
        }

        public void reset() {
            this.evaluated = false;
            this.activeInvariant = null;
        }

        public Object getValue() {
            return evaluated ? evaluatedValue : evaluateValue();
        }

        private Object evaluateValue() {
            for (InvariantDefinition invariant : invariants) {
                Object result = attribute.node.evaluateInvariant(invariant, attribute.getInvariantModel());
                if (name.isSearchedValue(result)) {
                    evaluatedValue = result;
                    evaluated = true;
                    activeInvariant = invariant;
                    break;
                }
            }
            if (evaluated) {
                return evaluatedValue;
            } else {
                return name.getDefaultValue();
            }
        }

        public void setInvariants(List<InvariantDefinition> invariants) {
            for (InvariantDefinition invariant : invariants) {
                if (name.equals(invariant.getFeature())) {
                    this.invariants.add(invariant);
                }
            }
        }

    }

    public void setNodeAttributeService(NodeAttributeService nodeAttributeService) {
        this.nodeAttributeService = nodeAttributeService;
    }

    public void setLanguagesRegistry(Map<String, InvariantLanguage> languages) {
        this.languages = languages;
    }

    public void setAttributeTypesRegistry(Map<QName, InvariantAttributeType> attributeTypes) {
        this.attributeTypes = attributeTypes;
    }

}
