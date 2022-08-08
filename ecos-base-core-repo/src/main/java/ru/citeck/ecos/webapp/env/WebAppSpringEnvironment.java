package ru.citeck.ecos.webapp.env;

import ecos.com.google.common.base.CaseFormat;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.env.*;
import ru.citeck.ecos.webapp.lib.env.EnvironmentComponent;

import java.util.*;

@RequiredArgsConstructor
public class WebAppSpringEnvironment implements EnvironmentComponent {

    private final AbstractEnvironment environment;
    private final Properties globalProps;

    @Override
    public boolean acceptsProfiles(@NotNull String... profiles) {
        return environment.acceptsProfiles(profiles);
    }

    @Override
    public boolean containsProperty(@NotNull String key) {
        return getProperty(key) != null;
    }

    @Nullable
    @Override
    public String getProperty(@NotNull String key) {
        return getProperty(key, String.class);
    }

    @Nullable
    @Override
    public <T> T getProperty(@NotNull String key, @NotNull Class<T> aClass) {
        if (aClass == String.class) {
            return aClass.cast(getStrProp(key, null));
        } if (aClass == boolean.class) {
            return aClass.cast(Boolean.TRUE.toString().equals(getStrProp(key, Boolean.FALSE.toString())));
        } else if (aClass == Boolean.class) {
            return aClass.cast(Boolean.TRUE.toString().equals(getStrProp(key, null)));
        }
        return environment.getProperty(key, aClass);
    }

    @Override
    public boolean containsValue(@NotNull String key) {

        MutablePropertySources sources = environment.getPropertySources();
        String keyPrefix = key + ".";

        Enumeration<?> keys = globalProps.propertyNames();
        while (keys.hasMoreElements()) {
            Object propsKey = keys.nextElement();
            if (propsKey instanceof String) {
                String strKey = (String) propsKey;
                if (strKey.equals(key) || strKey.startsWith(keyPrefix)) {
                    return true;
                }
            }
        }

        for (PropertySource<?> source : sources) {
            if (source instanceof EnumerablePropertySource) {
                String[] names = ((EnumerablePropertySource<?>) source).getPropertyNames();
                for (String name : names) {
                    if (key.equals(name) || name.startsWith(keyPrefix)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private String getStrProp(String key, String orElse) {

        String envKey = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, key);
        envKey = envKey.replaceAll("[^a-zA-Z0-9_]", "_").toUpperCase();
        String value = System.getenv(envKey);
        if (StringUtils.isBlank(value)) {
            value = globalProps.getProperty(key);
        }
        if (StringUtils.isBlank(value)) {
            value = environment.getProperty(key);
        }
        if (StringUtils.isBlank(value)) {
            return orElse;
        }
        return value;
    }

    @NotNull
    @Override
    public List<String> getActiveProfiles() {
        String[] profiles = environment.getActiveProfiles();
        if (profiles == null || profiles.length == 0) {
            profiles = environment.getDefaultProfiles();
        }
        if (profiles == null || profiles.length == 0) {
            return Collections.emptyList();
        }
        return Arrays.asList(profiles);
    }
}
