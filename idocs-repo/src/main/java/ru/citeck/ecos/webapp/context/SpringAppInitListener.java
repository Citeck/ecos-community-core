package ru.citeck.ecos.webapp.context;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.extensions.surf.util.AbstractLifecycleBean;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.domain.auth.EcosReqContextRequestFilterListener;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class SpringAppInitListener extends AbstractLifecycleBean implements EcosReqContextRequestFilterListener {

    private final EcosWebAppApiImpl context;

    private final AtomicBoolean ctxWasRefreshed = new AtomicBoolean();
    private final AtomicBoolean filterInitialized = new AtomicBoolean();

    @Override
    protected synchronized void onBootstrap(ApplicationEvent applicationEvent) {
        ctxWasRefreshed.set(true);
    }

    @Override
    public synchronized void onFilterInitialized() {
        if (filterInitialized.compareAndSet(false, true) && ctxWasRefreshed.get()) {
            context.applicationContextWasLoaded();
        }
    }

    @Override
    protected void onShutdown(ApplicationEvent applicationEvent) {
    }
}
