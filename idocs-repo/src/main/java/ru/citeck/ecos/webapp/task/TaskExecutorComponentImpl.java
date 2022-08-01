package ru.citeck.ecos.webapp.task;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import ru.citeck.ecos.webapp.lib.task.executor.TaskExecutorComponent;

@RequiredArgsConstructor
public class TaskExecutorComponentImpl implements TaskExecutorComponent {

    private final ThreadPoolTaskExecutor executor;

    @Override
    public void execute(@NotNull Runnable runnable) {
        executor.execute(runnable);
    }

    @Override
    public void shutdown() {
        executor.shutdown();
    }
}
