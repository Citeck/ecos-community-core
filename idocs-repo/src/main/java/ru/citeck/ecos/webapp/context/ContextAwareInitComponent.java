package ru.citeck.ecos.webapp.context;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.webapp.api.EcosWebAppApi;
import ru.citeck.ecos.webapp.lib.context.EcosWebAppContextAware;

import javax.annotation.PostConstruct;
import java.util.List;

@Component
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ContextAwareInitComponent {

    private final EcosWebAppApi context;
    private final List<EcosWebAppContextAware> webAppContextAware;

    @PostConstruct
    public void init() {
        webAppContextAware.forEach(it -> it.setEcosWebAppContext(context));
    }
}
