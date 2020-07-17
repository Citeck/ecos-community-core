package ru.citeck.ecos.records.notification.command;

import lombok.Data;

@Data
public class SendNotificationResult {
    private String status;
    private String result;
}
