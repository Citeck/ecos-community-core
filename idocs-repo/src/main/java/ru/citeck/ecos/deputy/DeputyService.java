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

import org.springframework.scheduling.annotation.Async;

import java.util.List;

/**
 * Extended interface for Java implementation of DeputyService.
 *
 * Deputy Service employs Deputy Listeners (@see DeputyListener) to add custom behaviour
 *  whenever events important for deputation occur.
 * These events can be result of activity of DeputyService or other services.
 * When it is DeputyService, it should call Deputy Listeners immediately.
 * When it is other services, some mechanism (listener) should call DeputyService listener method to activate DeputyListeners.
 * DeputyService should be abstract towards the implementation of listener of necessary events.
 * All that is important is that this listeners should call corresponding methods of DeputyService.
 *
 * @author Sergey Tiunov
 *
 */
public interface DeputyService extends DeputyServiceGeneric<List<String>, List<String>, List<String>>
{

	/////////////////////////////////////////////////////////////////
	//                    LISTENER INTERFACE                       //
	/////////////////////////////////////////////////////////////////

	/**
	 * This should be called, whenever user availability is changed.
	 *
	 * This is intended to be called by some listener, that listens to corresponding event.
	 * Deputy Service should transmit this event to registered DeputyListeners.
	 *
	 * @param userName
	 */
    void userAvailabilityChanged(String userName);

	/**
	 * This should be called, whenever user is added or removed from/to group.
	 *
	 * This is intended to be called by some listener, that listens to corresponding event.
	 * Deputy Service should transmit this event to registered DeputyListeners.
	 *
	 * @param userName
	 * @param groupFullName
	 */
    void userMembershipChanged(String userName, String groupFullName);

}
