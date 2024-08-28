package ru.citeck.ecos.webapp.discovery

import mu.KotlinLogging
import org.apache.commons.lang.StringUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.buildinfo.dto.BuildInfo
import ru.citeck.ecos.apps.app.domain.buildinfo.service.BuildInfoProvider
import ru.citeck.ecos.commons.data.Version
import ru.citeck.ecos.utils.InetUtils
import ru.citeck.ecos.webapp.api.properties.EcosWebAppProps
import ru.citeck.ecos.webapp.lib.discovery.WebAppMainInfo
import ru.citeck.ecos.webapp.lib.discovery.instance.AppInstanceRef
import ru.citeck.ecos.webapp.lib.discovery.instance.PortInfo
import ru.citeck.ecos.webapp.lib.discovery.instance.PortType
import ru.citeck.ecos.webapp.lib.env.EcosWebAppEnvironment
import ru.citeck.ecos.webapp.lib.utils.EcosEnvUtils
import java.time.Instant
import java.util.*
import java.util.function.Supplier

@Component
class DiscoveryInfoProvider {

    companion object {
        const val ENV_PROP_SHOULD_REGISTER: String = "ECOS_EUREKA_REGISTRATION_ENABLED"

        const val CONFIG_PREFIX: String = "ecos.eureka."
        const val APP_NAME_ALF_SHARE = "alfshare"

        private val log = KotlinLogging.logger {}
    }

    @Autowired
    @Qualifier("global-properties")
    private lateinit var globalProperties: Properties
    @Autowired
    private lateinit var env: EcosWebAppEnvironment
    @Autowired
    private lateinit var inetUtils: InetUtils
    @Autowired
    private lateinit var webappProps: EcosWebAppProps
    @Autowired
    private lateinit var buildInfoProvider: BuildInfoProvider

    private val alfrescoInfoField by lazy { evalAppInfo(AppType.ALFRESCO) }
    private val shareInfoField by lazy { evalAppInfo(AppType.SHARE) }

    fun isRegistrationEnabled(): Boolean {
        val shouldRegisterFromEnv = System.getenv(ENV_PROP_SHOULD_REGISTER)
        if (StringUtils.isNotBlank(shouldRegisterFromEnv)) {
            return shouldRegisterFromEnv == "true"
        }
        return getBoolParam("registration.enabled") { true }
    }

    fun getAlfrescoInfo(): WebAppMainInfo {
        return alfrescoInfoField
    }

    fun getShareInfo(): Pair<AppInstanceRef, WebAppMainInfo> {
        val shareAppRef = AppInstanceRef.create(
            APP_NAME_ALF_SHARE,
            webappProps.appInstanceId
        )
        return shareAppRef to shareInfoField
    }

    private fun evalAppInfo(appType: AppType): WebAppMainInfo {

        var priority = 0L
        if (env.acceptsProfiles("dev_local")) {
            priority = 1L
        }

        val buildInfo = getBuildInfo()

        var version: Version = Version.valueOf("1")
        var buildDate = Instant.EPOCH
        if (buildInfo != null) {
            version = Version.valueOf(buildInfo.version)
            buildDate = buildInfo.buildDate
        }

        return WebAppMainInfo(
            priority,
            version,
            buildDate,
            Instant.now(),
            getHost(appType),
            listOf(getPort(appType)),
            getIpAddress(appType)
        )
    }

    private fun getHost(appType: AppType): String {
        var host = System.getenv(appType.envHost)
        if (StringUtils.isBlank(host)) {
            if (appType != AppType.ALFRESCO) {
                host = getHost(AppType.ALFRESCO)
            } else {
                host = getStrParam("host") {
                    getGlobalStrParam("alfresco.host") { "localhost" }
                }
                if ("localhost" == host || "127.0.0.1" == host) {
                    host = getIpAddress(appType)
                }
            }
        }
        return host
    }

    private fun getIpAddress(appType: AppType): String {
        val envValue = System.getenv(appType.envIp)
        if (StringUtils.isNotEmpty(envValue)) {
            return envValue
        }
        return if (appType != AppType.ALFRESCO) {
            getIpAddress(AppType.ALFRESCO)
        } else {
            getStrParam("instance.ip") {
                val isDev = getGlobalBoolParam("ecos.environment.dev") { false }
                if (isDev && (EcosEnvUtils.isOsMac() || EcosEnvUtils.isOsWindows())) {
                    "host.docker.internal"
                } else {
                    inetUtils.findFirstNonLoopbackHostInfo().ipAddress.ifBlank { "127.0.0.1" }
                }
            }
        }
    }

    private fun getPort(appType: AppType): PortInfo {

        val portFromEnv = System.getenv(appType.envPort)

        var port = -1
        if (portFromEnv != null) {
            try {
                port = portFromEnv.toInt()
            } catch (e: NumberFormatException) {
                log.warn("Incorrect port in " + appType.envPort + " param. Value: " + portFromEnv)
            }
        }
        if (port == -1 && appType != AppType.ALFRESCO) {
            return getPort(AppType.ALFRESCO)
        }
        if (port == -1) {
            port = getIntParam("port") {
                getGlobalIntParam("alfresco.port") { 8080 }
            }
        }
        return PortInfo(port, PortType.HTTP)
    }

    private fun getBuildInfo(): BuildInfo? {
        val infos: List<BuildInfo> = buildInfoProvider.getBuildInfo()
        if (infos.isEmpty()) {
            return null
        }
        var lastInfo = infos[0]
        for (i in 1 until infos.size) {
            val info = infos[i]
            if (info.buildDate.isAfter(lastInfo.buildDate)) {
                lastInfo = info
            }
        }
        return lastInfo
    }

    protected fun getBoolParam(localKey: String, orElse: Supplier<Boolean>): Boolean {
        return getGlobalBoolParam(CONFIG_PREFIX + localKey, orElse)
    }

    protected fun getGlobalBoolParam(globalKey: String, orElse: Supplier<Boolean>): Boolean {
        val result = globalProperties.getProperty(globalKey) ?: return orElse.get()
        return java.lang.Boolean.TRUE.toString() == result
    }

    protected fun getStrParam(localKey: String, orElse: Supplier<String>): String {
        return getGlobalStrParam(CONFIG_PREFIX + localKey, orElse)
    }

    protected fun getGlobalStrParam(globalKey: String, orElse: Supplier<String>): String {
        var result = globalProperties.getProperty(globalKey)
        if (result == null) {
            result = orElse.get()
        }
        return result
    }

    protected fun getGlobalIntParam(globalKey: String, orElse: Supplier<Int>): Int {
        val result = globalProperties.getProperty(globalKey) ?: return orElse.get()
        return result.toInt()
    }

    protected fun getIntParam(localKey: String, orElse: Supplier<Int>): Int {
        return getGlobalIntParam(CONFIG_PREFIX + localKey, orElse)
    }

    private enum class AppType(
        val envPort: String,
        val envHost: String,
        val envIp: String
    ) {
        SHARE(
            envPort = "ECOS_EUREKA_INSTANCE_SHARE_PORT",
            envHost = "ECOS_EUREKA_INSTANCE_SHARE_HOST",
            envIp = "ECOS_EUREKA_INSTANCE_SHARE_IP"
        ),
        ALFRESCO(
            envPort = "ECOS_EUREKA_INSTANCE_PORT",
            envHost = "ECOS_EUREKA_INSTANCE_HOST",
            envIp = "ECOS_EUREKA_INSTANCE_IP"
        )
    }
}
