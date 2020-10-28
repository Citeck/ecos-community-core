package ru.citeck.ecos.records.language.predicate.converters.impl.utils;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.util.ISO8601DateFormat;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

@Slf4j
public class IsoTimeUtils {

    /**
     * Obtains a Duration from a text string such as "PnYnMnDTnHnMnS" and for negate value "-PnYnMnDTnHnMnS".
     * @param isoDuration String representation of a Duration
     * @return New Duration created from parsing the isoDuration
     */
    public static Duration parseIsoDuration(String isoDuration) {
        if (!isoDuration.startsWith("P") && !isoDuration.startsWith("-P")) {
            return null;
        }

        try {
            return DatatypeFactory.newInstance().newDuration(isoDuration);
        } catch (IllegalArgumentException | DatatypeConfigurationException e) {
            log.warn("Cannot parse duration");
            return null;
        }
    }

    /**
     * Converts the string argument into a Calendar value
     * @param isoTime A string in format "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
     * @return Calendar
     */
    public static Date parseIsoTime(String isoTime) {
        try {
            return ISO8601DateFormat.parse(isoTime);
        } catch (AlfrescoRuntimeException e) {
            log.warn("Cannot parse iso time");
            return null;
        }
    }

    /**
     * Converts the Date argument into a string value
     * @param date java.util.Date
     * @return string in format "yyyy-MM-dd" in UTC
     */
    public static String getIsoDateByCalendar(Date date) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        return dateFormat.format(date);
    }

    /**
     * Converts the Date argument into a string value
     * @param date java.util.Date
     * @return string in format "yyyy-MM-dd'T'HH:mm:ss.SSSXXX" in UTC
     */
    public static String getIsoDateTimeByCalendar(Date date) {
        return ISO8601DateFormat.format(date);
    }
}
