package ru.citeck.ecos.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

import ru.citeck.ecos.commons.bean.BeansRegistry
import ru.citeck.ecos.commons.bean.DefaultBeansRegistry
import ru.citeck.ecos.micrometer.EcosMicrometerContext
import ru.citeck.ecos.webapp.api.EcosWebAppApi

@Configuration
class EcosBeansRegistryConfig {

    @Bean
    fun ecosBeansRegistry(
        webAppApi: EcosWebAppApi,
        micrometerContext: EcosMicrometerContext
        //x509Registry: EcosX509Registry todo
    ): BeansRegistry {
        val registry = DefaultBeansRegistry()
        registry.register(webAppApi)
        registry.register(micrometerContext)
        return registry
    }
}
