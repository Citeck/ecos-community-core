package ru.citeck.ecos.model;

import org.alfresco.service.namespace.QName;

/**
 * @author Pavel Simonov
 */
public class EventModel {
    // model
    public static final String PREFIX = "iEvent";

    // namespace
    public static final String NAMESPACE = "http://www.citeck.ru/model/iEvent/1.0";

    // types
    public static final QName TYPE_EVENT = QName.createQName(NAMESPACE, "event");
    public static final QName TYPE_USER_ACTION = QName.createQName(NAMESPACE, "userAction");
    public static final QName TYPE_ADDITIONAL_DATA = QName.createQName(NAMESPACE, "additionalData");
    public static final QName TYPE_ADDITIONAL_CONFIRMER = QName.createQName(NAMESPACE, "additionalConfirmer");
    public static final QName TYPE_ADDITIONAL_PERFORMERS = QName.createQName(NAMESPACE, "additionalPerformers");

    // properties
    public static final QName PROP_TYPE = QName.createQName(NAMESPACE, "type");
    public static final QName PROP_ADDITIONAL_DATA_TYPE = QName.createQName(NAMESPACE, "additionalDataType");
    public static final QName PROP_CONFIRMATION_MESSAGE = QName.createQName(NAMESPACE, "confirmationMessage");
    public static final QName PROP_SUCCESS_MESSAGE = QName.createQName(NAMESPACE, "successMessage");
    public static final QName PROP_SUCCESS_MESSAGE_SPAN_CLASS = QName.createQName(NAMESPACE, "successMessageSpanClass");
    public static final QName PROP_COMMENT = QName.createQName(NAMESPACE, "comment");

    // associations
    public static final QName ASSOC_EVENT_SOURCE = QName.createQName(NAMESPACE, "eventSource");
    public static final QName ASSOC_CONDITIONS = QName.createQName(NAMESPACE, "conditions");
    public static final QName ASSOC_AUTHORIZED_ROLES = QName.createQName(NAMESPACE, "authorizedRoles");
    public static final QName ASSOC_AUTHORIZED_ROLES_PROP = QName.createQName(NAMESPACE, "authorizedRoles_prop");
    public static final QName ASSOC_ADDITIONAL_DATA_ITEMS = QName.createQName(NAMESPACE, "additionalDataItems");
    public static final QName ASSOC_CONFIRMER = QName.createQName(NAMESPACE, "confirmer");
    public static final QName ASSOC_PERFORMERS = QName.createQName(NAMESPACE, "performers");

}
