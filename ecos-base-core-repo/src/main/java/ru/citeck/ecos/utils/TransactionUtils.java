package ru.citeck.ecos.utils;

import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.AlfrescoTransactionSupport;
import org.alfresco.repo.transaction.RetryingTransactionHelper;
import org.alfresco.repo.transaction.TransactionalResourceHelper;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.transaction.TransactionService;
import org.alfresco.util.transaction.TransactionListenerAdapter;
import org.apache.log4j.Logger;
import org.springframework.extensions.surf.util.I18NUtil;
import ru.citeck.ecos.utils.performance.ActionPerformance;
import ru.citeck.ecos.utils.performance.Performance;

import java.util.*;
import java.util.function.Consumer;

public class TransactionUtils {

    private static final Logger LOG = Logger.getLogger(TransactionUtils.class);

    private static final String AFTER_COMMIT_JOBS_KEY = TransactionUtils.class + ".after-commit-jobs";

    private static TransactionService transactionService;

    public static void doBeforeCommit(final Runnable runnable) {
        doBeforeCommit(null, runnable);
    }

    public static void doBeforeCommit(String actionKey, final Runnable runnable) {
        AlfrescoTransactionSupport.bindListener(new TransactionListenerAdapter() {
            @Override
            public void beforeCommit(boolean readOnly) {
                Performance perf = new ActionPerformance(runnable, actionKey);
                runnable.run();
                perf.stop();
            }
        });
    }

    public static void doAfterBehaviours(String actionKey, final Runnable runnable) {
        doBeforeCommit("doAfterBehaviours", () -> doBeforeCommit(actionKey, runnable));
    }

    public static void doAfterBehaviours(final Runnable runnable) {
        doBeforeCommit(() -> doBeforeCommit(runnable));
    }

    public static void doAfterCommit(final Runnable job) {
        doAfterCommit(job, (Consumer<Exception>) null);
    }

    public static void doAfterCommit(final Runnable job, final Runnable errorHandler) {
        doAfterCommit(job, e -> errorHandler.run());
    }

    public static void doAfterCommit(final Runnable job, final Consumer<Exception> errorHandler) {

        final String currentUser = AuthenticationUtil.getFullyAuthenticatedUser();
        final Locale locale = I18NUtil.getLocale();

        List<Job> jobs = AlfrescoTransactionSupport.getResource(AFTER_COMMIT_JOBS_KEY);

        if (jobs == null) {

            jobs = new ArrayList<>();
            AlfrescoTransactionSupport.bindResource(AFTER_COMMIT_JOBS_KEY, jobs);

            final List<Job> finalJobs = jobs;
            AlfrescoTransactionSupport.bindListener(new TransactionListenerAdapter() {
                @Override
                public void afterCommit() {
                    prepareAfterCommitJobsThread(finalJobs, currentUser, locale).start();
                }
            });
        }
        jobs.add(new Job(job, errorHandler));
    }

    public static <T> void processBatchAfterCommit(String transactionKey,
                                                   T element,
                                                   Consumer<Set<T>> consumer,
                                                   Consumer<Exception> errorHandler) {

        final Set<T> elements = TransactionalResourceHelper.getSet(transactionKey);
        if (elements.isEmpty()) {
            TransactionUtils.doAfterCommit(() -> {
                AuthenticationUtil.runAsSystem(() -> {
                    consumer.accept(elements);
                    return null;
                });
                elements.clear();
            }, errorHandler);
        }
        elements.add(element);
    }

    public static <T> void processBeforeCommit(String transactionKey, T element, Consumer<T> consumer) {
        final Set<T> elements = TransactionalResourceHelper.getSet(transactionKey);
        if (elements.isEmpty()) {
            TransactionUtils.doBeforeCommit(transactionKey, () -> {
                AuthenticationUtil.runAsSystem(() -> {
                    processElements(elements, consumer);
                    return null;
                });
            });
        }
        elements.add(element);
    }

    public static <T> void processAfterBehaviours(String transactionKey, T element, Consumer<T> consumer) {
        final Set<T> elements = TransactionalResourceHelper.getSet(transactionKey);
        if (elements.isEmpty()) {
            TransactionUtils.doAfterBehaviours(transactionKey, () -> {
                AuthenticationUtil.runAsSystem(() -> {
                    processElements(elements, consumer);
                    return null;
                });
            });
        }
        elements.add(element);
    }

    private static <T> void processElements(Set<T> elements, Consumer<T> consumer) {
        Set<T> processedElements = new HashSet<>();

        while (!elements.isEmpty() && !processedElements.containsAll(elements)) {
            Set<T> copyElements = new HashSet<>(elements);
            elements.clear();

            for (T copyElement : copyElements) {
                if (processedElements.add(copyElement)) {
                    consumer.accept(copyElement);
                }
            }
        }
    }

    private static Thread prepareAfterCommitJobsThread(List<Job> jobs, final String currentUser, final Locale locale) {

        return new Thread(() -> {

            AuthenticationUtil.setRunAsUser(currentUser);
            I18NUtil.setLocale(locale);

            AuthenticationUtil.runAsSystem(() -> {

                for (int i = 0; i < jobs.size(); i++) {
                    Job job = jobs.get(i);
                    try {
                        List<Job> newJobs = new ArrayList<>();
                        doInTransaction(() -> {
                            newJobs.clear();
                            AlfrescoTransactionSupport.bindResource(AFTER_COMMIT_JOBS_KEY, newJobs);
                            job.runnable.run();
                        });
                        jobs.addAll(newJobs);
                    } catch (Exception e) {
                        LOG.error("Exception while job running", e);
                        if (job.errorHandler != null) {
                            doInTransaction(() -> job.errorHandler.accept(e));
                        }
                    }
                }
                return null;
            });
        });
    }

    private static void doInTransaction(final Runnable job) {
        transactionService.getRetryingTransactionHelper().doInTransaction(
                (RetryingTransactionHelper.RetryingTransactionCallback<String>) () -> {
                    job.run();
                    return null;
                }, false, true);
    }

    public static void setServiceRegistry(ServiceRegistry serviceRegistry) {
        transactionService = serviceRegistry.getTransactionService();
    }

    private static class Job {

        Runnable runnable;
        Consumer<Exception> errorHandler;

        Job(Runnable runnable, Consumer<Exception> errorHandler) {
            this.runnable = runnable;
            this.errorHandler = errorHandler;
        }
    }
}
