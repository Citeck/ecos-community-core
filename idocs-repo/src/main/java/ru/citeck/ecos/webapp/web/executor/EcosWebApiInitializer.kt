package ru.citeck.ecos.webapp.web.executor

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ru.citeck.ecos.webapp.api.web.executor.EcosWebExecutor
import ru.citeck.ecos.webapp.lib.discovery.WebAppDiscoveryService
import ru.citeck.ecos.webapp.lib.web.client.router.EcosWebRouter
import ru.citeck.ecos.webapp.lib.web.webapi.executor.EcosWebExecutorsService
import ru.citeck.ecos.webapp.web.client.EcosWebClientImpl
import javax.annotation.PostConstruct

@Component
class EcosWebApiInitializer @Autowired constructor(
    val executorsService: EcosWebExecutorsService,
    val ecosWebClient: EcosWebClientImpl,
    val webRouter: EcosWebRouter,
    val discoveryService: WebAppDiscoveryService
) {

    private var executors: List<EcosWebExecutor> = emptyList()

    @PostConstruct
    fun init() {
        executors.forEach {
            executorsService.register(it)
        }
        ecosWebClient.init(webRouter, discoveryService)
    }

    @Autowired(required = false)
    fun setExecutors(executors: List<EcosWebExecutor>) {
        this.executors = executors
    }
}
