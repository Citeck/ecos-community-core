package ru.citeck.ecos.cmmn.service.util;

import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import ru.citeck.ecos.cmmn.CMMNUtils;
import ru.citeck.ecos.cmmn.condition.Condition;
import ru.citeck.ecos.cmmn.condition.ConditionProperty;
import ru.citeck.ecos.cmmn.condition.ConditionsList;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

/**
 * @author Maxim Strizhov
 */
class CaseConditionExporter {

    private ConditionsList conditions = new ConditionsList();

    CaseConditionExporter(List<NodeRef> conditionsRef, NodeService nodeService, CMMNUtils utils) {
        for (NodeRef conditionRef : conditionsRef) {
            Condition condition = new Condition();
            condition.setType(utils.convertToXMLQName(nodeService.getType(conditionRef)));
            Map<QName, Serializable> properties = nodeService.getProperties(conditionRef);
            for (Map.Entry<QName, Serializable> entry : properties.entrySet()) {
                QName key = entry.getKey();
                if (!key.getNamespaceURI().equals("http://www.alfresco.org/model/system/1.0") &&
                        !key.getNamespaceURI().equals("http://www.alfresco.org/model/content/1.0")) {
                    ConditionProperty conditionProperty = new ConditionProperty();
                    conditionProperty.setType(utils.convertToXMLQName(key));
                    Serializable value = entry.getValue();
                    if (value != null) {
                        conditionProperty.setValue(value.toString());
                    } else {
                        conditionProperty.setValue("");
                    }
                    condition.getProperties().add(conditionProperty);
                }
            }
            conditions.getConditions().add(condition);
        }
    }

    String generateXML() throws JAXBException {
        JAXBContext jaxbContext = JAXBContext.newInstance(ConditionsList.class);
        Marshaller jaxbMarshaller = jaxbContext.createMarshaller();

        // output pretty printed
        jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        StringWriter stringWriter = new StringWriter();
        jaxbMarshaller.marshal(conditions, stringWriter);
        return stringWriter.toString();
    }
}
