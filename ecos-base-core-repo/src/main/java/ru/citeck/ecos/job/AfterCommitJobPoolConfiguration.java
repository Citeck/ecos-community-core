package ru.citeck.ecos.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.annotation.PreDestroy;

@Slf4j
@Configuration
public class AfterCommitJobPoolConfiguration {

    @Value("${after-commit-job.thread-pool.core-pool-size}")
    private Integer corePoolSize;

    @Value("${after-commit-job.thread-pool.max-pool-size}")
    private Integer maxPoolSize;

    @Value("${after-commit-job.thread-pool.await-termination-seconds}")
    private Integer awaitTerminationSeconds;

    @Value("${after-commit-job.thread-pool.queue-capacity}")
    private Integer queueCapacity;

    private ThreadPoolTaskExecutor executor;

    @Bean
    public TaskExecutor afterCommitTaskExecutor() {

        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);

        if (queueCapacity > 0) {
            executor.setQueueCapacity(queueCapacity);
        }

        executor.setThreadNamePrefix("after-commit-task-Executor-");
        executor.setAwaitTerminationSeconds(awaitTerminationSeconds);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();

        String msg = "Create after commit task thread pool with parameters:\n" +
            "corePoolSize: " + corePoolSize + "\n" +
            "maxPoolSize: " + maxPoolSize + "\n" +
            "queueCapacity: " + queueCapacity + "\n" +
            "awaitTerminationSeconds: " + awaitTerminationSeconds;
        log.info(msg);

        return executor;
    }

    @PreDestroy
    public void destroy() {
        log.info("Destroy...");
        executor.shutdown();
    }
}
