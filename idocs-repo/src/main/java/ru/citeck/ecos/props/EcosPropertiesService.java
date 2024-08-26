package ru.citeck.ecos.props;

import ecos.guava30.com.google.common.base.CaseFormat;
import ecos.guava30.com.google.common.cache.CacheBuilder;
import ecos.guava30.com.google.common.cache.CacheLoader;
import ecos.guava30.com.google.common.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.util.Pair;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.config.EcosConfigService;

import javax.annotation.PostConstruct;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Service
public class EcosPropertiesService {

    @Autowired
    @Qualifier("global-properties")
    private Properties properties;

    @Autowired
    @Qualifier("ecosConfigService")
    private EcosConfigService ecosConfigService;

    private LoadingCache<Pair<String, Class<?>>, Optional<Object>> cache;

    @PostConstruct
    public void init() {
        cache = CacheBuilder.newBuilder()
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .maximumSize(200)
            .build(CacheLoader.from(this::getPropertyImpl));
    }

    public String getStr(String key) {
        return getStr(key, "");
    }

    public String getStr(String key, String dflt) {
        return getProperty(key, String.class, () -> dflt);
    }

    public int getInt(String key) {
        return getInt(key, 0);
    }

    public int getInt(String key, int dflt) {
        return getProperty(key, Integer.class, () -> dflt);
    }

    public boolean getBool(String key) {
        return getBool(key, false);
    }

    public boolean getBool(String key, boolean dflt) {
        return getProperty(key, Boolean.class, () -> dflt);
    }

    public <T> T getProperty(String key, Class<T> type) {
        return getProperty(key, type, () -> null);
    }

    public <T> T getProperty(String key, Class<T> type, Supplier<T> orElse) {
        @SuppressWarnings("unchecked")
        T result = (T) cache.getUnchecked(new Pair<>(key, type)).orElseGet(orElse);
        return result;
    }

    private Optional<Object> getPropertyImpl(Pair<String, Class<?>> keyAndType) {

        String key = keyAndType.getFirst();
        Class<?> type = keyAndType.getSecond();

        Object value = convert(ecosConfigService.getParamValue(key), type);
        if (value == null) {
            String envKey = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, key);
            envKey = envKey.replaceAll("[^a-zA-Z0-9_]", "_").toUpperCase();
            value = convert(System.getenv(envKey), type);
        }
        if (value == null) {
            value = convert(properties.getProperty(key), type);
        }
        return Optional.ofNullable(value);
    }

    private <T> T convert(Object value, Class<T> type) {
        if (value == null || (value instanceof String && StringUtils.isBlank((String) value))) {
            return null;
        }
        try {
            return Json.getMapper().convert(value, type);
        } catch (Exception e) {
            log.error("Config reading failed. Value: " + value + " type: " + type, e);
        }
        return null;
    }
}
