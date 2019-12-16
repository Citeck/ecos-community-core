
package ru.citeck.ecos.cmmn.model;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for tPlanningTable complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="tPlanningTable">
 *   &lt;complexContent>
 *     &lt;extension base="{http://www.omg.org/spec/CMMN/20151109/MODEL}tTableItem">
 *       &lt;sequence>
 *         &lt;element ref="{http://www.omg.org/spec/CMMN/20151109/MODEL}tableItem" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element ref="{http://www.omg.org/spec/CMMN/20151109/MODEL}applicabilityRule" maxOccurs="unbounded" minOccurs="0"/>
 *       &lt;/sequence>
 *       &lt;anyAttribute processContents='lax' namespace='##other'/>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "tPlanningTable", namespace = "http://www.omg.org/spec/CMMN/20151109/MODEL", propOrder = {
    "tableItem",
    "applicabilityRule"
})
public class TPlanningTable
    extends TTableItem
{

    @XmlElementRef(name = "tableItem", namespace = "http://www.omg.org/spec/CMMN/20151109/MODEL", type = JAXBElement.class, required = false)
    protected List<JAXBElement<? extends TTableItem>> tableItem;
    @XmlElement(namespace = "http://www.omg.org/spec/CMMN/20151109/MODEL")
    protected List<TApplicabilityRule> applicabilityRule;

    /**
     * Gets the value of the tableItem property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the tableItem property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getTableItem().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link JAXBElement }{@code <}{@link TTableItem }{@code >}
     * {@link JAXBElement }{@code <}{@link TDiscretionaryItem }{@code >}
     * {@link JAXBElement }{@code <}{@link TPlanningTable }{@code >}
     * 
     * 
     */
    public List<JAXBElement<? extends TTableItem>> getTableItem() {
        if (tableItem == null) {
            tableItem = new ArrayList<>();
        }
        return this.tableItem;
    }

    /**
     * Gets the value of the applicabilityRule property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the applicabilityRule property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getApplicabilityRule().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link TApplicabilityRule }
     * 
     * 
     */
    public List<TApplicabilityRule> getApplicabilityRule() {
        if (applicabilityRule == null) {
            applicabilityRule = new ArrayList<>();
        }
        return this.applicabilityRule;
    }

}
