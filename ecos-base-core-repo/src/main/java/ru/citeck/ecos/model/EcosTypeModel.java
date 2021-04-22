package ru.citeck.ecos.model;

import org.alfresco.service.namespace.QName;

public class EcosTypeModel {

    // namespace
    public static final String NAMESPACE = "http://www.citeck.ru/model/ecos/type/1.0";

    // aspects
    public static final QName ASPECT_HAS_TYPE = QName.createQName(NAMESPACE, "hasType");
    public static final QName ASPECT_FOR_TYPE = QName.createQName(NAMESPACE, "forTypeAspect");
    public static final QName ASPECT_TENANT_SITE = QName.createQName(NAMESPACE, "tenantSite");
    public static final QName ASPECT_TYPE_ROOT = QName.createQName(NAMESPACE, "typeRootAspect");

    // properties
    public static final QName PROP_TYPE = QName.createQName(NAMESPACE, "type");
    public static final QName PROP_FOR_TYPE = QName.createQName(NAMESPACE, "forType");
    public static final QName PROP_ROOT_FOR_TYPE = QName.createQName(NAMESPACE, "rootForType");
    public static final QName PROP_TENANT = QName.createQName(NAMESPACE, "tenant");
}
