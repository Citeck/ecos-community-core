package ru.citeck.ecos.notification.task.record.services;

import org.springframework.stereotype.Service;
import ru.citeck.ecos.notification.task.record.TaskExecutionRecord;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EcosExecutionsTaskService {
    private final Map<Class<?>, EcosExecutionTaskService> taskExecutionServices = new ConcurrentHashMap<>();

    public Optional<TaskExecutionRecord> getExecutionRecord(Object task) {
        return getExecutionRecord(task.getClass(), task);
    }

    public Optional<TaskExecutionRecord> getExecutionRecord(Class<?> taskClass, Object task) {
        EcosExecutionTaskService taskService = needTaskService(taskClass);
        TaskExecutionRecord executionTaskRecord = taskService.getExecutionTaskRecord(task);
        return Optional.ofNullable(executionTaskRecord);
    }

    private EcosExecutionTaskService needTaskService(Class<?> taskClass) {
        EcosExecutionTaskService taskService = taskExecutionServices.get(taskClass);
        if (taskService == null) {
            throw new IllegalArgumentException("Task execution service for task class '"
                + taskClass + "' is not registered");
        }
        return taskService;
    }

    public void register(Class<?> taskClass, EcosExecutionTaskService taskExecutionService) {
        taskExecutionServices.put(taskClass, taskExecutionService);
    }
}
