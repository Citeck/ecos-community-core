package ru.citeck.ecos.records.language.predicate.converters.impl.utils;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.util.ISO8601DateFormat;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.TimeZone;

@Slf4j
public class TimeUtils {

    public static final String CANNOT_PARSE_TIME = "Cannot parse time";
    public static final String CANNOT_PARSE_ISO_TIME = "Cannot parse iso time";
    public static final String CANNOT_PARSE_DURATION = "Cannot parse duration";

    public static String convertTime(String time, Logger log) {
        ZoneOffset offset = OffsetDateTime.now().getOffset();
        if (!StringUtils.contains(time, 'Z') || offset.getTotalSeconds() == 0) {
            return time;
        }

        try {
            Instant timeInstant = Instant.parse(time);
            return DateTimeFormatter.ISO_ZONED_DATE_TIME.format(timeInstant.atZone(offset));
        } catch (Exception e) {
            log.error(CANNOT_PARSE_TIME, e);
        }
        return time;
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
            log.warn(CANNOT_PARSE_DURATION);
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
            log.warn(CANNOT_PARSE_ISO_TIME);
            return null;
        }
    }

    /**
     * Converts the Date argument into a string value
     * @param date java.util.Date
     * @return string in format "yyyy-MM-dd" in UTC
     */
    public static String formatIsoDate(Date date) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        return dateFormat.format(date);
    }

    /**
     * Converts the Date argument into a string value
     * @param date java.util.Date
     * @return string in format "yyyy-MM-dd'T'HH:mm:ss.SSSXXX" in UTC
     */
    public static String formatIsoDateTime(Date date) {
        return ISO8601DateFormat.format(date);
    }
}
