package ru.citeck.ecos.records.language.predicate.converters.impl.utils;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class TimeUtils {

    public static final String CANNOT_PARSE_TIME = "Cannot parse time";

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
}
