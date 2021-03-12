package ru.citeck.ecos.sysnotification.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.citeck.ecos.commons.data.MLText;

import java.time.Instant;

/**
 * @author Pavel Tkachenko
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SystemNotificationDto {
    protected String id;
    protected MLText message;
    protected Instant time;
    protected Instant created;
    protected Instant modified;
}
