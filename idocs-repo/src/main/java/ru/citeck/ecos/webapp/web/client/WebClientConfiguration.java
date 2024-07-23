package ru.citeck.ecos.webapp.web.client;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;
import ru.citeck.ecos.http.SkipSslVerificationHttpRequestFactory;
import ru.citeck.ecos.webapp.lib.web.authenticator.WebAuthenticator;
import ru.citeck.ecos.webapp.lib.web.authenticator.WebAuthenticatorsManager;
import ru.citeck.ecos.webapp.lib.web.client.interceptor.EcosHttpRequestInterceptor;
import ru.citeck.ecos.webapp.lib.web.client.interceptor.WebClientRequestInterceptor;
import ru.citeck.ecos.webapp.lib.web.webapi.client.props.EcosWebClientProps;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Configuration
public class WebClientConfiguration {

    public static final String REST_TEMPLATE_ID = "eurekaRestTemplate";
    public static final String SECURED_REST_TEMPLATE_ID = "eurekaSecuredRestTemplate";

    @Autowired
    private EcosWebClientProps webClientProperties;
    @Autowired
    private WebAuthenticatorsManager authenticatorsManager;

    @Bean(name = REST_TEMPLATE_ID)
    public RestTemplate createRestTemplate() {
        return createNonSecuredRestTemplate();
    }

    // not really secured, but let's keep it for compatibility
    @Bean(name = SECURED_REST_TEMPLATE_ID)
    public RestTemplate createSecuredRestTemplate() {
        return createNonSecuredRestTemplate();
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
