package ru.citeck.ecos.http;

import org.springframework.http.client.SimpleClientHttpRequestFactory;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

public class SkipSslVerificationHttpRequestFactory extends SimpleClientHttpRequestFactory {

    @Override
    protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
        if (connection instanceof HttpsURLConnection) {
            HttpsURLConnection httpsConnection = (HttpsURLConnection) connection;
            try {
                httpsConnection.setHostnameVerifier((host, sslSession) -> true);
                httpsConnection.setSSLSocketFactory(createSslSocketFactory());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        super.prepareConnection(connection, httpMethod);
    }

    private SSLSocketFactory createSslSocketFactory() throws NoSuchAlgorithmException, KeyManagementException {
        SSLContext context = SSLContext.getInstance(TlsUtils.computeTlsProtocol());
        context.init(null, new TrustManager[]{new SkipX509TrustManager()}, new SecureRandom());
        return context.getSocketFactory();
    }

    private class SkipX509TrustManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

}
