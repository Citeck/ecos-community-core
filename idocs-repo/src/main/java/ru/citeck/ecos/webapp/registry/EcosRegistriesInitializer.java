package ru.citeck.ecos.webapp.registry;

import kotlin.Unit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.promise.Promises;
import ru.citeck.ecos.webapp.api.EcosWebAppApi;
import ru.citeck.ecos.webapp.api.promise.Promise;
import ru.citeck.ecos.webapp.lib.registry.EcosRegistry;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class EcosRegistriesInitializer {

    private final EcosWebAppApi webAppCtx;
    private final List<EcosRegistry<?>> registries;

    @PostConstruct
    public void init() {
        webAppCtx.doWhenAppReady(0f, () -> {

            log.info("Begin registries initialization");

            List<Promise<?>> promises = registries.stream()
                .map(EcosRegistry::initializationPromise)
                .collect(Collectors.toList());

            Promises.all(promises).get();
            return Unit.INSTANCE;
        });
    }
}
