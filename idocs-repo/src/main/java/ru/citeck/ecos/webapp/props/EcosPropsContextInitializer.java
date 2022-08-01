package ru.citeck.ecos.webapp.props;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import ru.citeck.ecos.webapp.io.EcosSpringResourceResolverAdapter;
import ru.citeck.ecos.webapp.lib.env.EcosWebAppPropsLoader;

import java.util.Arrays;
import java.util.Map;

@Slf4j
public class EcosPropsContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String APPLICATION_PROPS_NAME = "application";
    private static final String ECOS_PROPS_SOURCE_PREFIX = "ecos-props-";

    private final EcosWebAppPropsLoader propsLoader = new EcosWebAppPropsLoader(
        Arrays.asList("alfresco/module/*/props", "ecos/props")
    );

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {

        EcosSpringResourceResolverAdapter resolver = new EcosSpringResourceResolverAdapter(applicationContext);
        ConfigurableEnvironment environment = applicationContext.getEnvironment();

        String[] profiles = environment.getActiveProfiles();
        if (profiles == null || profiles.length == 0) {
            profiles = environment.getDefaultProfiles();
        }

        Map<String, Object> props = propsLoader.load(resolver, APPLICATION_PROPS_NAME, Arrays.asList(profiles));

        environment.getPropertySources().addLast(
            new MapPropertySource(ECOS_PROPS_SOURCE_PREFIX + APPLICATION_PROPS_NAME, props)
        );
    }
}
