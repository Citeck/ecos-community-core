package ru.citeck.ecos.flowable.services.timer;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.repo.node.NodeUtils;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.RetryingTransactionHelper;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.transaction.TransactionService;
import org.codehaus.plexus.util.StringUtils;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RuntimeService;
import org.flowable.job.api.DeadLetterJobQuery;
import org.flowable.job.api.Job;
import org.flowable.job.api.JobNotFoundException;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.icase.CaseStatusService;
import ru.citeck.ecos.utils.JavaScriptImplUtils;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FlowableTimersRestorerService {

    @Autowired
    private RuntimeService flowableRuntimeService;
    @Autowired
    private ManagementService flowableManagementService;
    @Autowired
    private NodeService nodeService;
    @Autowired
    private CaseStatusService caseStatusService;
    @Autowired
    private TransactionService transactionService;

    private RetryingTransactionHelper transactionHelper;

    public static final String ECOS_FLOWABLE_TIMER_ERROR = "ecos-flowable-timer-error";

    @PostConstruct
    private void init() {
        transactionHelper = transactionService.getRetryingTransactionHelper();
    }

    public void restore() {
        restore(new Config(100, 3, 10000, null));
    }

    public void restore(Config config) {
        new Restorer(config).restore();
    }

    public void restoreByProcessInstanceIds(Config config, Set<String> processInstanceIds) {
        new Restorer(config).restoreByProcessInstanceIds(processInstanceIds);
    }

    public void restoreByProcessInstanceId(Config config, String processInstanceId) {
        new Restorer(config).restoreByProcessInstanceId(processInstanceId);
    }

    private class Restorer {

        private final Config config;

        Restorer(Config config) {
            this.config = config;
        }

        public void restore() {
            log.info("Start FlowableTimersRestorer");
            try {
                AuthenticationUtil.runAsSystem(() -> {
                    restoreImpl();
                    return null;
                });
            } catch (Exception e) {
                log.error("FlowableTimersRestorer is failed", e);
            } finally {
                log.info("END FlowableTimersRestorer");
            }
        }

        private void restoreImpl() {
            final List<Job> result = flowableManagementService.createDeadLetterJobQuery()
                .orderByJobDuedate().desc().listPage(0, 1);
            if (result.isEmpty()) {
                return;
            }

            final Job lastJob = result.get(0);
            // Plus one second, because can not set condition dueDateLowerThanOrEquals
            final Date dueDateLast = getDueDatePlusOneSecond(lastJob);
            Date dueDateLower = null;

            List<Job> jobs;
            while (!(jobs = getJobs(dueDateLower, dueDateLast)).isEmpty()) {
                Set<String> processInstanceIds = jobs.stream()
                    .map(Job::getProcessInstanceId)
                    .collect(Collectors.toSet());

                int jobSize = jobs.size();
                if (jobSize != 0) {
                    Date lastDueDate = jobs.get(jobSize - 1).getDuedate();
                    if (lastDueDate == null) {
                        log.error("Next DueDate is null, restore is stop");
                        break;
                    }
                    if (jobSize == config.getBatchSize() && lastDueDate.equals(dueDateLower)) {
                        log.warn("DueDate was not change in batch, jobs may be skipped for " + lastDueDate + " date");
                    }
                    dueDateLower = lastDueDate;
                }

                restoreByProcessInstanceIds(processInstanceIds);
            }
        }

        @NotNull
        private Date getDueDatePlusOneSecond(Job lastJob) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(lastJob.getDuedate());
            calendar.add(Calendar.SECOND, 1);
            return calendar.getTime();
        }

        private List<Job> getJobs(Date from, Date to) {
            DeadLetterJobQuery query = flowableManagementService.createDeadLetterJobQuery()
                .orderByJobDuedate().asc()
                .duedateLowerThan(to);
            if (from != null) {
                query.duedateHigherThan(from);
            }
            return query.listPage(0, config.getBatchSize());
        }

        public void restoreByProcessInstanceIds(Set<String> processInstanceIds) {
            for (String processInstanceId : processInstanceIds) {
                restoreByProcessInstanceId(processInstanceId);
            }
        }

        public void restoreByProcessInstanceId(String processInstanceId) {
            try {
                restoreByProcessInstanceIdImpl(processInstanceId);
            } catch (Exception e) {
                log.error("Failed to restore processInstanceId " + processInstanceId, e);
            }
        }

        private void restoreByProcessInstanceIdImpl(String processInstanceId) {
            long jobCount = flowableManagementService.createDeadLetterJobQuery()
                .processInstanceId(processInstanceId)
                .count();
            if (jobCount == 0) {
                return;
            }
            if (config.maxCountJobForProcess > 0 && jobCount > config.maxCountJobForProcess) {
                log.error("The count of jobs for the "
                    + processInstanceId + " processInstanceId is more than the limit " + config.maxCountJobForProcess
                    + ". Jobs restore skipped"
                );
                return;
            }

            List<Job> jobs = flowableManagementService.createDeadLetterJobQuery()
                .processInstanceId(processInstanceId)
                .list().stream()
                .filter((job) -> {
                    if (job == null) {
                        return false;
                    }

                    String jobType = job.getJobType();
                    return Job.JOB_TYPE_TIMER.equals(jobType);
                })
                .collect(Collectors.toList());

            restoreTimers(processInstanceId, jobs);
        }

        private void restoreTimers(String processInstanceId, List<Job> jobs) {
            boolean isNeedChangeStatus = false;
            for (Job job : jobs) {
                String exceptionMessage = job.getExceptionMessage();
                if (isConcurrentlyException(exceptionMessage)) {
                    restoreTimer(job);
                } else {
                    log.info("Timer was not restore, because error is not concurrently."
                        + ". ProcessInstanceId: " + processInstanceId
                        + ", JobId: " + job.getId()
                        + ". Error: " + exceptionMessage);
                    isNeedChangeStatus = isNeedChangeStatus || checkNeedChangeStatus(job);
                }
            }

            if (isNeedChangeStatus) {
                changeStatusForProcess(processInstanceId);
            }
        }

        private boolean isConcurrentlyException(String exceptionMessage) {
            return exceptionMessage.endsWith("was updated by another transaction concurrently");
        }

        private void restoreTimer(Job job) {
            try {
                flowableManagementService.moveDeadLetterJobToExecutableJob(job.getId(), config.retriesCount);
                log.info("Timer is restore. "
                    + ". ProcessInstanceId: " + job.getProcessInstanceId()
                    + ", JobId: " + job.getId());
            } catch (JobNotFoundException e) {
                log.info("Job is not restore. Job " + job.getId() + " not found.", e);
            } catch (Exception e) {
                log.error("Restore is failed by unknown exception. JobId " + job.getId() + ".", e);
            }
        }

        private boolean checkNeedChangeStatus(Job job) {
            String processDefinitionId = job.getProcessDefinitionId();
            if (StringUtils.isBlank(processDefinitionId)) {
                return false;
            }
            String process = processDefinitionId.split(":")[0];
            String activity = Json.getMapper().toJson(job.getJobHandlerConfiguration()).get("activityId").asText();

            return config.processActivitiesToChangeStatus
                .getOrDefault(process, Collections.emptySet())
                .contains(activity);
        }

        private void changeStatusForProcess(String processInstanceId) {
            Map<String, Object> variables = flowableRuntimeService.getVariables(processInstanceId);

            Object docObject = variables.get("document");
            NodeRef nodeRef = JavaScriptImplUtils.getNodeRef(docObject);
            if (!NodeUtils.exists(nodeRef, nodeService)) {
                log.error("Status can not change. NodeRef " + nodeRef + " does not exist");
                return;
            }

            try {
                transactionHelper.doInTransaction(() -> {
                    String status = caseStatusService.getStatus(nodeRef);
                    if (!ECOS_FLOWABLE_TIMER_ERROR.equals(status)) {
                        caseStatusService.setStatus(nodeRef, ECOS_FLOWABLE_TIMER_ERROR);
                        log.info("Node " + nodeRef + " status changed to ecos-process-timer-error");
                    }
                    return null;
                });
            } catch (Exception e) {
                log.error("Node " + nodeRef + " status change failed", e);
            }
        }
    }

    @Data
    public static class Config {
        private final int maxCountJobForProcess;
        private final int retriesCount;
        private final int batchSize;
        private final Map<String, Set<String>> processActivitiesToChangeStatus;

        /**
         * @param processActivitiesToChangeStatus - Map where key is flowableProcessId, value is set of timers ids.
         * If the timer falls, then the document will go into error status "ecos-flowable-timer-error"
         */
        public Config(
            int maxCountJobForProcess,
            int retriesCount,
            int batchSize,
            Map<String, Set<String>> processActivitiesToChangeStatus
        ) {
            if (maxCountJobForProcess <= 0) {
                throw new IllegalArgumentException("maxCountJobForProcess must be more then 0");
            }
            if (retriesCount <= 1) {
                throw new IllegalArgumentException("maxCountJobForProcess must be more then 0");
            }
            if (batchSize <= 0) {
                throw new IllegalArgumentException("maxCountJobForProcess must be more then 0");
            }

            this.maxCountJobForProcess = maxCountJobForProcess;
            this.retriesCount = retriesCount;
            this.batchSize = batchSize;
            this.processActivitiesToChangeStatus = processActivitiesToChangeStatus == null
                ? Collections.emptyMap() : processActivitiesToChangeStatus;
        }
    }
}
