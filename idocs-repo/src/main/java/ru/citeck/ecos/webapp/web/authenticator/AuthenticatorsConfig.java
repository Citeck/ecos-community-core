package ru.citeck.ecos.webapp.web.authenticator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import ru.citeck.ecos.webapp.api.constants.WebAppProfile;
import ru.citeck.ecos.webapp.lib.env.EcosWebAppEnvironment;
import ru.citeck.ecos.webapp.lib.web.authenticator.WebAuthenticatorFactory;
import ru.citeck.ecos.webapp.lib.web.authenticator.WebAuthenticatorsManager;
import ru.citeck.ecos.webapp.lib.web.authenticator.jwt.JwtAuthenticatorFactory;

import java.util.List;

@Configuration
public class AuthenticatorsConfig {

    @Autowired
    private Environment env;
    @Autowired
    private EcosWebAppEnvironment ecosWebAppEnvironment;

    @Bean
    public WebAuthenticatorFactory<?> jwtAuthenticatorFactory() {
        return new JwtAuthenticatorFactory(env.acceptsProfiles(WebAppProfile.TEST));
    }

    @Bean
    public WebAuthenticatorsManager authenticatorsManager(List<WebAuthenticatorFactory<?>> factories) {
        WebAuthenticatorsManager manager = new WebAuthenticatorsManager(ecosWebAppEnvironment);
        manager.register(factories);
        return manager;
    }
}
