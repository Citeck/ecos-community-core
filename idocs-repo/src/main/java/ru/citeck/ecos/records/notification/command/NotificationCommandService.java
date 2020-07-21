package ru.citeck.ecos.records.notification.command;

import kotlin.Unit;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.commands.CommandsService;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.notifications.lib.NotificationType;
import ru.citeck.ecos.records.notification.NotificationTemplateService;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records2.meta.RecordsMetaService;
import ru.citeck.ecos.utils.TransactionUtils;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class NotificationCommandService {

    private static final String TARGET_APP = "notifications";

    private final CommandsService commandsService;
    private final RecordsService recordsService;
    private final RecordsMetaService recordsMetaService;
    private final NotificationTemplateService notificationTemplateService;

    @Autowired
    public NotificationCommandService(CommandsService commandsService, RecordsService recordsService,
                                      RecordsMetaService recordsMetaService,
                                      NotificationTemplateService notificationTemplateService) {
        this.commandsService = commandsService;
        this.recordsService = recordsService;
        this.recordsMetaService = recordsMetaService;
        this.notificationTemplateService = notificationTemplateService;
    }


    public void send(RecordRef record, RecordRef templateRef, NotificationType type, List<String> recipients,
                     String from, Locale locale, Map<String, Object> additionalMeta) {
        Map<String, String> model = notificationTemplateService.getTemplateModel(templateRef);

        Map<String, String> rootModel = new HashMap<>();
        Map<String, String> additionalModel = new HashMap<>();

        model.forEach((key, att) -> {

            if (StringUtils.startsWithAny(att, new String[]{"$", ".att(n:\"$", ".atts(n:\"$"})) {
                additionalModel.put(key, att.replaceFirst("\\$", ""));
            } else {
                rootModel.put(key, att);
            }
        });

        Map<String, Object> data = new HashMap<>();

        RecordMeta attributes = recordsService.getAttributes(record, rootModel);
        attributes.forEach(data::put);

        if (MapUtils.isNotEmpty(additionalMeta)) {
            RecordMeta additionalRecord = recordsMetaService.getMeta(additionalMeta, additionalModel);
            ObjectData additionalAttrs = additionalRecord.getAttributes();
            additionalAttrs.forEach(data::put);
        }

        SendNotificationCommand command = new SendNotificationCommand(
            templateRef, type, locale.toString(), recipients, data, from
        );

        TransactionUtils.doAfterCommit(() -> commandsService.execute(b -> {
            b.setTtl(Duration.ZERO);
            b.setTargetApp(TARGET_APP);
            b.setBody(command);
            return Unit.INSTANCE;
        }));
    }

}
