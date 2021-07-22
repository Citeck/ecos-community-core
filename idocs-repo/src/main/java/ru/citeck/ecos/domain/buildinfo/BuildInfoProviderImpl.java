package ru.citeck.ecos.domain.buildinfo;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.service.cmr.module.ModuleService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.domain.buildinfo.dto.BuildInfo;
import ru.citeck.ecos.apps.app.domain.buildinfo.service.BuildInfoProvider;
import ru.citeck.ecos.apps.app.domain.buildinfo.service.BuildInfoService;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.utils.ResourceResolver;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class BuildInfoProviderImpl implements BuildInfoProvider {

    private ResourceResolver resourceResolver;
    private final ModuleService moduleService;
    private final BuildInfoService buildInfoService;

    @PostConstruct
    public void init() {
        buildInfoService.addProvider(this);
    }

    @NotNull
    @Override
    public List<BuildInfo> getBuildInfo() {

        Map<String, ModuleBuildInfo> result = new HashMap<>();

        moduleService.getAllModules().forEach(module -> {

            BuildInfo buildInfo = getBuildInfoForModule(module.getId());

            if (buildInfo != null) {

                ModuleBuildInfo currentBuildInfo = result.get(buildInfo.getRepo());

                if (currentBuildInfo != null) {

                    if (!Objects.equals(currentBuildInfo.buildInfo, buildInfo)) {

                        log.warn("Found two modules from the same repository, " +
                            "but with different build info. " +
                            "First module: "
                            + formatBuildInfoForLog(currentBuildInfo.moduleId, currentBuildInfo.buildInfo)
                            + " Second module: "
                            + formatBuildInfoForLog(module.getId(), buildInfo));

                        if (currentBuildInfo.buildInfo.getBuildDate().isBefore(buildInfo.getBuildDate())) {
                            result.put(buildInfo.getRepo(), new ModuleBuildInfo(buildInfo, module.getId()));
                        }
                    }
                } else {
                    log.info("Found build info: " + formatBuildInfoForLog(module.getId(), buildInfo));
                    result.put(buildInfo.getRepo(), new ModuleBuildInfo(buildInfo, module.getId()));
                }
            }
        });

        if (result.isEmpty()) {
            log.info("Build info doesn't exists in any module");
            return Collections.emptyList();
        }

        return result.values()
            .stream()
            .map(ModuleBuildInfo::getBuildInfo)
            .collect(Collectors.toList());
    }

    private String formatBuildInfoForLog(String moduleId, BuildInfo buildInfo) {
        return moduleId + " (repo: " + buildInfo.getRepo()
            + " branch: " + buildInfo.getBranch()
            + " version: " + buildInfo.getVersion()
            + " buildDate: "  + buildInfo.getBuildDate() + ")";
    }

    @Nullable
    private BuildInfo getBuildInfoForModule(String moduleId) {
        try {
            Resource modulesDir = resourceResolver.getResource("classpath:alfresco/module/" + moduleId);
            if (modulesDir != null && modulesDir.exists()) {
                try {
                    File modulesDirFile = modulesDir.getFile();
                    File buildInfoFile = new File(modulesDirFile, "build-info/full.json");
                    if (buildInfoFile.exists()) {
                        BuildInfo info = Json.getMapper().read(buildInfoFile, BuildInfo.class);
                        if (info == null) {
                            log.warn("Build info reading failed. Path: " + buildInfoFile.getAbsolutePath());
                        }
                        return info;
                    }
                } catch (FileNotFoundException e) {
                    // module is not a directory (e.g. jar module). do nothing
                }
            }
        } catch (Exception e) {
            // this error is not critical and can be logged with debug level
            log.debug("Module build info can't be resolved. ModuleId: " + moduleId, e);
        }
        return null;
    }

    @Autowired
    @Qualifier("resourceResolver")
    public void setResourceResolver(ResourceResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
    }

    @Data
    @RequiredArgsConstructor
    private final static class ModuleBuildInfo {
        private final BuildInfo buildInfo;
        private final String moduleId;
    }
}
