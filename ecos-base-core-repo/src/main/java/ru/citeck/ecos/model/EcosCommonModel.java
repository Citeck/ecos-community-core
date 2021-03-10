package ru.citeck.ecos.model;

import org.alfresco.service.namespace.QName;

public interface EcosCommonModel {

    String NAMESPACE = "http://www.citeck.ru/model/ecos/common/1.0";

    QName PROP_KEY = QName.createQName(NAMESPACE, "key");
    QName PROP_TAG = QName.createQName(NAMESPACE, "tag");
}
