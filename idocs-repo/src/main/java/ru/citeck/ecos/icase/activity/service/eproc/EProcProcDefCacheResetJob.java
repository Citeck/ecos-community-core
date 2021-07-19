package ru.citeck.ecos.icase.activity.service.eproc;

import kotlin.Unit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commands.CommandsService;
import ru.citeck.ecos.commands.dto.CommandResult;
import ru.citeck.ecos.icase.activity.service.eproc.commands.dto.request.GetProcDefCache;
import ru.citeck.ecos.icase.activity.service.eproc.commands.dto.response.GetProcDefCacheResp;
import ru.citeck.ecos.rabbitmq.RabbitMqConn;
import ru.citeck.ecos.rabbitmq.RabbitMqConnProvider;

import javax.annotation.PostConstruct;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class EProcProcDefCacheResetJob {

    private final ScheduledExecutorService scheduledExecutorService;
    private final EProcActivityServiceImpl eProcActivityService;
    private final CommandsService commandsService;
    private final RabbitMqConnProvider rabbitMqConnProvider;

    private String cacheKey;

    @Autowired
    public EProcProcDefCacheResetJob(
            @Qualifier("ecosScheduledExecutor")
            ScheduledExecutorService scheduledExecutorService,
            EProcActivityServiceImpl eProcActivityService,
            CommandsService commandsService,
            RabbitMqConnProvider rabbitMqConnProvider) {

        this.rabbitMqConnProvider = rabbitMqConnProvider;
        this.commandsService = commandsService;
        this.eProcActivityService = eProcActivityService;
        this.scheduledExecutorService = scheduledExecutorService;
    }

    @PostConstruct
    public void init() {

        RabbitMqConn rabbitMqConn = rabbitMqConnProvider.getConnection();
        if (rabbitMqConn == null) {
            log.warn("RabbitMQ connection is null. Jow won't be started");
            return;
        }

        int initDelaySec = 20;
        log.info("Initialization will be started in " + initDelaySec + " seconds");

        scheduledExecutorService.schedule(() -> {

            log.info("Wait until RabbitMQ will be ready");
            rabbitMqConn.waitUntilReady(TimeUnit.MINUTES.toMillis(30));

            scheduledExecutorService.scheduleWithFixedDelay(
                this::updateCacheIfRequired,
                5,
                5,
                TimeUnit.SECONDS
            );

            log.info("Initialization completed");

        }, initDelaySec, TimeUnit.SECONDS);
    }

    private void updateCacheIfRequired() {
        GetProcDefCacheResp resp;
        try {
            CommandResult res = commandsService.executeSync(commandsService.buildCommand(b -> {
                b.setTargetApp("eproc");
                b.setBody(new GetProcDefCache());
                return Unit.INSTANCE;
            }));
            resp = res.getResultAs(GetProcDefCacheResp.class);
        } catch (Exception e) {
            log.trace("GetProcDefCache command failed", e);
            return;
        }
        if (resp != null && resp.getCacheKey() != null && !Objects.equals(resp.getCacheKey(), cacheKey)) {
            log.info("Cache key was changed from " + cacheKey + " to " + resp.getCacheKey());
            eProcActivityService.resetCache();
            cacheKey = resp.getCacheKey();
        }
    }
}
