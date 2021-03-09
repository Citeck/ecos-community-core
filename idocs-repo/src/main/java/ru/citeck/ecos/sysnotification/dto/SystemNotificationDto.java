package ru.citeck.ecos.sysnotification.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import ru.citeck.ecos.commons.data.MLText;

import java.time.Instant;

/**
 * @author Pavel Tkachenko
 */
@Data
@AllArgsConstructor
public class SystemNotificationDto {
    private String id;
    private MLText message;
    private Instant time;
}
