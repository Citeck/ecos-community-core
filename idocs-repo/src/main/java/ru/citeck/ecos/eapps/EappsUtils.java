package ru.citeck.ecos.eapps;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.service.cmr.module.ModuleDetails;
import org.alfresco.service.cmr.module.ModuleService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.io.file.EcosFile;
import ru.citeck.ecos.commons.io.file.mem.EcosMemDir;
import ru.citeck.ecos.commons.io.file.std.EcosStdFile;
import ru.citeck.ecos.utils.ResourceResolver;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

@Slf4j
@Component
public class EappsUtils {

    private static final String DEV_ENV_PROP = "ecos.environment.dev";
    private static final String DEV_ARTIFACTS_LOCATIONS_PROP = "eapps.watcher.artifacts.locations";

    private final ResourceResolver resolver;
    private final ModuleService moduleService;

    @Autowired
    @Qualifier("global-properties")
    private Properties globalProps;

    @Autowired
    public EappsUtils(@Qualifier("resourceResolver") ResourceResolver resolver,
                      ModuleService moduleService) {
        this.resolver = resolver;
        this.moduleService = moduleService;
    }

    public List<String> getModuleIds() {
        return moduleService.getAllModules()
            .stream()
            .map(ModuleDetails::getId)
            .collect(Collectors.toList());
    }

    public boolean isDevEnv() {
        return getBooleanProp(DEV_ENV_PROP, false);
    }

    public boolean getBooleanProp(String prop, boolean orElse) {
        String value = globalProps.getProperty(prop);
        if (StringUtils.isBlank(value)) {
            return orElse;
        }
        return "true".equals(value);
    }

    public List<Path> getLocationsFromProp(String prop) {

        String value = globalProps.getProperty(prop);
        if (StringUtils.isBlank(value)) {
            return Collections.emptyList();
        }

        return Arrays.stream(value.split(","))
            .map(v -> Paths.get(v))
            .collect(Collectors.toList());
    }

    public EcosFile getClasspathDir(String path) {
        try {
            Resource classpathDir = resolver.getResource("classpath:alfresco/" + path);
            if (classpathDir != null && classpathDir.exists()) {
                File file = null;
                try {
                    file = classpathDir.getFile();
                } catch (FileNotFoundException e) {
                    // module is not a directory (e.g. jar module). do nothing
                }
                if (file != null) {
                    return new EcosStdFile(file);
                }
            }
        } catch (Exception e) {
            log.error("Directory resolving error. Path: " + path, e);
        }
        return new EcosMemDir();
    }
}
