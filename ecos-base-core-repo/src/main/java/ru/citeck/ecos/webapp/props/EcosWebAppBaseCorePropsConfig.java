package ru.citeck.ecos.webapp.props;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.records3.RecordsProperties;
import ru.citeck.ecos.webapp.api.properties.EcosWebAppProperties;
import ru.citeck.ecos.webapp.lib.env.EcosWebAppEnvironment;
import ru.citeck.ecos.webapp.lib.utils.AppUtils;

@Configuration
public class EcosWebAppBaseCorePropsConfig {

    private static final String DEFAULT_APP_NAME = "alfresco";

    @Autowired
    private EcosWebAppEnvironment environment;

    @Value("${ecos.eureka.instance.appname}")
    private String appName;

    @Bean
    public EcosWebAppProperties webappProperties() {
        String appname = DEFAULT_APP_NAME;
        if (this.appName != null && !this.appName.contains("{")) {
            appname = this.appName;
        }
        String instanceId = AppUtils.generateAppInstanceId();
        return new EcosWebAppProperties(
            appname,
            instanceId,
            false
        );
    }

    @Bean
    public RecordsProperties recordsProperties() {
        return environment.getValue("ecos.webapp.records", RecordsProperties.class);
    }
}
