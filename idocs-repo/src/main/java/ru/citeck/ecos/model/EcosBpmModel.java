package ru.citeck.ecos.model;

import org.alfresco.service.namespace.QName;

public interface EcosBpmModel {

    String NAMESPACE = "http://www.citeck.ru/model/ecos/bpm/1.0";

    QName TYPE_PROCESS_MODEL = QName.createQName(NAMESPACE, "processModel");
    QName TYPE_DEPLOYMENT_INFO = QName.createQName(NAMESPACE, "deploymentInfo");

    QName PROP_INDEX = QName.createQName(NAMESPACE, "index");
    QName PROP_CATEGORY = QName.createQName(NAMESPACE, "category");
    QName PROP_JSON_MODEL = QName.createQName(NAMESPACE, "jsonModel");
    QName PROP_THUMBNAIL = QName.createQName(NAMESPACE, "thumbnail");
    QName PROP_PROCESS_ID = QName.createQName(NAMESPACE, "processId");
    QName PROP_ENGINE = QName.createQName(NAMESPACE, "engine");
    QName PROP_MODEL_IMAGE = QName.createQName(NAMESPACE, "modelImage");
    QName PROP_START_FORM_REF = QName.createQName(NAMESPACE, "startFormRef");
    QName PROP_DEPLOYMENT_PROC_DEF_ID = QName.createQName(NAMESPACE, "deploymentProcDefId");
    QName PROP_DEPLOYMENT_PROC_DEF_VERSION = QName.createQName(NAMESPACE, "deploymentProcDefVersion");
    QName PROP_DEPLOYMENT_ENGINE = QName.createQName(NAMESPACE, "deploymentEngine");
    QName PROP_DEPLOYMENT_VERSION = QName.createQName(NAMESPACE, "deploymentVersion");

    QName ASSOC_DEPLOYMENTS = QName.createQName(NAMESPACE, "deployments");
}
