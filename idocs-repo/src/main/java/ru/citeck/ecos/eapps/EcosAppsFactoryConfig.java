package ru.citeck.ecos.eapps;

import com.netflix.discovery.converters.Auto;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.apps.EcosAppsServiceFactory;
import ru.citeck.ecos.apps.app.domain.handler.ArtifactHandlerService;
import ru.citeck.ecos.apps.app.service.LocalAppService;
import ru.citeck.ecos.apps.artifact.ArtifactService;
import ru.citeck.ecos.apps.artifact.type.ArtifactTypeService;
import ru.citeck.ecos.apps.eapps.service.RemoteEappsService;
import ru.citeck.ecos.commands.CommandsServiceFactory;
import ru.citeck.ecos.records3.RecordsServiceFactory;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
public class EcosAppsFactoryConfig extends EcosAppsServiceFactory {

    @PostConstruct
    public void init() {
        super.init();
    }

    @Bean
    @NotNull
    @Override
    protected ArtifactHandlerService createArtifactHandlerService() {
        return super.createArtifactHandlerService();
    }

    @Bean
    @NotNull
    @Override
    protected ArtifactService createArtifactService() {

        ArtifactService artifactService = super.createArtifactService();

        Map<String, String> mapping = new HashMap<>();
        mapping.put("process/cmmn", "case/templates");

        artifactService.setArtifactLocations(mapping);

        return artifactService;
    }

    @Bean
    @NotNull
    @Override
    protected ArtifactTypeService createArtifactTypeService() {
        return super.createArtifactTypeService();
    }

    @Bean
    @NotNull
    @Override
    protected LocalAppService createLocalAppService() {
        return super.createLocalAppService();
    }

    @Bean
    @NotNull
    @Override
    protected RemoteEappsService createRemoteEappsService() {
        return super.createRemoteEappsService();
    }

    @Autowired
    public void injectCommandsServices(CommandsServiceFactory services) {
        setCommandsServices(services);
    }

    @Autowired
    public void injectRecordsServices(RecordsServiceFactory services) {
        setRecordsServices(services);
    }
}
