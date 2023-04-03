package ru.citeck.ecos.node;

import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;

import java.io.Serializable;
import java.util.Map;

public interface AlfNodeInfo {

    QName getType();

    NodeRef getNodeRef();

    Map<QName, Serializable> getProperties();

    Serializable getProperty(QName qName);
}
