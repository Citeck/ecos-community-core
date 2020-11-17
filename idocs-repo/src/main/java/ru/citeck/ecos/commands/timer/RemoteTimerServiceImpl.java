package ru.citeck.ecos.commands.timer;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.commands.CommandsProperties;
import ru.citeck.ecos.commands.CommandsService;
import ru.citeck.ecos.commands.dto.CommandResult;
import ru.citeck.ecos.commands.timer.dto.request.CancelTimerCommand;
import ru.citeck.ecos.commands.timer.dto.request.CreateTimerCommand;
import ru.citeck.ecos.commands.timer.dto.request.TimerCommandDto;
import ru.citeck.ecos.commands.timer.dto.result.CancelTimerCommandRes;
import ru.citeck.ecos.commands.timer.dto.result.CreateTimerCommandRes;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.utils.TransactionUtils;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class RemoteTimerServiceImpl implements RemoteTimerService {

    private static final String EPROC_TARGET_APP_NAME = "eproc";

    private final CommandsProperties commandsProperties;
    private final CommandsService commandsService;

    @Autowired
    public RemoteTimerServiceImpl(CommandsProperties commandsProperties, CommandsService commandsService) {
        this.commandsProperties = commandsProperties;
        this.commandsService = commandsService;
    }

    @Override
    public <T> String scheduleTimer(Instant triggerTime, String commandType, T callbackData) {
        String currentApp = commandsProperties.getAppName();
        return scheduleTimer(triggerTime, commandType, currentApp, callbackData);
    }

    @Override
    public <T> String scheduleTimer(Instant triggerTime, String commandType, String targetApp, T callbackData) {
        CreateTimerCommand command = composeCreateTimerCommand(triggerTime, commandType, targetApp, callbackData);
        CreateTimerCommandRes result = sendScheduleCommand(command);
        if (result == null || StringUtils.isBlank(result.getTimerId())) {
            throw new RuntimeException("Scheduling of timer command returns illegal data, command=" + command);
        }

        return result.getTimerId();
    }

    private <T> CreateTimerCommand composeCreateTimerCommand(Instant triggerTime, String commandType,
                                                             String targetApp, T callbackData) {

        CreateTimerCommand command = new CreateTimerCommand();
        command.setTriggerTime(triggerTime);
        TimerCommandDto commandDto = new TimerCommandDto();
        commandDto.setId(UUID.randomUUID().toString());
        commandDto.setTargetApp(targetApp);
        commandDto.setType(commandType);
        commandDto.setBody(ObjectData.create(callbackData));
        command.setCommand(commandDto);
        return command;
    }

    private CreateTimerCommandRes sendScheduleCommand(CreateTimerCommand command) {
        CommandResult commandResult = commandsService.executeSync(command, EPROC_TARGET_APP_NAME);
        commandResult.throwPrimaryErrorIfNotNull();
        if (CollectionUtils.isNotEmpty(commandResult.getErrors())) {
            throw new RuntimeException("Exception while scheduling of timer, command=" + command + ". " +
                    "For detailed information see logs");
        }
        return commandResult.getResultAs(CreateTimerCommandRes.class);
    }

    @Override
    public void cancelTimerAfterTransaction(String timerId) {
        TransactionUtils.doAfterCommit(() -> cancelTimer(timerId));
    }

    @Override
    public void cancelTimer(String timerId) {
        CancelTimerCommand command = composeCancelTimerCommand(timerId);
        CancelTimerCommandRes result = sendCancelCommand(command);
        if (result == null || !result.isWasCancelled()) {
            log.warn("Timer with id='" + timerId + "' was not cancelled");
        }
    }

    private CancelTimerCommand composeCancelTimerCommand(String timerId) {
        CancelTimerCommand command = new CancelTimerCommand();
        command.setTimerId(timerId);
        return command;
    }

    private CancelTimerCommandRes sendCancelCommand(CancelTimerCommand command) {
        CommandResult commandResult = commandsService.executeSync(command, EPROC_TARGET_APP_NAME);
        commandResult.throwPrimaryErrorIfNotNull();
        if (CollectionUtils.isNotEmpty(commandResult.getErrors())) {
            log.warn("Exception while cancelling of timer, command=" + command + ". " +
                    "For detailed information see logs");
        }
        return commandResult.getResultAs(CancelTimerCommandRes.class);
    }
}
