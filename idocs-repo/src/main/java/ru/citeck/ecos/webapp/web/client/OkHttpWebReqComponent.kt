package ru.citeck.ecos.webapp.web.client

import mu.KotlinLogging
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okio.BufferedSink
import org.apache.http.conn.ssl.NoopHostnameVerifier
import ru.citeck.ecos.webapp.lib.web.client.props.EcosWebClientProps
import ru.citeck.ecos.webapp.lib.web.client.props.EcosWebClientTlsProps
import ru.citeck.ecos.webapp.lib.web.webapi.client.EcosWebReqComponent
import ru.citeck.ecos.webapp.web.client.tls.EcosTlsUtils
import ru.citeck.ecos.webapp.web.client.tls.TrustAllX509TrustManager
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.RuntimeException
import java.net.URL
import java.security.SecureRandom
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class OkHttpWebReqComponent(props: EcosWebClientProps) : EcosWebReqComponent {

    companion object {
        private const val TRUST_STORE_NAME = "TrustStore"
        private const val KEY_STORE_NAME = "KeyStore"

        private val log = KotlinLogging.logger {}
    }

    private val insecureClient: OkHttpClient
    private val secureClient: OkHttpClient

    init {
        var builder = OkHttpClient.Builder()
            .connectTimeout(props.connectTimeout)
            .readTimeout(props.readTimeout)
            .writeTimeout(props.writeTimeout)

        builder = setupTlsProps(builder, props.tls)

        val baseClient: OkHttpClient = builder.build()
        baseClient.dispatcher.maxRequestsPerHost = 16

        if (props.tls.enabled) {

            secureClient = baseClient
            insecureClient = setupTlsProps(
                baseClient.newBuilder(),
                EcosWebClientTlsProps.create { withEnabled(false) }
            ).build()
        } else {

            traceTlsInfo { "TLS disabled. Secure client will be replaced by insecure." }

            secureClient = baseClient
            insecureClient = baseClient
        }
    }

    private fun setupTlsProps(
        builder: OkHttpClient.Builder,
        tlsProps: EcosWebClientTlsProps
    ): OkHttpClient.Builder {

        return if (!tlsProps.enabled) {

            val context = SSLContext.getInstance("TLS")
            val trustManager = TrustAllX509TrustManager()
            context.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
            builder.sslSocketFactory(context.socketFactory, trustManager)
                .hostnameVerifier(NoopHostnameVerifier.INSTANCE)
        } else {

            logTlsInfo { "TLS enabled. Secure client initialization started." }

            if (!tlsProps.verifyHostname) {
                builder.hostnameVerifier(NoopHostnameVerifier.INSTANCE)
                logTlsInfo { "Hostname verification is disabled" }
            } else {
                logTlsInfo { "Hostname verification is enabled" }
            }

            val trustManager: X509TrustManager = if (tlsProps.trustStore.isNotBlank()) {
                val trustStore = EcosTlsUtils.loadKeyStore(
                    TRUST_STORE_NAME,
                    tlsProps.trustStore,
                    tlsProps.trustStorePassword,
                    tlsProps.trustStoreType
                )
                EcosTlsUtils.getX509TrustManager(trustStore)
            } else {
                logTlsInfo { "Custom $TRUST_STORE_NAME doesn't defined. Default will be used." }
                EcosTlsUtils.getX509TrustManager(null)
            }

            val keyManager: KeyManager? = if (tlsProps.keyStore.isNotBlank()) {
                val keyStore = EcosTlsUtils.loadKeyStore(
                    KEY_STORE_NAME,
                    tlsProps.keyStore,
                    tlsProps.keyStorePassword,
                    tlsProps.keyStoreType
                )
                EcosTlsUtils.getX509KeyManager(keyStore, tlsProps.keyStorePassword)
            } else {
                null
            }

            val context = SSLContext.getInstance("TLS")
            context.init(keyManager?.let { arrayOf(it) }, arrayOf(trustManager), SecureRandom())
            builder.sslSocketFactory(context.socketFactory, trustManager)

            builder
        }
    }

    override fun execute(
        url: URL,
        reqContent: (OutputStream) -> Unit,
        onResponse: (InputStream) -> Unit,
        onFailure: (Throwable) -> Unit
    ) {

        val requestBody = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun writeTo(sink: BufferedSink) {
                reqContent.invoke(sink.outputStream())
            }
        }

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        val client = if (url.protocol == "https") {
            secureClient
        } else {
            insecureClient
        }

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onFailure.invoke(e)
            }
            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    try {
                        if (!resp.isSuccessful) {
                            onFailure.invoke(RuntimeException("Unexpected response code: ${resp.code}"))
                        } else {
                            val bytesResp = resp.body?.byteStream()
                            if (bytesResp == null) {
                                onFailure.invoke(RuntimeException("Body is null"))
                            } else {
                                onResponse.invoke(bytesResp)
                            }
                        }
                    } catch (e: Throwable) {
                        onFailure(RuntimeException("Unexpected error", e))
                    }
                }
            }
        })
    }

    private fun logTlsInfo(msg: () -> String) {
        log.info { "[WebClient TLS] ${msg.invoke()}" }
    }

    private fun traceTlsInfo(msg: () -> String) {
        log.trace { "[WebClient TLS] ${msg.invoke()}" }
    }
}
