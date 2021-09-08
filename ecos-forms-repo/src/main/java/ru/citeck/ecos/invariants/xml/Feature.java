
package ru.citeck.ecos.invariants.xml;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for feature.
 *
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="feature">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="value"/>
 *     &lt;enumeration value="options"/>
 *     &lt;enumeration value="default"/>
 *     &lt;enumeration value="mandatory"/>
 *     &lt;enumeration value="mandatory-on-protected"/>
 *     &lt;enumeration value="protected"/>
 *     &lt;enumeration value="multiple"/>
 *     &lt;enumeration value="relevant"/>
 *     &lt;enumeration value="valid"/>
 *     &lt;enumeration value="title"/>
 *     &lt;enumeration value="description"/>
 *     &lt;enumeration value="value-title"/>
 *     &lt;enumeration value="value-description"/>
 *     &lt;enumeration value="value-order"/>
 *     &lt;enumeration value="datatype"/>
 *     &lt;enumeration value="nodetype"/>
 *     &lt;enumeration value="nonblocking-value"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 *
 */
@XmlType(name = "feature")
@XmlEnum
public enum Feature {

    @XmlEnumValue("value")
    VALUE("value"),
    @XmlEnumValue("options")
    OPTIONS("options"),
    @XmlEnumValue("default")
    DEFAULT("default"),
    @XmlEnumValue("mandatory-on-protected")
    VALID_ON_PROTECTED("mandatory-on-protected"),
    @XmlEnumValue("mandatory")
    MANDATORY("mandatory"),
    @XmlEnumValue("protected")
    PROTECTED("protected"),
    @XmlEnumValue("multiple")
    MULTIPLE("multiple"),
    @XmlEnumValue("relevant")
    RELEVANT("relevant"),
    @XmlEnumValue("valid")
    VALID("valid"),
    @XmlEnumValue("title")
    TITLE("title"),
    @XmlEnumValue("description")
    DESCRIPTION("description"),
    @XmlEnumValue("value-title")
    VALUE_TITLE("value-title"),
    @XmlEnumValue("value-description")
    VALUE_DESCRIPTION("value-description"),
    @XmlEnumValue("value-order")
    VALUE_ORDER("value-order"),
    @XmlEnumValue("datatype")
    DATATYPE("datatype"),
    @XmlEnumValue("nodetype")
    NODETYPE("nodetype"),
    @XmlEnumValue("nonblocking-value")
    NONBLOCKING_VALUE("nonblocking-value");
    private final String value;

    Feature(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static Feature fromValue(String v) {
        for (Feature c: Feature.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
