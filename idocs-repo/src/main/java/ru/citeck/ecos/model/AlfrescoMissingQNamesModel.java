package ru.citeck.ecos.model;

import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;

public class AlfrescoMissingQNamesModel {

    public static final String CONTENT_NAMESPACE = NamespaceService.CONTENT_MODEL_1_0_URI;
    public static final String CM_PREFIX = NamespaceService.CONTENT_MODEL_PREFIX;

    public static final QName PROP_MIDDLE_NAME = QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, "middleName");
}
