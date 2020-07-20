package ru.citeck.ecos.records.notification.command;

import kotlin.Unit;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.commands.CommandsService;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records2.graphql.meta.annotation.MetaAtt;
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

    @Autowired
    public NotificationCommandService(CommandsService commandsService, RecordsService recordsService) {
        this.commandsService = commandsService;
        this.recordsService = recordsService;
    }


    public void send(RecordRef record, RecordRef templateRef, NotificationType type, List<String> recipients,
                     Locale locale) {

        TemplateModelDto metaModel = recordsService.getMeta(templateRef, TemplateModelDto.class);

        Map<String, String> model = new HashMap<>();
        metaModel.model.forEach((s, dataValue) -> model.put(s, dataValue.asText()));

        Map<String, Object> data = new HashMap<>();
        RecordMeta attributes = recordsService.getAttributes(record, model);
        attributes.forEach(data::put);


        SendNotificationCommand command = new SendNotificationCommand(
            templateRef, type, locale.toString(), recipients, data, "test@mail.ru"
        );

        TransactionUtils.doAfterCommit(() -> commandsService.execute(b -> {
            b.setTtl(Duration.ZERO);
            b.setTargetApp(TARGET_APP);
            b.setBody(command);
            return Unit.INSTANCE;
        }));
    }

    @Data
    private static class TemplateModelDto {
        @MetaAtt("model")
        private ObjectData model;
    }

}
