package ru.citeck.ecos.webapp.discovery

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ru.citeck.ecos.webapp.lib.discovery.WebAppDiscoveryInfoProvider
import ru.citeck.ecos.webapp.lib.discovery.WebAppDiscoveryService
import ru.citeck.ecos.webapp.lib.web.webapi.discovery.WebApiDiscoveryInfo
import ru.citeck.ecos.webapp.lib.web.webapi.executor.EcosWebExecutorsService
import javax.annotation.PostConstruct

@Component
class EcosWebApiDiscoveryInfoRegistrar @Autowired constructor(
    private val discoveryService: WebAppDiscoveryService,
    private val webExecutorsService: EcosWebExecutorsService
) {

    @PostConstruct
    fun init() {
        discoveryService.register(WebApiDiscoveryInfoProvider())
    }

    private inner class WebApiDiscoveryInfoProvider : WebAppDiscoveryInfoProvider<WebApiDiscoveryInfo> {

        override fun getInfo(): WebApiDiscoveryInfo {
            return WebApiDiscoveryInfo(
                "/alfresco/s/citeck/ecos/webapi",
                0,
                webExecutorsService.getExecutorsInfo()
            )
        }

        override fun getKey(): String {
            return WebApiDiscoveryInfo.KEY
        }

        override fun onUpdate(action: (WebApiDiscoveryInfo) -> Unit) {}
    }
}
