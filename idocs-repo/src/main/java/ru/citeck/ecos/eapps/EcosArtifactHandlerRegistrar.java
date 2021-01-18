package ru.citeck.ecos.eapps;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.domain.handler.ArtifactHandlerService;
import ru.citeck.ecos.apps.app.domain.handler.EcosArtifactHandler;

import javax.annotation.PostConstruct;
import java.util.List;

@Slf4j
@Component
public class EcosArtifactHandlerRegistrar {

    private List<EcosArtifactHandler<?>> handlers = null;

    @Autowired
    private ArtifactHandlerService handlerService;

    @PostConstruct
    public void registerAll() {
        if (handlers != null) {
            handlers.forEach(this::register);
        }
    }

    private void register(EcosArtifactHandler<?> handler) {

        log.info("Found and registered '" + handler.getArtifactType() + "' handler "
            + "with name: " + handler.getClass().getSimpleName());

        handlerService.register(handler);
    }

    @Autowired(required = false)
    public void setHandlers(List<EcosArtifactHandler<?>> handlers) {
        this.handlers = handlers;
    }
}
