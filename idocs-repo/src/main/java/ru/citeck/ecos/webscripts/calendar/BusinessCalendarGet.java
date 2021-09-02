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
package ru.citeck.ecos.webscripts.calendar;

import org.alfresco.service.cmr.search.SearchService;
import org.apache.commons.lang3.time.FastDateFormat;
import org.springframework.extensions.webscripts.*;
import ru.citeck.ecos.calendar.BusinessCalendar;

import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class BusinessCalendarGet extends DeclarativeWebScript {

    // web script arguments
    private static final String PARAM_ADD_FIELD = "addField";
    private static final String PARAM_ADD_AMOUNT = "addAmount";
    private static final String PARAM_CURRENT_DATE = "currentDate";
    private static final FastDateFormat DATE_FORMAT = FastDateFormat.getInstance("yyyy-MM-dd'T'HH:mm:ss.SSSZZ");

    private SearchService searchService;

    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache) {

        String addField = req.getParameter(PARAM_ADD_FIELD);
        String addAmount = req.getParameter(PARAM_ADD_AMOUNT);
        String currentDateParam = req.getParameter(PARAM_CURRENT_DATE);


        if (addField == null) {
            status.setCode(Status.STATUS_BAD_REQUEST, "Parameter addField is mandatory");
            return null;
        }

        if (addAmount == null) {
            status.setCode(Status.STATUS_BAD_REQUEST, "Parameter addAmount is mandatory");
            return null;
        }

        Date currentDate = null;
        try {
            currentDate = DATE_FORMAT.parse(currentDateParam);
        } catch (ParseException ex) {
            throw new WebScriptException("Unable to parse date: " + ex.getMessage(), ex);
        }

        int amount = Integer.parseInt(addAmount);

        Map<String, Object> model = new HashMap<>();
        BusinessCalendar calendar = new BusinessCalendar();
        calendar.setSearchService(searchService);
        calendar.setWorkingDays();
        calendar.setDayOff();
        calendar.setTime(currentDate);
        if ("days".equals(addField)) {
            calendar.add(Calendar.DAY_OF_YEAR, amount);
            model.put("date", calendar.getTime());
        }
        return model;

    }

    public void setSearchService(SearchService searchService) {
        this.searchService = searchService;
    }
}
