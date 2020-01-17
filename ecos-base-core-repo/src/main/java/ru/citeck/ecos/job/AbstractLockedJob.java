package ru.citeck.ecos.job;

import org.alfresco.repo.lock.JobLockService;
import org.alfresco.repo.lock.LockAcquisitionException;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.VmShutdownListener;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.StatefulJob;
import org.springframework.scheduling.quartz.QuartzJobBean;
import ru.citeck.ecos.model.EcosModel;

import java.util.concurrent.TimeUnit;

public abstract class AbstractLockedJob extends QuartzJobBean implements StatefulJob {

    private static final long DEFAULT_LOCK_TTL = TimeUnit.HOURS.toMillis(2);

    private static final String LOG_MSG = "[%s]{%s} %s";

    private static final String PARAM_JOB_LOCK_SERVICE = "jobLockService";
    private static final String PARAM_LOCK_TTL = "lockTTL";
    private static final String PARAM_NAME = "name";

    private static final Log logger = LogFactory.getLog(AbstractLockedJob.class);

    private JobLockService jobLockService;

    private QName lockQName;
    private long lockTTL;

    private boolean initialized = false;

    @Override
    protected final synchronized void executeInternal(final JobExecutionContext jobContext)
            throws JobExecutionException {

        if (!initialized) {

            JobDataMap jobDataMap = jobContext.getJobDetail().getJobDataMap();

            jobLockService = (JobLockService) jobDataMap.get(PARAM_JOB_LOCK_SERVICE);

            if (jobLockService == null) {
                throw new RuntimeException("Missing setting: " + PARAM_JOB_LOCK_SERVICE);
            }
            String jobName = getJobName(jobContext);
            lockQName = QName.createQName(EcosModel.ECOS_NAMESPACE, jobName);
            lockTTL = getLockTTL(jobContext);

            initialized = true;
        }

        executeJobWithLock(jobContext);
    }

    /**
     * It will execute job taking care of all cluster aware lockings.
     *
     * @param jobContext the usual quartz job context
     * @throws JobExecutionException thrown if the job fails to execute
     */
    private void executeJobWithLock(JobExecutionContext jobContext) throws JobExecutionException {
        String lockToken = null;
        try {
            debugMsg("Job started");

            lockToken = jobLockService.getLock(lockQName, lockTTL);
            executeJob(jobContext);

            debugMsg("Job completed");
        } catch (LockAcquisitionException e) {
            // Job being done by another process
            debugMsg("Job already underway");
        } catch (VmShutdownListener.VmShutdownException e) {
            debugMsg("Job aborted");
        } finally {
            if (lockToken != null) {
                jobLockService.releaseLock(lockToken, lockQName);
            }
        }
    }

    private void debugMsg(String msg) {
        if (logger.isDebugEnabled()) {
            logger.debug(String.format(LOG_MSG, getClass(), lockQName.getLocalName(), msg));
        }
    }

    /**
     * This is the method that should be implemented by any extension of the
     * abstract class. It won't need to worry about any lockings of the job and
     * can focus only on its specific task.
     *
     * @param jobContext context of the execution for retrieving services, etc
     * @throws JobExecutionException if a job fails to execute
     */
    public abstract void executeJob(JobExecutionContext jobContext) throws JobExecutionException;

    protected String getJobName(JobExecutionContext jobContext) {
        String name = (String) jobContext.getJobDetail().getJobDataMap().get(PARAM_NAME);
        return name == null ? this.getClass().getSimpleName() : name;
    }

    protected long getLockTTL(JobExecutionContext jobContext) {
        String paramTTL = (String) jobContext.getJobDetail().getJobDataMap().get(PARAM_LOCK_TTL);
        return StringUtils.isNotBlank(paramTTL) ? Long.parseLong(paramTTL) : DEFAULT_LOCK_TTL;
    }
}
