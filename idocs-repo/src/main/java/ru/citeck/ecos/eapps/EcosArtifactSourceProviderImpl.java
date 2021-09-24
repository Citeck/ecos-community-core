package ru.citeck.ecos.eapps;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.domain.artifact.reader.ArtifactsReader;
import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourceInfo;
import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourceProvider;
import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourceType;
import ru.citeck.ecos.apps.app.domain.artifact.source.SourceKey;
import ru.citeck.ecos.apps.app.util.AppDirWatchUtils;
import ru.citeck.ecos.apps.artifact.type.TypeContext;
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
    private ArtifactsReader artifactsReader;

    private final Map<String, ModuleArtifactsSource> sources = new ConcurrentHashMap<>();

    private boolean initialized = false;

    private Function1<? super ArtifactSourceInfo, Unit> listener = null;

    @Autowired
    public EcosArtifactSourceProviderImpl(EappsUtils eappsUtils) {
        this.eappsUtils = eappsUtils;
    }

    @Override
    public void init(@NotNull ArtifactsReader artifactsReader) {

        this.artifactsReader = artifactsReader;

        if (!initialized) {
            initSources();
            initWatcher();
            initialized = true;
        }
    }

    private void initSources() {
        eappsUtils.getModulePaths().forEach((moduleId, path) ->
            sources.put(
                ALF_MODULE_SOURCE_PREFIX + moduleId,
                new ModuleArtifactsSource(
                    moduleId,
                    new EcosStdFile(path.toFile())
                )
            )
        );
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

                        listener.invoke(ArtifactSourceInfo.create(builder -> {
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
        return artifactsReader.readArtifacts(source.artifactsDir, types);
    }

    public void update() {
        sources.values()
            .forEach(it -> it.lastModified = Instant.now());
    }

    @Override
    public boolean isStatic() {
        return true;
    }

    @Override
    public void listenChanges(@NotNull Function1<? super ArtifactSourceInfo, Unit> listener) {
        this.listener = listener;
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
