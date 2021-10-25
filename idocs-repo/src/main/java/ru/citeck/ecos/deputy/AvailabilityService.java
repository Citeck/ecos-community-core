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

import org.alfresco.service.cmr.repository.NodeRef;

public interface AvailabilityService {

	/**
	 * Get specified user availability.
	 *
	 * @param userName - user to query availability
	 * @return true if user is available, false if user is not available
	 */
    boolean getUserAvailability(String userName);

	/**
	 * Get specified user availability.
	 *
	 * @param userRef - user to query availability
	 * @return true if user is available, false if user is not available
	 */
    boolean getUserAvailability(NodeRef userRef);

	/**
	 * Get specified user autoAnswer.
	 *
	 * @param userName - user to query autoAnswer
	 * @return empty string is message is null
	 */
    String getUserUnavailableAutoAnswer(String userName);

	/**
	 * Set specified user availability.
	 *
	 * @param userName - user to set availability
	 * @param availability - true if user is available, false if user is not available
	 */
    void setUserAvailability(String userName, boolean availability);

    /**
     * Set specified user availability.
     *
     * @param userName - user to set availability
     * @param availability - true if user is available, false if user is not available
     */
    void setUserAvailabilityAsync(String userName, boolean availability);

	/**
	 * Set specified user availability.
	 *
	 * @param user - user to set availability
	 * @param availability - true if user is available, false if user is not available
	 */
    void setUserAvailability(NodeRef user, boolean availability);

    /**
     * Set specified user availability.
     *
     * @param user - user to set availability
     * @param availability - true if user is available, false if user is not available
     */
    void setUserAvailabilityAsync(NodeRef user, boolean availability);

	/**
	 * @see #getUserAvailability(String)
	 */
    boolean getCurrentUserAvailability();

	/**
	 * @see #setUserAvailability(String, boolean)
	 */
    void setCurrentUserAvailability(boolean availability);

    /**
     * @see #setUserAvailability(String, boolean)
     */
    void setCurrentUserAvailabilityAsync(boolean availability);

}
