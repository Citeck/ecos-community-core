package ru.citeck.ecos.http;

import org.jetbrains.annotations.NotNull;

import javax.net.ssl.SSLContext;
import java.security.NoSuchAlgorithmException;

public class TlsUtils {

    private static final String TLS_1_2_PROTOCOL = "TLSv1.2";
    private static final String TLS_1_0_PROTOCOL = "TLSv1";

    @NotNull
    public static String computeTlsProtocol() throws NoSuchAlgorithmException {
        boolean isTls12Supported = false;
        boolean isTls10Supported = false;

        String[] supportedProtocols = SSLContext.getDefault().getSupportedSSLParameters().getProtocols();
        if (supportedProtocols != null) {
            for (String supportedProtocol : supportedProtocols) {
                if (TLS_1_2_PROTOCOL.equalsIgnoreCase(supportedProtocol)) {
                    isTls12Supported = true;
                }
                if (TLS_1_0_PROTOCOL.equalsIgnoreCase(supportedProtocol)) {
                    isTls10Supported = true;
                }
            }
        }

        if (isTls12Supported) {
            return TLS_1_2_PROTOCOL;
        }

        if (isTls10Supported) {
            return TLS_1_0_PROTOCOL;
        }

        return "SSL";
    }

}
