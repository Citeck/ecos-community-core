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
package ru.citeck.ecos.invariants;

import org.alfresco.service.namespace.QName;

import java.util.Collections;
import java.util.List;

public enum Feature {

    VALUE(Object.class, null, "value"),
    OPTIONS(List.class, Collections.emptyList(), "options"),
    DEFAULT(Object.class, null, "default value"),
    MANDATORY(Boolean.class, false, "attribute is mandatory"),
    MANDATORY_ON_PROTECTED(Boolean.class, false, "attribute should be mandatory on protected"){
        @Override
        public String toString() {
            return "mandatory-on-protected";
        }
    },
    PROTECTED(Boolean.class, false, "attribute is protected"),
    MULTIPLE(Boolean.class, false, "attribute is multiple"),
    RELEVANT(Boolean.class, true, "attribute is relevant"),
    VALID(Boolean.class, true, "attribute should be valid") {
        @Override
        public boolean isSearchedValue(Object value) {
            return Boolean.FALSE.equals(value);
        }
    },
    TITLE(String.class, "", "attribute title"),
    DESCRIPTION(String.class, "", "attribute description"),

    VALUE_TITLE(String.class, "", "value title") {
        @Override
        public String toString() {
            return "value-title";
        }
    },
    VALUE_DESCRIPTION(String.class, "", "value description") {
        @Override
        public String toString() {
            return "value-description";
        }
    },
    VALUE_ORDER(Double.class, 0d, "value-order") {
        @Override
        public String toString() {
            return "value-order";
        }
    },

    DATATYPE(QName.class, null, ""),
    NODETYPE(QName.class, null, ""),
    NONBLOCKING_VALUE(Object.class, null, "nonblocking value") {
        @Override
        public String toString() {
            return "nonblocking-value";
        }
    };

    private final Class<?> type;
    private final Object defaultValue;
    private final String defaultDescription;

    private Feature(Class<?> type, Object defaultValue, String defaultDescription) {
        this.type = type;
        this.defaultValue = defaultValue;
        this.defaultDescription = defaultDescription;
    }

    public static Feature valueOf(ru.citeck.ecos.invariants.xml.Feature on) {
        return valueOf(on.toString());
    }

    public Class<?> getType() {
        return type;
    }

    public boolean isSearchedValue(Object value) {
        return value != null;
    }

    @Override
    public String toString() {
        return super.toString().toLowerCase();
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public String getDefaultDescription() {
        return defaultDescription;
    }

}
