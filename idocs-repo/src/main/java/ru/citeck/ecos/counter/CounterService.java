/*
 * Copyright (C) 2008-2018 Citeck LLC.
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
package ru.citeck.ecos.counter;

public interface CounterService {

    /**
     * Switch alfresco counter to using emodel counter
     */
    void switchToEmodelCounter(String alfCounterName, String emodelNumTemplateId, String emodelCounterKey);

    /**
     * Sets current value for specified counter.
     * This value can be further retrieved by getCounterLast function.
     *
     * @param counterName
     * @param value
     */
    void setCounterLast(String counterName, long value);

    /**
     * Gets last number, that was given by this counter or null, if counter does not exist.
     *
     * @param counterName
     * @return
     */
    Long getCounterLast(String counterName);

    /**
     * Gets next number, that would be given by this counter.
     *
     * @param counterName
     * @param increment   - if true, increment counter, if false - just give the value or null
     * @return Note: if increment == false and counter does not exist, null should be returned.
     */
    Long getCounterNext(String counterName, boolean increment);

}
