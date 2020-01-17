
package ru.citeck.ecos.journals.xml;

import javax.xml.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


/**
 * <p>Java class for header complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="header">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="formatter" type="{http://www.citeck.ru/ecos/journals/1.0}formatter" minOccurs="0"/>
 *         &lt;element name="option" type="{http://www.citeck.ru/ecos/journals/1.0}option" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element name="batch-edit" type="{http://www.citeck.ru/ecos/journals/1.0}batchEdit" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element name="criterion" type="{http://www.citeck.ru/ecos/journals/1.0}criterion" minOccurs="0"/>
 *       &lt;/sequence>
 *       &lt;attribute name="key" use="required" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="default" type="{http://www.w3.org/2001/XMLSchema}boolean" default="false" />
 *       &lt;attribute name="visible" type="{http://www.w3.org/2001/XMLSchema}boolean" default="true" />
 *       &lt;attribute name="searchable" type="{http://www.w3.org/2001/XMLSchema}boolean" default="true" />
 *       &lt;attribute name="sortable" type="{http://www.w3.org/2001/XMLSchema}boolean" default="true" />
 *       &lt;attribute name="groupable" type="{http://www.w3.org/2001/XMLSchema}boolean" default="false" />
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "header", propOrder = {
    "formatter",
    "option",
    "batchEdit",
    "criterion"
})
public class Header {

    protected Formatter formatter;
    protected List<Option> option;
    @XmlElement(name = "batch-edit")
    protected List<BatchEdit> batchEdit;
    protected Criterion criterion;
    @XmlAttribute(name = "key", required = true)
    protected String key;
    @XmlAttribute(name = "default")
    protected Boolean _default;
    @XmlAttribute(name = "visible")
    protected Boolean visible;
    @XmlAttribute(name = "searchable")
    protected Boolean searchable;
    @XmlAttribute(name = "sortable")
    protected Boolean sortable;
    @XmlAttribute(name = "groupable")
    protected Boolean groupable;

    /**
     * Gets the value of the formatter property.
     * 
     * @return
     *     possible object is
     *     {@link Formatter }
     *     
     */
    public Formatter getFormatter() {
        return formatter;
    }

    /**
     * Sets the value of the formatter property.
     * 
     * @param value
     *     allowed object is
     *     {@link Formatter }
     *     
     */
    public void setFormatter(Formatter value) {
        this.formatter = value;
    }

    /**
     * Gets the value of the option property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the option property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getOption().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link Option }
     * 
     * 
     */
    public List<Option> getOption() {
        if (option == null) {
            option = new ArrayList<>();
        }
        return this.option;
    }

    /**
     * Gets the value of the batchEdit property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the batchEdit property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getBatchEdit().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link BatchEdit }
     * 
     * 
     */
    public List<BatchEdit> getBatchEdit() {
        if (batchEdit == null) {
            batchEdit = new ArrayList<>();
        }
        return this.batchEdit;
    }

    /**
     * Gets the value of the criterion property.
     * 
     * @return
     *     possible object is
     *     {@link Criterion }
     *     
     */
    public Criterion getCriterion() {
        return criterion;
    }

    /**
     * Sets the value of the criterion property.
     * 
     * @param value
     *     allowed object is
     *     {@link Criterion }
     *     
     */
    public void setCriterion(Criterion value) {
        this.criterion = value;
    }

    /**
     * Gets the value of the key property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getKey() {
        return key;
    }

    /**
     * Sets the value of the key property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setKey(String value) {
        this.key = value;
    }

    /**
     * Gets the value of the default property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public boolean isDefault() {
        if (_default == null) {
            return false;
        } else {
            return _default;
        }
    }

    /**
     * Sets the value of the default property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setDefault(Boolean value) {
        this._default = value;
    }

    /**
     * Gets the value of the visible property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public boolean isVisible() {
        if (visible == null) {
            return true;
        } else {
            return visible;
        }
    }

    /**
     * Sets the value of the visible property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setVisible(Boolean value) {
        this.visible = value;
    }

    /**
     * Gets the value of the searchable property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public boolean isSearchable() {
        if (searchable == null) {
            return true;
        } else {
            return searchable;
        }
    }

    /**
     * Sets the value of the searchable property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setSearchable(Boolean value) {
        this.searchable = value;
    }

    /**
     * Gets the value of the sortable property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public boolean isSortable() {
        if (sortable == null) {
            return true;
        } else {
            return sortable;
        }
    }

    /**
     * Sets the value of the sortable property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setSortable(Boolean value) {
        this.sortable = value;
    }

    /**
     * Gets the value of the groupable property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public boolean isGroupable() {
        if (groupable == null) {
            return false;
        } else {
            return groupable;
        }
    }

    /**
     * Sets the value of the groupable property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setGroupable(Boolean value) {
        this.groupable = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Header header = (Header) o;

        if (!Objects.equals(option, header.option)) return false;
        if (!Objects.equals(key, header.key)) return false;
        if (!Objects.equals(_default, header._default)) return false;
        if (!Objects.equals(visible, header.visible)) return false;
        if (!Objects.equals(searchable, header.searchable)) return false;
        if (!Objects.equals(sortable, header.sortable)) return false;
        return Objects.equals(groupable, header.groupable);

    }

    @Override
    public int hashCode() {
        int result = option != null ? option.hashCode() : 0;
        result = 31 * result + (key != null ? key.hashCode() : 0);
        result = 31 * result + (_default != null ? _default.hashCode() : 0);
        result = 31 * result + (visible != null ? visible.hashCode() : 0);
        result = 31 * result + (searchable != null ? searchable.hashCode() : 0);
        result = 31 * result + (sortable != null ? sortable.hashCode() : 0);
        result = 31 * result + (groupable != null ? groupable.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Header{" +
                "option=" + option +
                ", key='" + key + '\'' +
                ", _default=" + _default +
                ", visible=" + visible +
                ", searchable=" + searchable +
                ", sortable=" + sortable +
                ", groupable=" + groupable +
                '}';
    }
}
