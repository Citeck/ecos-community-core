package ru.citeck.ecos.records;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.eureka.EurekaAlfInstanceConfig;
import ru.citeck.ecos.records3.RecordsProperties;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Configuration
public class RecordsPropertiesConfiguration {

    private static final String ECOS_RECORDS_PREFIX = "ecos.records.";

    private static final String TLS_ENABLED_KEY = ECOS_RECORDS_PREFIX + "tls.enabled";
    private static final String TLS_KEY_STORE_KEY = ECOS_RECORDS_PREFIX + "tls.key-store";
    private static final String TLS_KEY_STORE_TYPE_KEY = ECOS_RECORDS_PREFIX + "tls.key-store-type";
    private static final String TLS_KEY_STORE_PASSWORD_KEY = ECOS_RECORDS_PREFIX + "tls.key-store-password";
    private static final String TLS_KEY_STORE_KEY_ALIAS_KEY = ECOS_RECORDS_PREFIX + "tls.key-store-key-alias";
    private static final String TLS_TRUST_STORE_KEY = ECOS_RECORDS_PREFIX + "tls.trust-store";
    private static final String TLS_TRUST_STORE_TYPE_KEY = ECOS_RECORDS_PREFIX + "tls.trust-store-type";
    private static final String TLS_TRUST_STORE_PASSWORD_KEY = ECOS_RECORDS_PREFIX + "tls.trust-store-password";

    private static final String ECOS_RECORDS_APPS_PREFIX = "ecos.records.apps.";
    private static final String RECORDS_APP_BASE_URL_CONFIG_NAME = "rec-base-url";
    private static final String RECORDS_APP_USER_BASE_URL_CONFIG_NAME = "rec-user-base-url";
    private static final String RECORDS_APP_AUTH_USERNAME_CONFIG_NAME = "auth.username";
    private static final String RECORDS_APP_AUTH_PASSWORD_CONFIG_NAME = "auth.password";
    private static final String RECORDS_APP_TLS_ENABLED_CONFIG_NAME = "tls.enabled";

    private final Pattern ECOS_RECORDS_APP_CONFIG_PATTERN = Pattern.compile(
        "^ecos\\.records\\.apps\\.(?<appName>[\\w-]*)\\.(?<configName>.*)$");

    @Autowired
    private EurekaAlfInstanceConfig eurekaAlfInstanceConfig;

    @Autowired
    @Qualifier("global-properties")
    private Properties globalProps;

    @Bean
    public RecordsProperties createRecordsProperties() {
        RecordsProperties properties = new RecordsProperties();
        properties.setAppName(eurekaAlfInstanceConfig.getAppname());
        properties.setAppInstanceId(eurekaAlfInstanceConfig.getInstanceId());
        properties.setApps(composeAppsRecordProperties());
        properties.setTls(composeTlsRecordProperties());
        return properties;
    }

    private Map<String, RecordsProperties.App> composeAppsRecordProperties() {
        Map<String, RecordsProperties.App> result = new HashMap<>();
        for (String propName : globalProps.stringPropertyNames()) {
            if (!propName.startsWith(ECOS_RECORDS_APPS_PREFIX)) {
                continue;
            }

            Matcher matcher = ECOS_RECORDS_APP_CONFIG_PATTERN.matcher(propName);
            if (!matcher.find()) {
                continue;
            }

            String appName = matcher.group("appName");
            String configName = matcher.group("configName");
            if (StringUtils.isBlank(appName) || StringUtils.isBlank(configName)) {
                continue;
            }

            switch (configName) {
                case RECORDS_APP_BASE_URL_CONFIG_NAME:
                    getRecordAppWithName(result, appName).setRecBaseUrl(globalProps.getProperty(propName, ""));
                    break;

                case RECORDS_APP_USER_BASE_URL_CONFIG_NAME:
                    getRecordAppWithName(result, appName).setRecUserBaseUrl(globalProps.getProperty(propName, ""));
                    break;

                case RECORDS_APP_AUTH_USERNAME_CONFIG_NAME:
                    RecordsProperties.App appForSetUsername = getRecordAppWithName(result, appName);
                    if (appForSetUsername.getAuth() == null) {
                        RecordsProperties.Authentication authentication = new RecordsProperties.Authentication();
                        authentication.setUsername(globalProps.getProperty(propName, ""));
                        appForSetUsername.setAuth(authentication);
                    } else {
                        appForSetUsername.getAuth().setUsername(globalProps.getProperty(propName, ""));
                    }
                    break;

                case RECORDS_APP_AUTH_PASSWORD_CONFIG_NAME:
                    RecordsProperties.App appForSetPassword = getRecordAppWithName(result, appName);
                    if (appForSetPassword.getAuth() == null) {
                        RecordsProperties.Authentication authentication = new RecordsProperties.Authentication();
                        authentication.setPassword(globalProps.getProperty(propName, ""));
                        appForSetPassword.setAuth(authentication);
                    } else {
                        appForSetPassword.getAuth().setPassword(globalProps.getProperty(propName, ""));
                    }
                    break;

                case RECORDS_APP_TLS_ENABLED_CONFIG_NAME:
                    getRecordAppWithName(result, appName).getTls().setEnabled(getBooleanProp(propName, false));
                    break;
            }
        }
        return result;
    }

    private RecordsProperties.App getRecordAppWithName(Map<String, RecordsProperties.App> appsProperties, String appName) {
        return appsProperties.computeIfAbsent(appName, (key) -> new RecordsProperties.App());
    }

    private RecordsProperties.Tls composeTlsRecordProperties() {
        RecordsProperties.Tls tls = new RecordsProperties.Tls();

        tls.setEnabled(getBooleanProp(TLS_ENABLED_KEY, tls.getEnabled()));
        tls.setKeyStore(globalProps.getProperty(TLS_KEY_STORE_KEY, tls.getKeyStore()));
        tls.setKeyStoreType(globalProps.getProperty(TLS_KEY_STORE_TYPE_KEY, tls.getKeyStoreType()));
        tls.setKeyStorePassword(globalProps.getProperty(TLS_KEY_STORE_PASSWORD_KEY, tls.getKeyStorePassword()));
        tls.setKeyStoreKeyAlias(globalProps.getProperty(TLS_KEY_STORE_KEY_ALIAS_KEY, tls.getKeyStoreKeyAlias()));
        tls.setTrustStore(globalProps.getProperty(TLS_TRUST_STORE_KEY, tls.getTrustStore()));
        tls.setTrustStoreType(globalProps.getProperty(TLS_TRUST_STORE_TYPE_KEY, tls.getTrustStoreType()));
        tls.setTrustStorePassword(globalProps.getProperty(TLS_TRUST_STORE_PASSWORD_KEY, tls.getTrustStorePassword()));

        return tls;
    }

    public boolean getBooleanProp(String prop, boolean orElse) {
        String value = globalProps.getProperty(prop);
        if (StringUtils.isBlank(value)) {
            return orElse;
        }
        return "true".equalsIgnoreCase(value);
    }
}
