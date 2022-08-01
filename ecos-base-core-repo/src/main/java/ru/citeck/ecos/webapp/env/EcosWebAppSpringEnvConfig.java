package ru.citeck.ecos.webapp.env;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.Environment;
import ru.citeck.ecos.webapp.lib.env.EcosWebAppEnvironment;
import ru.citeck.ecos.webapp.lib.env.EcosWebAppEnvironmentImpl;

import java.util.Properties;

@Configuration
public class EcosWebAppSpringEnvConfig {

    @Bean
    public EcosWebAppEnvironment ecosWebAppEnvironment(
        Environment environment,
        @Qualifier("global-properties")
        Properties properties
    ) {
        return new EcosWebAppEnvironmentImpl(new WebAppSpringEnvironment(
            (AbstractEnvironment) environment,
            properties
        ));
    }
}
