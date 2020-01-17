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
package ru.citeck.ecos.calendar;

import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.search.ResultSet;
import org.alfresco.service.cmr.search.ResultSetRow;
import org.alfresco.service.cmr.search.SearchService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import ru.citeck.ecos.model.BusinessCalendarModel;

import java.util.*;

public class BusinessCalendar extends GregorianCalendar {
    private Map<Date, Date> extraWorkingDays /*= fillDateSet("working-day")*/;
    private Map<Date, Date> extraDayOff /*= fillDateSet("day-off")*/;
    private static final Log log = LogFactory.getLog(BusinessCalendar.class);
    private SearchService searchService;

    public void add(int field, int amount)
    {
        if(amount>0)
        {
            while(amount!=0)
            {
                super.add(field, 1);
                if(this.isBusinessDay())
                {
                    amount--;
                }
            }
        }
        else
        {
            while(amount!=0)
            {
                super.add(field, -1);
                if(this.isBusinessDay())
                {
                    amount++;

                }
            }
        }
    }

    public boolean isBusinessDay()
    {
        if (this.get(DAY_OF_WEEK) == Calendar.SUNDAY || this.get(DAY_OF_WEEK) == Calendar.SATURDAY) {
            return mapContainsDate(extraWorkingDays, this);
        } else {
            return !mapContainsDate(extraDayOff, this);
        }
    }

    public BusinessCalendar()
    {
    }

    public void setWorkingDays()
    {
        log.debug("fillDateSet(working-day)");
        extraWorkingDays = fillDateSet("working-day");
    }

    public void setDayOff()
    {
        log.debug("fillDateSet(day-off)");
        extraDayOff = fillDateSet("day-off");
    }

    public boolean mapContainsDate(Map<Date, Date> dates, Calendar calendar)
    {
        Date dateToCheck = calendar.getTime();
        if(dates!=null && !dates.isEmpty())
        {
            for(Map.Entry<Date, Date> entry : dates.entrySet())
            {
                Date dateFrom = entry.getKey();
                Date dateTo = entry.getValue();
                if(dateToCheck.after(dateFrom) && dateToCheck.before(dateTo))
                {
                    return true;
                }
            }
        }
        return false;
    }

    public Map<Date, Date> fillDateSet(String remark)
    {
        Map<Date, Date> dateSet = new HashMap<>();
        String search_query = "TYPE:\""+BusinessCalendarModel.TYPE_CALENDAR+"\" AND @bcal\\:remark:\""+remark+"\"";
        log.debug("   Search query: " + search_query);
        ResultSet rs = null;
        try {
            log.debug("SearchService "+searchService);
            rs = searchService.query(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, SearchService.LANGUAGE_LUCENE, search_query);
            log.debug("      Query result contains " + rs.length() + " records.");
            if (rs.length() > 0) {
                log.debug("      Query result contains " + rs.length() + " records.");
                for (ResultSetRow row : rs) {
                    dateSet.put((Date)row.getValue(BusinessCalendarModel.PROP_DATE_FROM), (Date)row.getValue(BusinessCalendarModel.PROP_DATE_TO));
                }
            }
        }
        catch (Exception e)
        {
            log.error("error "+e);
        }
        finally {
            if (rs != null)
                rs.close();
        }
        log.debug("COMPLETED!!!!!!!!!!");
        return dateSet;
    }

    public void setSearchService(SearchService searchService) {
        this.searchService = searchService;
    }

    public static BusinessCalendar getInstance()
    {
        BusinessCalendar cal = new BusinessCalendar();
        cal.setWorkingDays();
        cal.setDayOff();
        return cal;
    }
}
