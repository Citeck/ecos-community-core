package ru.citeck.ecos.webapp.web.client.tls

import mu.KotlinLogging
import org.springframework.util.ResourceUtils
import java.security.KeyStore
import java.util.*
import javax.net.ssl.*

object EcosTlsUtils {

    private val log = KotlinLogging.logger {}

    @JvmStatic
    fun loadKeyStore(name: String, path: String, password: CharArray?, type: String): KeyStore {

        debugTlsInfo { "Start loading $name with type $type by path: $path" }
        val url = ResourceUtils.getURL(path)
        debugTlsInfo { "$name URL: $url" }

        return url.openStream().use {
            val keyStore = KeyStore.getInstance(type)
            keyStore.load(it, password)
            debugTlsInfo { "$name loading finished. Entries size: ${keyStore.size()}" }
            keyStore
        }
    }

    @JvmStatic
    fun getX509TrustManager(keyStore: KeyStore?): X509TrustManager {
        val algorithm: String = TrustManagerFactory.getDefaultAlgorithm()
        val trustManagerFactory = TrustManagerFactory.getInstance(algorithm)
        trustManagerFactory.init(keyStore)
        val trustManagers = trustManagerFactory.trustManagers
        if (trustManagers.size != 1 || trustManagers[0] !is X509TrustManager) {
            throw IllegalStateException("Unexpected trust managers: " + Arrays.toString(trustManagers))
        }
        return trustManagers[0] as X509TrustManager
    }

    @JvmStatic
    fun getX509KeyManager(keyStore: KeyStore, password: CharArray?): X509KeyManager {
        val algorithm: String = KeyManagerFactory.getDefaultAlgorithm()
        val keyManagerFactory = KeyManagerFactory.getInstance(algorithm)
        keyManagerFactory.init(keyStore, password)
        val keyManagers = keyManagerFactory.keyManagers
        if (keyManagers.size != 1 || keyManagers[0] !is X509KeyManager) {
            throw IllegalStateException("Unexpected key managers: " + Arrays.toString(keyManagers))
        }
        return keyManagers[0] as X509KeyManager
    }

    private fun debugTlsInfo(msg: () -> String) {
        log.info { "[TLS Utils] ${msg.invoke()}" }
    }
}
