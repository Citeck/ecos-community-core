package ru.citeck.ecos.webapp.task;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.citeck.ecos.webapp.api.task.scheduler.trigger.TriggerContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import ru.citeck.ecos.webapp.api.task.scheduler.trigger.Trigger;
import ru.citeck.ecos.webapp.lib.task.scheduler.TaskSchedulerComponent;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.ScheduledFuture;

@RequiredArgsConstructor
public class TaskSchedulerComponentImpl implements TaskSchedulerComponent {

    private final ThreadPoolTaskScheduler scheduler;

    @Override
    public void execute(@NotNull Runnable runnable) {
        scheduler.execute(runnable);
    }

    @NotNull
    @Override
    public ScheduledFuture<?> schedule(@NotNull Runnable runnable, @NotNull Instant startTime) {
        return scheduler.schedule(runnable, Date.from(startTime));
    }

    @Nullable
    @Override
    public ScheduledFuture<?> schedule(@NotNull Runnable runnable, @NotNull Trigger trigger) {

        SpringTriggerContextAdapter context = new SpringTriggerContextAdapter();
        context.setContext(
            new org.springframework.scheduling.TriggerContext() {
                @Override
                public Date lastScheduledExecutionTime() {
                    return null;
                }
                @Override
                public Date lastActualExecutionTime() {
                    return null;
                }
                @Override
                public Date lastCompletionTime() {
                    return null;
                }
            }
        );

        return scheduler.schedule(
            runnable,
            triggerContext -> {
                context.setContext(triggerContext);
                Instant nextExecTime = trigger.getNextExecutionTime(context);
                if (nextExecTime == null) {
                    return null;
                }
                return Date.from(nextExecTime);
            }
        );
    }

    @NotNull
    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(
        @NotNull Runnable runnable,
        @NotNull Duration initialDelay,
        @NotNull Duration period
    ) {
        return scheduler.scheduleAtFixedRate(
            runnable,
            Date.from(Instant.now().plus(initialDelay)),
            period.toMillis()
        );
    }

    @NotNull
    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(@NotNull Runnable runnable, @NotNull Duration initialDelay, @NotNull Duration delay) {
        return scheduler.scheduleWithFixedDelay(
            runnable,
            Date.from(Instant.now().plus(initialDelay)),
            delay.toMillis()
        );
    }

    @Override
    public void shutdown() {
        scheduler.shutdown();
    }

    private static class SpringTriggerContextAdapter implements TriggerContext {

        private org.springframework.scheduling.TriggerContext context;

        public void setContext(org.springframework.scheduling.TriggerContext context) {
            this.context = context;
        }

        @Nullable
        @Override
        public Instant getLastActualExecutionTime() {
            return toInstantOrNull(context.lastActualExecutionTime());
        }

        @Nullable
        @Override
        public Instant getLastCompletionTime() {
            return toInstantOrNull(context.lastCompletionTime());
        }

        @Nullable
        @Override
        public Instant getLastScheduledExecutionTime() {
            return toInstantOrNull(context.lastScheduledExecutionTime());
        }

        @Nullable
        private Instant toInstantOrNull(Date date) {
            if (date == null) {
                return null;
            }
            return date.toInstant();
        }
    }
}
