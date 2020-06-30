package ru.citeck.ecos.service;

import org.alfresco.cmis.client.impl.AlfrescoUtils;
import org.alfresco.service.namespace.QName;

/**
 * @author Pavel Simonov
 */
public interface AlfrescoServices {

    String ALFRESCO_NAMESPACE = AlfrescoUtils.ALFRESCO_NAMESPACE;

    QName MESSAGE_SERVICE = QName.createQName(ALFRESCO_NAMESPACE, "messageService");
    QName REPOSITORY_STATE = QName.createQName(ALFRESCO_NAMESPACE, "repositoryState");
    QName POLICY_COMPONENT = QName.createQName(ALFRESCO_NAMESPACE, "policyComponent");
    QName ACTIVITI_RUNTIME_SERVICE = QName.createQName(ALFRESCO_NAMESPACE, "activitiRuntimeService");
    QName ACTIVITI_TASK_SERVICE = QName.createQName(ALFRESCO_NAMESPACE, "activitiTaskService");
    QName TENANT_SERVICE = QName.createQName(ALFRESCO_NAMESPACE, "tenantService");
    QName SCRIPT_SERVICE = QName.createQName(ALFRESCO_NAMESPACE, "scriptService");
}
