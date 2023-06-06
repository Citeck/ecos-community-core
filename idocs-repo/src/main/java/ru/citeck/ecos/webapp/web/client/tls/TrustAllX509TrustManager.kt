package ru.citeck.ecos.webapp.web.client.tls

import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

class TrustAllX509TrustManager : X509TrustManager {
    override fun getAcceptedIssuers(): Array<X509Certificate> {
        return emptyArray()
    }
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
}
