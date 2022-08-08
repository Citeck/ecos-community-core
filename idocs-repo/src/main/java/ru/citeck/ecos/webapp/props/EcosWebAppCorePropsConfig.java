package ru.citeck.ecos.webapp.props;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.notifications.lib.NotificationsProperties;
import ru.citeck.ecos.webapp.lib.env.EcosWebAppEnvironment;
import ru.citeck.ecos.webapp.lib.web.client.props.EcosWebClientProps;
import ru.citeck.ecos.webapp.lib.web.client.props.EcosWebClientTlsProps;
import ru.citeck.ecos.webapp.lib.web.server.EcosWebServerProperties;

import java.util.Locale;

@Configuration
public class EcosWebAppCorePropsConfig {

    @Autowired
    private EcosWebAppEnvironment environment;

    @Value("${notifications.default.locale}")
    private String defaultAppNotificationLocale;

    @Value("${notifications.default.from}")
    private String defaultAppNotificationFrom;

    @Bean
    public EcosWebServerProperties webServerProperties() {
        return new EcosWebServerProperties("");
        //return environment.getValue("ecos.webapp.web.server", EcosWebServerProperties.class);
    }

    @Bean
    public EcosWebClientProps webClientProperties() {
        EcosWebClientTlsProps tls = environment.getValue("ecos-records.tls", EcosWebClientTlsProps.class);
        return EcosWebClientProps.create()
            .withTls(tls)
            .withAuthenticator("jwt")
            .build();
    }

    @Bean
    public NotificationsProperties notificationsProps() {
        return new NotificationsProperties(new Locale(defaultAppNotificationLocale), defaultAppNotificationFrom);
    }
}
