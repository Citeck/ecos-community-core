package ru.citeck.ecos.records.notification.command;

import kotlin.Unit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.commands.CommandsProperties;
import ru.citeck.ecos.commands.CommandsService;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.utils.TransactionUtils;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationCommandService {

    private static final String TARGET_APP = "notifications";

    private final CommandsService commandsService;
    private final CommandsProperties commandsProperties;


    public void send(RecordRef templateRef, NotificationType type, List<String> recipients) {

        Map<String, Object> model = new HashMap<>();
        model.put("firstName", "Roman");
        model.put("lastName", "Makarskiy");
        model.put("age", "777");

        SendNotificationCommand command = new SendNotificationCommand(
            templateRef, type, "en", recipients, model, "test@mail.ru"
        );

        TransactionUtils.doAfterCommit(() -> commandsService.execute(b -> {
            b.setTtl(Duration.ZERO);
            b.setTargetApp(TARGET_APP);
            b.setBody(command);
            return Unit.INSTANCE;
        }));
    }

}
