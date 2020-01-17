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
package ru.citeck.ecos.deputy;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.node.NodeServicePolicies;
import org.alfresco.repo.policy.Behaviour.NotificationFrequency;
import ru.citeck.ecos.behavior.JavaBehaviour;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import ru.citeck.ecos.model.DeputyModel;
import ru.citeck.ecos.service.CiteckServices;

import java.io.Serializable;
import java.util.Map;


public class AvailabilityBehaviour implements NodeServicePolicies.OnUpdatePropertiesPolicy, NodeServicePolicies.OnAddAspectPolicy
{
    private PolicyComponent policyComponent;
    private NodeService nodeService;
    private DeputyService deputyService;

    public void init() {
        policyComponent.bindClassBehaviour(NodeServicePolicies.OnUpdatePropertiesPolicy.QNAME, DeputyModel.ASPECT_AVAILABILITY,
                new JavaBehaviour(this, "onUpdateProperties", NotificationFrequency.EVERY_EVENT));

        policyComponent.bindClassBehaviour(NodeServicePolicies.OnAddAspectPolicy.QNAME, DeputyModel.ASPECT_AVAILABILITY,
                new JavaBehaviour(this, "onAddAspect", NotificationFrequency.FIRST_EVENT));
    }

    public void setPolicyComponent(PolicyComponent policyComponent) {
        this.policyComponent = policyComponent;
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        this.deputyService = (DeputyService) serviceRegistry.getService(CiteckServices.DEPUTY_SERVICE);
    }

    @Override
    public void onUpdateProperties(NodeRef nodeRef,
            Map<QName, Serializable> before,
            Map<QName, Serializable> after)
    {
        if(!nodeService.exists(nodeRef)) {
            return;
        }
        Boolean availableBefore = (Boolean) before.get(DeputyModel.PROP_AVAILABLE);
        Boolean availableAfter = (Boolean) after.get(DeputyModel.PROP_AVAILABLE);
        if(availableAfter.equals(availableBefore)) {
            return;
        }

        String userName = (String) nodeService.getProperty(nodeRef, ContentModel.PROP_USERNAME);
        if(userName == null) {
            return;
        }

        deputyService.userAvailabilityChanged(userName);
    }
    @Override
    public void onAddAspect(NodeRef nodeRef, QName aspectTypeQName) {
        if(!nodeService.exists(nodeRef)) {
            return;
        }

        String userName = (String) nodeService.getProperty(nodeRef, ContentModel.PROP_USERNAME);
        if(userName == null) {
            return;
        }

        deputyService.userAvailabilityChanged(userName);
    }

}
