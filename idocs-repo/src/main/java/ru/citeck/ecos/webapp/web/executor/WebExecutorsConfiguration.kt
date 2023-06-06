package ru.citeck.ecos.webapp.web.executor

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.txn.lib.manager.TransactionManager
import ru.citeck.ecos.webapp.api.properties.EcosWebAppProps
import ru.citeck.ecos.webapp.lib.web.authenticator.WebAuthenticatorsManager
import ru.citeck.ecos.webapp.lib.web.webapi.executor.EcosWebExecutorsService

@Configuration
class WebExecutorsConfiguration {

    @Bean
    fun executorsService(
        webAppProps: EcosWebAppProps,
        authenticatorsManager: WebAuthenticatorsManager,
        txnManager: TransactionManager
    ): EcosWebExecutorsService {
        return EcosWebExecutorsService(
            webAppProps,
            authenticatorsManager.getJwtAuthenticator("jwt"),
            txnManager
        )
    }
}
