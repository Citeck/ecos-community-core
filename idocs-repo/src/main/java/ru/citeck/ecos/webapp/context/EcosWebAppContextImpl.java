package ru.citeck.ecos.webapp.context;

import kotlin.jvm.functions.Function0;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.audit.lib.EcosAuditService;
import ru.citeck.ecos.commons.promise.Promises;
import ru.citeck.ecos.webapp.api.apps.EcosWebAppsApi;
import ru.citeck.ecos.webapp.api.authority.EcosAuthorityService;
import ru.citeck.ecos.webapp.api.context.EcosWebAppContext;
import ru.citeck.ecos.webapp.api.lock.EcosLockService;
import ru.citeck.ecos.webapp.api.promise.Promise;
import ru.citeck.ecos.webapp.api.properties.EcosWebAppProperties;
import ru.citeck.ecos.webapp.api.task.EcosTasksApi;
import ru.citeck.ecos.webapp.api.web.EcosWebClient;
import ru.citeck.ecos.webapp.api.web.EcosWebController;
import ru.citeck.ecos.webapp.lib.lock.EcosAppLockService;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class EcosWebAppContextImpl implements EcosWebAppContext {

    private final EcosAuthorityService ecosAuthorityService;
    private final EcosWebAppProperties properties;
    private final EcosWebAppsApi ecosWebAppsApi;
    private final EcosAuditService ecosAuditService;
    private final EcosAppLockService ecosLockService;
    private final EcosWebClient webClient;
    private final EcosTasksApi ecosTasksApi;

    private final AtomicBoolean isReadyFlag = new AtomicBoolean(false);
    private final ScheduledExecutorService systemScheduler = Executors.newScheduledThreadPool(1);
    private final Set<OnReadyAction> onReadyActions = Collections.synchronizedSet(new TreeSet<>());

    @NotNull
    @Override
    public <T> Promise<T> doWhenAppReady(float order, @NotNull Function0<? extends T> action) {

        CompletableFuture<T> future = new CompletableFuture<>();

        Runnable onReadyActionTask = () -> {
            try {
                future.complete(action.invoke());
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        };
        if (!isReadyFlag.get()) {
            onReadyActions.add(new OnReadyAction(order, onReadyActionTask));
        } else {
            systemScheduler.submit(onReadyActionTask);
        }
        return Promises.create(future);
    }

    @NotNull
    @Override
    public EcosLockService getAppLockService() {
        return ecosLockService;
    }

    @NotNull
    @Override
    public EcosAuditService getAuditApi() {
        return ecosAuditService;
    }

    @NotNull
    @Override
    public EcosAuthorityService getAuthorityService() {
        return ecosAuthorityService;
    }

    @NotNull
    @Override
    public EcosWebAppProperties getProperties() {
        return properties;
    }

    @NotNull
    @Override
    public EcosTasksApi getTasksApi() {
        return ecosTasksApi;
    }

    @NotNull
    @Override
    public EcosWebAppsApi getWebAppsApi() {
        return ecosWebAppsApi;
    }

    @NotNull
    @Override
    public EcosWebClient getWebClient() {
        return webClient;
    }

    @NotNull
    @Override
    public EcosWebController getWebController() {
        return (s, ecosWebExecutor) -> {
            //do nothing
        };
    }

    @Override
    public boolean isReady() {
        return isReadyFlag.get();
    }

    public void applicationContextWasLoaded() {
        if (!isReadyFlag.compareAndSet(false, true)) {
            return;
        }
        log.info("Application context was loaded. OnReady actions to execute: " + onReadyActions.size());
        synchronized(this) {
            onReadyActions.forEach((action) -> action.action.run());
            onReadyActions.clear();
        }
    }

    @RequiredArgsConstructor
    private static class OnReadyAction implements Comparable<OnReadyAction> {

        private static final AtomicLong instanceCounter = new AtomicLong();

        final float order;
        final Runnable action;

        private final long idx = instanceCounter.getAndIncrement();

        @Override
        public int compareTo(@NotNull OnReadyAction other) {
            int res = Float.compare(order, other.order);
            if (res == 0) {
                return Long.compare(idx, other.idx);
            }
            return 0;
        }
    }
}
