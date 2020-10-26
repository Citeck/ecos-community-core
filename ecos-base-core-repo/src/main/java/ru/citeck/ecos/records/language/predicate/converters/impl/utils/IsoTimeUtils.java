package ru.citeck.ecos.records.language.predicate.converters.impl.utils;

import lombok.extern.slf4j.Slf4j;

import javax.xml.bind.DatatypeConverter;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

@Slf4j
public class IsoTimeUtils {

    private static final SimpleDateFormat DATE_TIME_FORMAT;
    private static final SimpleDateFormat DATE_FORMAT;

    static {
        DATE_TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        DATE_TIME_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));

        DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

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
     * @param isoTime A string in format "yyyy-MM-dd'T'HH:mm:ss.SSSXXX" or "yyyy-MM-dd"
     * @return Calendar
     */
    public static Calendar parseIsoTime(String isoTime) {
        try {
            return DatatypeConverter.parseDateTime(isoTime);
        } catch (IllegalArgumentException e) {
            log.warn("Cannot parse iso time");
            return null;
        }
    }

    /**
     * Converts the Calendar argument into a string value
     * @param calendar java.util.Calendar
     * @return string in format "yyyy-MM-dd" in UTC
     */
    public static String getIsoDateByCalendar(Calendar calendar) {
        return DATE_FORMAT.format(calendar.getTime());
    }

    /**
     * Converts the Calendar argument into a string value
     * @param calendar java.util.Calendar
     * @return string in format "yyyy-MM-dd'T'HH:mm:ss.SSSXXX" in UTC
     */
    public static String getIsoDateTimeByCalendar(Calendar calendar) {
        return DATE_TIME_FORMAT.format(calendar.getTime());
    }
}
