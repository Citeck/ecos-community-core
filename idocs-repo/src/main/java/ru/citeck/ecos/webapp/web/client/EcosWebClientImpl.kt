package ru.citeck.ecos.webapp.web.client

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ru.citeck.ecos.micrometer.EcosMicrometerContext
import ru.citeck.ecos.webapp.api.properties.EcosWebAppProps
import ru.citeck.ecos.webapp.api.web.client.EcosWebClientApi
import ru.citeck.ecos.webapp.api.web.client.EcosWebClientReq
import ru.citeck.ecos.webapp.lib.discovery.WebAppDiscoveryService
import ru.citeck.ecos.webapp.lib.web.authenticator.WebAuthenticatorsManager
import ru.citeck.ecos.webapp.lib.web.client.router.EcosWebRouter
import ru.citeck.ecos.webapp.lib.web.webapi.client.EcosWebClient
import ru.citeck.ecos.webapp.lib.web.webapi.client.props.EcosWebClientProps

@Component
class EcosWebClientImpl @Autowired constructor(
    private val webAppProps: EcosWebAppProps,
    private val webClientProperties: EcosWebClientProps,
    private val authenticatorsManager: WebAuthenticatorsManager,
    private val micrometerContext: EcosMicrometerContext
) : EcosWebClientApi {

    private var isReady: Boolean = false
    private lateinit var client: EcosWebClientApi

    fun init(webRouter: EcosWebRouter, discoveryService: WebAppDiscoveryService) {
        client = EcosWebClient(
            webAppProps,
            webClientProperties,
            webRouter,
            discoveryService,
            OkHttpWebReqComponent(webClientProperties),
            authenticatorsManager.getJwtAuthenticator(webClientProperties.authenticator),
            micrometerContext
        )
        isReady = true
    }

    override fun getApiVersion(targetApp: String, path: String, maxVersion: Int): Int {
        if (!isReady) {
            error("Client is not initialized")
        }
        return client.getApiVersion(targetApp, path, maxVersion)
    }

    override fun newRequest(): EcosWebClientReq {
        if (!isReady) {
            error("Client is not initialized")
        }
        return client.newRequest()
    }
}
