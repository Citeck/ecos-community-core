package ru.citeck.ecos.webapp.web.client

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ru.citeck.ecos.eureka.EcosAlfServiceDiscovery
import javax.annotation.PostConstruct

@Component
class EcosRemoteWebAppsApiInitializer {

    @Autowired
    private lateinit var remoteWebAppsApi: EcosRemoteWebAppsApiImpl
    @Autowired
    private lateinit var ecosAlfServiceDiscovery: EcosAlfServiceDiscovery

    @PostConstruct
    fun init() {
        remoteWebAppsApi.setEcosAlfServiceDiscovery(ecosAlfServiceDiscovery)
    }
}
