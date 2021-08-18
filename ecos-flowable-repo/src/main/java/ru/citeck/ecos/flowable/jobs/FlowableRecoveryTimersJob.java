package ru.citeck.ecos.flowable.jobs;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import ru.citeck.ecos.flowable.services.timer.FlowableTimersRestorerService;
import ru.citeck.ecos.job.AbstractLockedJob;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

@Slf4j
public class FlowableRecoveryTimersJob extends AbstractLockedJob {

    @Override
    public void executeJob(JobExecutionContext context) {
        final JobDataMap data = context.getJobDetail().getJobDataMap();

        String jobName = this.getJobName(context);
        log.info("Job with name `" + jobName + "` start");

        AuthenticationUtil.runAsSystem(() -> {
            FlowableTimersRestorerService flowableTimersRestorerService = (FlowableTimersRestorerService) data.get("flowableTimersRestorerService");

            int maxCountJobForProcess = data.getInt("maxCountJobForProcess");
            int retriesCount = data.getInt("retriesCount");
            int batchSize = data.getInt("batchSize");
            @SuppressWarnings("unchecked")
            Map<String, Set<String>> processActivitiesToChangeStatus = (Map<String, Set<String>>) data.get("processActivitiesToChangeStatus");
            @SuppressWarnings("unchecked")
            Collection<String> exceptionMsgPatterns = (Collection<String>) data.get("exceptionMsgPatterns");

            FlowableTimersRestorerService.Config config;
            if (exceptionMsgPatterns == null || exceptionMsgPatterns.isEmpty()) {
                config = new FlowableTimersRestorerService.Config(
                    maxCountJobForProcess,
                    retriesCount,
                    batchSize,
                    processActivitiesToChangeStatus
                );
            } else {
                config = new FlowableTimersRestorerService.Config(
                    maxCountJobForProcess,
                    retriesCount,
                    batchSize,
                    processActivitiesToChangeStatus,
                    new ArrayList<>(exceptionMsgPatterns)
                );
            }

            flowableTimersRestorerService.restore(config);

            return null;
        });

        log.info("Job with name `" + jobName + "` end");
    }
}
