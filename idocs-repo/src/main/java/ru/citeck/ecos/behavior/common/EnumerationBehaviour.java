/*
 * Copyright (C) 2008-2015 Citeck LLC.
 *
 * This file is part of Citeck EcoS
 *
 * Citeck EcoS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Citeck EcoS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Citeck EcoS. If not, see <http://www.gnu.org/licenses/>.
 */
package ru.citeck.ecos.behavior.common;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.repo.node.NodeServicePolicies;
import org.alfresco.repo.policy.Behaviour.NotificationFrequency;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang.StringUtils;
import ru.citeck.ecos.behavior.JavaBehaviour;
import ru.citeck.ecos.counter.EnumerationException;
import ru.citeck.ecos.counter.EnumerationService;
import ru.citeck.ecos.node.NodeInfo;
import ru.citeck.ecos.node.NodeInfoFactory;
import ru.citeck.ecos.service.CiteckServices;

import java.util.Objects;

public class EnumerationBehaviour implements NodeServicePolicies.OnCreateNodePolicy
{
    private PolicyComponent policyComponent;
    private NodeService nodeService;
    private EnumerationService enumerationService;
    private NodeInfoFactory nodeInfoFactory;

    private QName className;
    private QName numberField;
    private QName barcodeField;
    private QName enumerationStateField;
    private Object enabledState;
    private String templateName;

    public void init() {

        policyComponent.bindClassBehaviour(NodeServicePolicies.OnCreateNodePolicy.QNAME,
                className, new JavaBehaviour(this, "onCreateNode", NotificationFrequency.TRANSACTION_COMMIT));
        if(enumerationStateField == null) {
            enumerationStateField = numberField;
        }
    }

    @Override
    public void onCreateNode(ChildAssociationRef childAssocRef)
    {
        NodeRef nodeRef = childAssocRef.getChildRef();
        if (!nodeService.exists(nodeRef)) {
            return;
        }
        // check if enumeration is enabled
        Object enumerationState = nodeService.getProperty(nodeRef, enumerationStateField);
        if (!Objects.equals(enabledState, enumerationState)) {
            return;
        }
        NodeRef template = enumerationService.getTemplate(templateName);
        NodeInfo nodeInfo = nodeInfoFactory.createNodeInfo(nodeRef);

        String number = null;
        try {
            number = enumerationService.getNumber(template, nodeInfo);
        } catch (EnumerationException e) {
            throw new AlfrescoRuntimeException(e.getMessage(), e);
        }

        nodeService.setProperty(nodeRef, numberField, number);
        fillBarcodeField(nodeRef, number);
    }

    private void fillBarcodeField(NodeRef nodeRef, String number) {
        if (barcodeField != null && StringUtils.isNotBlank(number)) {
            nodeService.setProperty(nodeRef, barcodeField, number);
        }
    }

    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        this.nodeService = serviceRegistry.getNodeService();
        this.enumerationService = (EnumerationService) serviceRegistry.getService(CiteckServices.ENUMERATION_SERVICE);
    }

    public void setPolicyComponent(PolicyComponent policyComponent) {
        this.policyComponent = policyComponent;
    }

    public void setNodeInfoFactory(NodeInfoFactory nodeInfoFactory) {
        this.nodeInfoFactory = nodeInfoFactory;
    }

    public void setClassName(QName className) {
        this.className = className;
    }

    public void setNumberField(QName numberField) {
        this.numberField = numberField;
    }

    public void setBarcodeField(QName barcodeField) {
        this.barcodeField = barcodeField;
    }

    public void setEnumerationStateField(QName enumerationStateField) {
        this.enumerationStateField = enumerationStateField;
    }

    public void setEnabledState(Object enabledState) {
        this.enabledState = enabledState;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    @Deprecated
    public void setOrder(int order) {
        // not used
    }

}
