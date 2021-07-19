package ru.citeck.ecos.eapps;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.domain.artifact.type.ArtifactTypeProvider;
import ru.citeck.ecos.apps.app.service.LocalAppService;
import ru.citeck.ecos.apps.app.util.AppDirWatchUtils;
import ru.citeck.ecos.commons.io.file.EcosFile;
import ru.citeck.ecos.commons.io.file.mem.EcosMemDir;
import ru.citeck.ecos.commons.io.file.std.EcosStdFile;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class EcosArtifactTypeProviderImpl implements ArtifactTypeProvider {

    private static final String PROP_WATCHER_ENABLED = "eapps.watcher.types.enabled";
    private static final String PROP_WATCHER_LOCATIONS = "eapps.watcher.types.locations";

    private final EappsUtils eappsUtils;

    private Instant lastModified = Instant.now();
    private List<EcosStdFile> locationsToWatch = Collections.emptyList();

    private boolean initialized = false;

    @Autowired
    public EcosArtifactTypeProviderImpl(EappsUtils eappsUtils,
                                        LocalAppService localAppService) {
        this.eappsUtils = eappsUtils;
        localAppService.setTypeProvider(this);
    }

    public void init() {
        if (!initialized) {
            initWatcher();
            initialized = true;
        }
    }

    private void initWatcher() {

        if (!eappsUtils.getBooleanProp(PROP_WATCHER_ENABLED, eappsUtils.isDevEnv())) {
            return;
        }

        List<Path> possibleLocationsToWatch = new ArrayList<>();

        eappsUtils.getModuleIds().forEach(moduleId -> {
            String path = "./" + moduleId + "/src/main/resources/alfresco/extension/eapps/types";
            possibleLocationsToWatch.add(Paths.get(path));
        });
        possibleLocationsToWatch.addAll(eappsUtils.getLocationsFromProp(PROP_WATCHER_LOCATIONS));

        List<Path> locationsToWatch = new ArrayList<>();
        possibleLocationsToWatch.forEach(typesDir -> {
            try {
                if (typesDir.toFile().exists()) {
                    locationsToWatch.add(typesDir);
                }
            } catch (SecurityException e) {
                log.warn("Types location checking failed: " + typesDir, e);
            }
        });

        AppDirWatchUtils.watchJ(
            "EcosArtifactType",
            locationsToWatch,
            Collections.singletonList(StandardWatchEventKinds.ENTRY_MODIFY),
            this::onTypeFileChanged
        );

        this.locationsToWatch = locationsToWatch.stream()
            .map(location -> new EcosStdFile(location.toFile()))
            .collect(Collectors.toList());

        log.info("Artifact types watcher started for " + locationsToWatch);
    }

    private void onTypeFileChanged(Path path, WatchEvent<Path> event) {
        lastModified = Instant.now();
    }

    @NotNull
    @Override
    public EcosFile getArtifactTypesDir() {

        init();

        EcosFile result = eappsUtils.getClasspathDir("extension/eapps/types");

        if (locationsToWatch.isEmpty()) {
            return result;
        }

        EcosMemDir resultWithWatchedTypes = new EcosMemDir();
        resultWithWatchedTypes.copyFilesFrom(result);
        locationsToWatch.forEach(resultWithWatchedTypes::copyFilesFrom);

        return resultWithWatchedTypes;
    }

    @Nullable
    @Override
    public Instant getLastModified() {
        return lastModified;
    }

    public void update() {
        this.lastModified = Instant.now();
    }
}
