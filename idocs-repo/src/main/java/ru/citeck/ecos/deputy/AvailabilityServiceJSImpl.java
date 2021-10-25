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

import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.NodeRef;
import ru.citeck.ecos.service.CiteckServices;
import ru.citeck.ecos.utils.AlfrescoScopableProcessorExtension;

public class AvailabilityServiceJSImpl extends AlfrescoScopableProcessorExtension implements AvailabilityService {

    private AvailabilityService availabilityService;

    @Override
    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        super.setServiceRegistry(serviceRegistry);
        this.availabilityService = (AvailabilityService) serviceRegistry.getService(CiteckServices.AVAILABILITY_SERVICE);
    }

    @Override
    public boolean getUserAvailability(String userName) {
        return availabilityService.getUserAvailability(userName);
    }

    @Override
    public boolean getUserAvailability(NodeRef userRef) {
        return availabilityService.getUserAvailability(userRef);
    }

    @Override
    public String getUserUnavailableAutoAnswer(String userName) {
        return availabilityService.getUserUnavailableAutoAnswer(userName);
    }

    @Override
    public void setUserAvailability(String userName, boolean availability) {
        availabilityService.setUserAvailability(userName, availability);
    }

    @Override
    public void setUserAvailabilityAsync(String userName, boolean availability) {
        availabilityService.setUserAvailabilityAsync(userName, availability);
    }

    @Override
    public void setUserAvailability(NodeRef user, boolean availability) {
        availabilityService.setUserAvailability(user, availability);
    }

    @Override
    public void setUserAvailabilityAsync(NodeRef user, boolean availability) {
        availabilityService.setUserAvailabilityAsync(user, availability);
    }

    @Override
    public boolean getCurrentUserAvailability() {
        return availabilityService.getCurrentUserAvailability();
    }

    @Override
    public void setCurrentUserAvailability(boolean availability) {
        availabilityService.setCurrentUserAvailability(availability);
    }

    @Override
    public void setCurrentUserAvailabilityAsync(boolean availability) {
        availabilityService.setCurrentUserAvailabilityAsync(availability);
    }
}
