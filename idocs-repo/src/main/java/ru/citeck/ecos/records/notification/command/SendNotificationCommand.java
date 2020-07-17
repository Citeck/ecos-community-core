package ru.citeck.ecos.records.notification.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.citeck.ecos.commands.annotation.CommandType;
import ru.citeck.ecos.records2.RecordRef;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@CommandType("ecos.notifications.send")
public class SendNotificationCommand {

    private RecordRef templateRef;
    private NotificationType type;
    private String lang;
    private List<String> recipients;
    private Map<String, Object> model;
    private String from;

}
