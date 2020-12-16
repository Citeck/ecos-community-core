package ru.citeck.ecos.notification.task.record.services;

import org.springframework.stereotype.Service;
import ru.citeck.ecos.notification.task.record.TaskExecutionRecord;

@Service
public interface EcosExecutionTaskService {

    TaskExecutionRecord getExecutionTaskRecord(Object task);

}
