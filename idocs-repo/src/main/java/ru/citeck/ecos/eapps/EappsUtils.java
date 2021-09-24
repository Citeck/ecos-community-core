package ru.citeck.ecos.eapps;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.repo.module.ModuleDetailsImpl;
import org.alfresco.service.cmr.module.ModuleDetails;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.io.file.EcosFile;
import ru.citeck.ecos.commons.io.file.mem.EcosMemDir;
import ru.citeck.ecos.commons.io.file.std.EcosStdFile;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class EappsUtils implements ApplicationContextAware {

    private static final String DEV_ENV_PROP = "ecos.environment.dev";
    private static final String MODULE_CONFIG_SEARCH_ALL = "classpath*:alfresco/module/*/module.properties";

    private ResourcePatternResolver resolver;

    @Autowired
    @Qualifier("global-properties")
    private Properties globalProps;

    @Getter(lazy = true)
    private final Map<String, Path> modulePaths = getModulePathsImpl();

    @SneakyThrows
    private Map<String, Path> getModulePathsImpl() {

        Map<String, Path> result = new HashMap<>();
        Resource[] modulesProps = resolver.getResources(MODULE_CONFIG_SEARCH_ALL);

        for (Resource moduleProps : modulesProps) {
            try {
                InputStream is = new BufferedInputStream(moduleProps.getInputStream());
                Properties properties = new Properties();
                properties.load(is);
                ModuleDetails details = new ModuleDetailsImpl(properties);
                if (StringUtils.isNotBlank(details.getId())) {
                    File file;
                    try {
                        file = moduleProps.getFile();
                    } catch (IOException ex) {
                        log.debug("Props of module '" + details.getId() + "' " +
                            "is not available in file system. Skip it");
                        continue;
                    }
                    result.put(details.getId(), file.toPath().getParent());
                }
            } catch (Throwable e) {
                log.error("Unable to use module information for resource: " + moduleProps, e);
            }
        }
        return result;
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
            .map(Paths::get)
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

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.resolver = applicationContext;
    }
}
