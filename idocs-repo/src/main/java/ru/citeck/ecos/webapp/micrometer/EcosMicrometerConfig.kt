package ru.citeck.ecos.webapp.micrometer

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.micrometer.EcosMicrometerContext

@Configuration
class EcosMicrometerConfig {

    @Bean
    fun ecosMicrometerContext(): EcosMicrometerContext {
        return EcosMicrometerContext.NOOP
    }
}
