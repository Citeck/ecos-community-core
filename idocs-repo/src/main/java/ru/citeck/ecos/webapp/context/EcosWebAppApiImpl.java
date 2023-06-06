package ru.citeck.ecos.webapp.context;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.audit.lib.EcosAuditService;
import ru.citeck.ecos.webapp.api.EcosWebAppApi;
import ru.citeck.ecos.webapp.api.apps.EcosRemoteWebAppsApi;
import ru.citeck.ecos.webapp.api.authority.EcosAuthoritiesApi;
import ru.citeck.ecos.webapp.api.content.EcosContentApi;
import ru.citeck.ecos.webapp.api.content.EcosContentData;
import ru.citeck.ecos.webapp.api.content.FileUploader;
import ru.citeck.ecos.webapp.api.entity.EntityRef;
import ru.citeck.ecos.webapp.api.lock.EcosLockApi;
import ru.citeck.ecos.webapp.api.properties.EcosWebAppProps;
import ru.citeck.ecos.webapp.api.task.EcosTasksApi;
import ru.citeck.ecos.webapp.api.web.client.EcosWebClientApi;
import ru.citeck.ecos.webapp.api.web.executor.EcosWebExecutorsApi;
import ru.citeck.ecos.webapp.lib.lock.EcosAppLockService;
import ru.citeck.ecos.webapp.lib.web.webapi.client.EcosWebClient;
import ru.citeck.ecos.webapp.lib.web.webapi.executor.EcosWebExecutorsService;

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
public class EcosWebAppApiImpl implements EcosWebAppApi {

    private final EcosAuthoritiesApi ecosAuthoritiesApi;
    private final EcosWebAppProps properties;
    private final EcosRemoteWebAppsApi ecosRemoteWebAppsApi;
    private final EcosAuditService ecosAuditService;
    private final EcosAppLockService ecosLockService;
    private final EcosWebClientApi webClient;
    private final EcosTasksApi ecosTasksApi;
    private final EcosWebExecutorsService ecosWebExecutorsService;

    private final AtomicBoolean isReadyFlag = new AtomicBoolean(false);
    private final ScheduledExecutorService systemScheduler = Executors.newScheduledThreadPool(1);
    private final Set<OnReadyAction> onReadyActions = Collections.synchronizedSet(new TreeSet<>());

    @Override
    public void doWhenAppReady(float order, @NotNull Function0<Unit> action) {

        CompletableFuture<Unit> future = new CompletableFuture<>();

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
    }

    @NotNull
    @Override
    public EcosLockApi getAppLockApi() {
        return ecosLockService;
    }

    @NotNull
    @Override
    public EcosAuditService getAuditApi() {
        return ecosAuditService;
    }

    @NotNull
    @Override
    public EcosAuthoritiesApi getAuthoritiesApi() {
        return ecosAuthoritiesApi;
    }

    @NotNull
    @Override
    public EcosWebAppProps getProperties() {
        return properties;
    }

    @NotNull
    @Override
    public EcosTasksApi getTasksApi() {
        return ecosTasksApi;
    }

    @NotNull
    @Override
    public EcosRemoteWebAppsApi getRemoteWebAppsApi() {
        return ecosRemoteWebAppsApi;
    }

    @NotNull
    @Override
    public EcosWebClientApi getWebClientApi() {
        return webClient;
    }

    @NotNull
    @Override
    public EcosWebExecutorsApi getWebExecutorsApi() {
        return ecosWebExecutorsService;
    }

    @Override
    public void doBeforeAppReady(float order, @NotNull Function0<Unit> action) {
        action.invoke();
    }

    @NotNull
    @Override
    public EcosContentApi getContentApi() {
        // TODO
        return new EcosContentApi() {
            @Nullable
            @Override
            public EcosContentData getContent(@NotNull EntityRef entityRef) {
                throw new RuntimeException("Not implemented");
            }
            @Nullable
            @Override
            public EcosContentData getContent(@NotNull EntityRef entityRef, @NotNull String s) {
                throw new RuntimeException("Not implemented");
            }
            @Nullable
            @Override
            public EcosContentData getContent(@NotNull EntityRef entityRef, @NotNull String s, int i) {
                throw new RuntimeException("Not implemented");
            }
            @NotNull
            @Override
            public String getDownloadUrl(@NotNull EntityRef entityRef) {
                throw new RuntimeException("Not implemented");
            }
            @NotNull
            @Override
            public String getDownloadUrl(@NotNull EntityRef entityRef, @NotNull String s) {
                throw new RuntimeException("Not implemented");
            }
            @NotNull
            @Override
            public String getDownloadUrl(@NotNull EntityRef entityRef, @NotNull String s, int i) {
                throw new RuntimeException("Not implemented");
            }
            @NotNull
            @Override
            public FileUploader uploadTempFile() {
                throw new RuntimeException("Not implemented");
            }
            @NotNull
            @Override
            public FileUploader uploadFile() {
                throw new RuntimeException("Not implemented");
            }
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
