package ru.citeck.ecos.model;

import org.alfresco.service.namespace.QName;

public interface EcosAutoModel {

    String NAMESPACE = "http://www.citeck.ru/ecos/automodel/1.0";

    QName TYPE_MODEL_DEF = QName.createQName(NAMESPACE, "modelDef");

    QName PROP_ECOS_TYPE_REF = QName.createQName(NAMESPACE, "ecosTypeRef");
    QName PROP_MODEL_PREFIX = QName.createQName(NAMESPACE, "modelPrefix");
    QName PROP_MODELS_COUNTER = QName.createQName(NAMESPACE, "modelsCounter");
}
