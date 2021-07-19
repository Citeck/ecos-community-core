package ru.citeck.ecos.eapps;

import kotlin.Unit;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourceInfo;
import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourceProvider;
import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourceType;
import ru.citeck.ecos.apps.app.domain.artifact.source.SourceKey;
import ru.citeck.ecos.apps.app.service.LocalAppService;
import ru.citeck.ecos.apps.app.util.AppDirWatchUtils;
import ru.citeck.ecos.apps.artifact.ArtifactService;
import ru.citeck.ecos.apps.artifact.type.TypeContext;
import ru.citeck.ecos.apps.eapps.service.RemoteEappsService;
import ru.citeck.ecos.commons.io.file.EcosFile;
import ru.citeck.ecos.commons.io.file.std.EcosStdFile;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class EcosArtifactSourceProviderImpl implements ArtifactSourceProvider {

    private static final String PROP_WATCHER_ENABLED = "eapps.watcher.artifacts.enabled";
    private static final String ALF_MODULE_SOURCE_PREFIX = "alf-module-";

    private final EappsUtils eappsUtils;
    private final ArtifactService artifactService;
    private final RemoteEappsService remoteEappsService;

    private final Map<String, ModuleArtifactsSource> sources = new ConcurrentHashMap<>();

    private boolean initialized = false;

    @Autowired
    public EcosArtifactSourceProviderImpl(EappsUtils eappsUtils,
                                          ArtifactService artifactService,
                                          LocalAppService localAppService,
                                          RemoteEappsService remoteEappsService) {
        this.eappsUtils = eappsUtils;
        this.artifactService = artifactService;
        this.remoteEappsService = remoteEappsService;

        localAppService.setSourceProvider(this);
    }

    public void init() {
        if (!initialized) {
            initSources();
            initWatcher();
            initialized = true;
        }
    }

    private void initSources() {

        eappsUtils.getModuleIds().forEach(moduleId -> {

            try {
                EcosFile moduleDir = eappsUtils.getClasspathDir("module/" + moduleId);
                if (moduleDir instanceof EcosStdFile) {
                    sources.put(ALF_MODULE_SOURCE_PREFIX + moduleId, new ModuleArtifactsSource(
                        moduleId,
                        (EcosStdFile) moduleDir
                    ));
                }
            } catch (Exception e) {
                log.warn("Module artifacts dir initialization failed. Module ID: " + moduleId, e);
            }
        });
    }

    private void initWatcher() {

        if (!eappsUtils.getBooleanProp(PROP_WATCHER_ENABLED, eappsUtils.isDevEnv())) {
            return;
        }

        sources.forEach((sourceId, source) -> {

            Path artifactsSrcDir =
                Paths.get("./" + source.moduleId + "/src/main/resources/alfresco/module/" + source.moduleId);

            boolean exists = false;
            try {
                exists = artifactsSrcDir.toFile().exists();
            } catch (Exception e) {
                log.debug("Artifacts SRC dir checking failed. Path: " + artifactsSrcDir);
            }

            if (exists) {
                source.artifactsDir = new EcosStdFile(artifactsSrcDir.toFile());
            }

            try {
                Path watchPath = source.artifactsDir.getFile().toPath();
                AppDirWatchUtils.watchJ(
                    "EcosArtifactsModule-" + source.moduleId,
                    watchPath,
                    Collections.singletonList(StandardWatchEventKinds.ENTRY_MODIFY),
                    (file, event) -> {

                        log.info("File change detected: " + file);
                        source.lastModified = Instant.now();

                        remoteEappsService.artifactsForceUpdate(ArtifactSourceInfo.create(builder -> {
                            builder.withKey(sourceId, ArtifactSourceType.APPLICATION);
                            builder.withLastModified(source.lastModified);
                            return Unit.INSTANCE;
                        }));
                    }
                );
                log.info("Artifacts watcher started for " + watchPath);
            } catch (Exception e) {
                log.error("Watcher registration error", e);
            }
        });
    }

    @NotNull
    @Override
    public List<ArtifactSourceInfo> getArtifactSources() {

        init();

        List<ArtifactSourceInfo> result = new ArrayList<>();

        sources.forEach((sourceId, source) ->
            result.add(ArtifactSourceInfo.create(builder -> {
                builder.withKey(sourceId, ArtifactSourceType.APPLICATION);
                builder.withLastModified(source.lastModified);
                return Unit.INSTANCE;
            }))
        );

        return result;
    }

    @NotNull
    @Override
    public Map<String, List<Object>> getArtifacts(@NotNull SourceKey sourceKey,
                                                  @NotNull List<? extends TypeContext> types,
                                                  @NotNull Instant since) {



        ModuleArtifactsSource source = sources.get(sourceKey.getId());
        if (source == null) {
            return Collections.emptyMap();
        }
        return artifactService.readArtifacts(source.artifactsDir, types);
    }

    public void update() {
        sources.values()
            .forEach(it -> it.lastModified = Instant.now());
    }

    @Data
    static class ModuleArtifactsSource {

        private final String moduleId;
        private EcosStdFile artifactsDir;
        private Instant lastModified = Instant.now();

        public ModuleArtifactsSource(String moduleId, EcosStdFile artifactsDir) {
            this.moduleId = moduleId;
            this.artifactsDir = artifactsDir;
        }
    }
}
