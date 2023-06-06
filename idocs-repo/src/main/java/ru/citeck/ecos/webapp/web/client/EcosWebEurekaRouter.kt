package ru.citeck.ecos.webapp.web.client

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ru.citeck.ecos.eureka.EcosAlfServiceDiscovery
import ru.citeck.ecos.eureka.EcosServiceInstanceInfo
import ru.citeck.ecos.webapp.api.properties.EcosWebAppProps
import ru.citeck.ecos.webapp.lib.discovery.WebAppDiscoveryService
import ru.citeck.ecos.webapp.lib.discovery.instance.AppInstanceInfo
import ru.citeck.ecos.webapp.lib.discovery.instance.AppInstanceRef
import ru.citeck.ecos.webapp.lib.discovery.instance.PortInfo
import ru.citeck.ecos.webapp.lib.discovery.instance.PortType
import ru.citeck.ecos.webapp.lib.env.EcosWebAppEnvironment
import ru.citeck.ecos.webapp.lib.web.client.router.EcosWebRoute
import ru.citeck.ecos.webapp.lib.web.client.router.EcosWebRouter
import javax.annotation.PostConstruct

@Component
class EcosWebEurekaRouter @Autowired constructor(
    val webAppProps: EcosWebAppProps,
    val env: EcosWebAppEnvironment
) : EcosWebRouter {

    companion object {
        private const val LOCALHOST = "localhost"
        private val log = KotlinLogging.logger {}
    }

    private lateinit var discoveryService: WebAppDiscoveryService
    private lateinit var alfDiscoveryService: EcosAlfServiceDiscovery

    private var isLocalDevEnv = false
    private var isTlsEnabled = false

    private var selfPort: Int = 0

    @PostConstruct
    fun init() {
        isLocalDevEnv = env.acceptsProfiles("dev_local") || env.getText("ecos.environment.dev") == "true"
        isTlsEnabled = env.acceptsProfiles("tls")
        selfPort = env.getText("alfresco.port").toInt()
    }

    override fun getRoute(appName: String): EcosWebRoute? {
        if (appName == webAppProps.appName) {
            return EcosWebRoute(
                LOCALHOST,
                AppInstanceRef.create(webAppProps.appName, webAppProps.appInstanceId),
                if (isTlsEnabled) {
                    listOf(PortInfo(selfPort, PortType.HTTPS))
                } else {
                    listOf(PortInfo(selfPort, PortType.HTTP))
                }
            )
        }
        var route: EcosWebRoute? = null

        if (appName.contains(AppInstanceRef.INSTANCE_ID_DELIM)) {
            return getRouteByInstanceRef(AppInstanceRef.valueOf(appName))
        }

        val txnRoutes = EcosWebRoute.getTxnRoutesMap()
        val txnAppInstanceId = txnRoutes[appName] ?: ""
        if (txnAppInstanceId.isNotEmpty()) {
            getRouteByInstanceRef(AppInstanceRef.create(appName, txnAppInstanceId))
                ?: error(
                    "Application instance '$txnAppInstanceId' is not " +
                        "found for transactional route of application '$appName'"
                )
        }
        try {
            route = alfDiscoveryService.getInstanceInfo(appName)?.let { getRoute(it) }
        } catch (e: RuntimeException) {
            if (e.message?.startsWith("No matches") != true) {
                log.error(e) { "Exception in getNextServerFromEureka. AppName: $appName" }
            }
        }
        if (route == null) {
            val instances = discoveryService.getInstances(appName)
            if (instances.isNotEmpty()) {
                // todo: add routing strategies support
                route = getRoute(instances[0])
            }
        }
        if (route != null) {
            txnRoutes[route.instanceRef.name] = route.instanceRef.instanceId
        }
        return route
    }

    private fun getRouteByInstanceRef(instanceRef: AppInstanceRef): EcosWebRoute? {
        val eurekaInstance = alfDiscoveryService.getInstanceInfo(instanceRef.toString())
        return if (eurekaInstance != null) {
            getRoute(eurekaInstance)
        } else {
            val dsAppInstance = discoveryService.getInstance(instanceRef)
            if (dsAppInstance == null) {
                null
            } else {
                getRoute(dsAppInstance)
            }
        }
    }

    private fun getRoute(instance: EcosServiceInstanceInfo): EcosWebRoute? {
        val route = EcosWebRoute(
            if (isLocalDevEnv) {
                LOCALHOST
            } else {
                instance.ip
            },
            AppInstanceRef.create(instance.appName, instance.appInstanceId),
            listOf(
                PortInfo(
                    instance.port, if (instance.securePortEnabled) {
                        PortType.HTTPS
                    } else {
                        PortType.HTTP
                    }
                )
            )
        )
        return if (route.getHttpPort() != null) {
            route
        } else {
            null
        }
    }

    private fun getRoute(instance: AppInstanceInfo): EcosWebRoute? {
        val route = EcosWebRoute(
            if (isLocalDevEnv) {
                LOCALHOST
            } else {
                instance.getIpAddress()
            },
            instance.getRef(),
            instance.getPorts()
        )
        return if (route.getHttpPort() != null) {
            route
        } else {
            null
        }
    }

    @Autowired
    fun setDiscoveryService(discoveryService: WebAppDiscoveryService) {
        this.discoveryService = discoveryService
    }

    @Autowired
    fun setAlfDiscoveryService(alfDiscoveryService: EcosAlfServiceDiscovery) {
        this.alfDiscoveryService = alfDiscoveryService
    }
}
