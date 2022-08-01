package ru.citeck.ecos.webapp.web.client;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.ResourceUtils;
import org.springframework.web.client.RestTemplate;
import ru.citeck.ecos.http.SkipSslVerificationHttpRequestFactory;
import ru.citeck.ecos.webapp.lib.web.authenticator.WebAuthenticator;
import ru.citeck.ecos.webapp.lib.web.authenticator.WebAuthenticatorsManager;
import ru.citeck.ecos.webapp.lib.web.client.interceptor.EcosHttpRequestInterceptor;
import ru.citeck.ecos.webapp.lib.web.client.interceptor.WebClientRequestInterceptor;
import ru.citeck.ecos.webapp.lib.web.client.props.EcosWebClientProps;
import ru.citeck.ecos.webapp.lib.web.client.props.EcosWebClientTlsProps;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.io.InputStream;
import java.net.URL;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Configuration
public class WebClientConfiguration {

    public static final String REST_TEMPLATE_ID = "eurekaRestTemplate";
    public static final String SECURED_REST_TEMPLATE_ID = "eurekaSecuredRestTemplate";

    public static final String TRUST_STORE_NAME = "TrustStore";

    @Autowired
    private EcosWebClientProps webClientProperties;
    @Autowired
    private WebAuthenticatorsManager authenticatorsManager;

    @Bean(name = REST_TEMPLATE_ID)
    public RestTemplate createRestTemplate() {
        return createNonSecuredRestTemplate();
    }

    private void logTlsInfo(String msg) {
        log.info("[Records TLS] " + msg);
    }

    @Bean(name = SECURED_REST_TEMPLATE_ID)
    public RestTemplate createSecuredRestTemplate() throws Exception {

        EcosWebClientTlsProps tlsProps = webClientProperties.getTls();
        if (!tlsProps.getEnabled()) {
            logTlsInfo("TLS disabled. Secure SecureRestTemplate will be replaced by insecure.");
            return createNonSecuredRestTemplate();
        }
        logTlsInfo("TLS enabled. SecureRestTemplate initialization started.");

        SSLContextBuilder sslContextBuilder = SSLContextBuilder.create();

        if (StringUtils.isNotBlank(tlsProps.getTrustStore())) {

            KeyStore trustStore = loadKeyStore(
                TRUST_STORE_NAME,
                tlsProps.getTrustStore(),
                tlsProps.getTrustStorePassword(),
                tlsProps.getTrustStoreType()
            );

            sslContextBuilder.loadTrustMaterial(trustStore, null);

        } else {

            logTlsInfo("Custom " + TRUST_STORE_NAME + " doesn't defined. Default will be used.");
        }

        HostnameVerifier hostnameVerifier = SSLConnectionSocketFactory.getDefaultHostnameVerifier();
        if (!tlsProps.getVerifyHostname()) {
            hostnameVerifier = NoopHostnameVerifier.INSTANCE;
            logTlsInfo("Hostname verification is disabled");
        } else {
            logTlsInfo("Hostname verification is enabled");
        }

        SSLContext sslContext = sslContextBuilder.build();
        SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(sslContext, hostnameVerifier);

        CloseableHttpClient httpClient = HttpClients.custom()
            .setSSLSocketFactory(socketFactory)
            .setSSLHostnameVerifier(hostnameVerifier)
            .build();

        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);

        RestTemplate template = new RestTemplate(factory);
        setupRestTemplate(template);

        return template;
    }

    @SneakyThrows
    private KeyStore loadKeyStore(String name, String path, String password, String type) {

        logTlsInfo("Start loading " + name + " with type $type by path: " + path);
        URL url = ResourceUtils.getURL(path);
        logTlsInfo(name + " URL:" + url);

        try (InputStream in = url.openStream()) {
            KeyStore keyStore = KeyStore.getInstance(type);
            keyStore.load(in, password != null ? password.toCharArray() : null);
            logTlsInfo(name + " loading finished. Entries size: " + keyStore.size());
            return keyStore;
        }
    }

    @NotNull
    private RestTemplate createNonSecuredRestTemplate() {
        RestTemplate template = new RestTemplate(new SkipSslVerificationHttpRequestFactory());
        setupRestTemplate(template);
        return template;
    }

    private void setupRestTemplate(RestTemplate template) {

        List<EcosHttpRequestInterceptor> interceptors = new ArrayList<>();

        WebAuthenticator authenticator = null;
        if (StringUtils.isNotBlank(webClientProperties.getAuthenticator())) {
            authenticator = authenticatorsManager.getAuthenticator(webClientProperties.getAuthenticator());
        }

        interceptors.add(new WebClientRequestInterceptor(authenticator));

        List<ClientHttpRequestInterceptor> restInterceptors = new ArrayList<>(template.getInterceptors());
        restInterceptors.addAll(interceptors.stream()
            .map(EcosHttpRequestInterceptorSpringAdapter::new)
            .collect(Collectors.toList()));

        template.setInterceptors(restInterceptors);
    }
}
