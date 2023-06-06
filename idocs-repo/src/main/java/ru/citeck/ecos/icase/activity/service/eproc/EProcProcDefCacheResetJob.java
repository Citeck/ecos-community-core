package ru.citeck.ecos.icase.activity.service.eproc;

import kotlin.Unit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commands.CommandsService;
import ru.citeck.ecos.commands.dto.CommandResult;
import ru.citeck.ecos.commons.task.schedule.Schedules;
import ru.citeck.ecos.icase.activity.service.eproc.commands.dto.request.GetProcDefCache;
import ru.citeck.ecos.icase.activity.service.eproc.commands.dto.response.GetProcDefCacheResp;
import ru.citeck.ecos.webapp.api.task.scheduler.EcosTaskSchedulerApi;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class EProcProcDefCacheResetJob {

    private final EcosTaskSchedulerApi ecosTaskScheduler;
    private final EProcActivityServiceImpl eProcActivityService;
    private final CommandsService commandsService;

    private final AtomicReference<String> cacheKey = new AtomicReference<>();

    @PostConstruct
    public void init() {
        ecosTaskScheduler.schedule(
            "eproc-cache-reset",
            Schedules.fixedDelay(Duration.ofSeconds(5)),
            this::updateCacheIfRequired
        );
    }

    private Unit updateCacheIfRequired() {
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
            return Unit.INSTANCE;
        }
        if (resp != null && resp.getCacheKey() != null && !Objects.equals(resp.getCacheKey(), cacheKey.get())) {
            log.info("Cache key was changed from " + cacheKey + " to " + resp.getCacheKey());
            eProcActivityService.resetCache();
            cacheKey.set(resp.getCacheKey());
        }
        return Unit.INSTANCE;
    }
}
