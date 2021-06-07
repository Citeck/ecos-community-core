package ru.citeck.ecos.remote;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.CommonsClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.social.support.URIBuilder;
import org.springframework.web.client.RestTemplate;
import ru.citeck.ecos.utils.json.mixin.NodeRefMixIn;
import ru.citeck.ecos.utils.json.mixin.QNameMixIn;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class RestConnection {

    private static final Log logger = LogFactory.getLog(RestConnection.class);

    private RestTemplate restTemplate;

    private String username;
    private String password;

    private String host;

    private long timeoutMs = 5 * 1000 * 60;

    private boolean enabled = false;
    private boolean initialized = false;

    private ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public synchronized void init() {

        if (initialized || !enabled) {
            return;
        }

        if (StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password)) {
            this.restTemplate = new RestTemplate(createSecureTransport(username, password));
        } else {
            this.restTemplate = new RestTemplate();
        }

        objectMapper.addMixInAnnotations(NodeRef.class, NodeRefMixIn.class);
        objectMapper.addMixInAnnotations(QName.class, QNameMixIn.class);

        List<HttpMessageConverter<?>> messageConverters = new ArrayList<>();

        messageConverters.add(new StringHttpMessageConverter(StandardCharsets.UTF_8));

        MappingJackson2HttpMessageConverter jsonMessageConverter = new MappingJackson2HttpMessageConverter();
        jsonMessageConverter.setObjectMapper(objectMapper);
        messageConverters.add(jsonMessageConverter);

        restTemplate.setMessageConverters(messageConverters);

        initialized = true;
    }

    private ClientHttpRequestFactory createSecureTransport(String username, String password) {
        HttpClient client = new HttpClient();
        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(username, password);
        client.getState().setCredentials(AuthScope.ANY, credentials);
        client.getParams().setConnectionManagerTimeout(timeoutMs);
        return new CommonsClientHttpRequestFactory(client);
    }

    public <O> O jsonPost(String strUrl, Object postData, Class<O> responseType) {
        if (!initialized || !enabled) {
            logger.warn("Rest connection is not initialized or disabled! " +
                        "Return null for query " + strUrl + " with data: " + postData + ". " +
                        "Host: " + host);
            return null;
        }
        String hostUrl;
        if (!strUrl.startsWith("/")) {
            hostUrl = host + "/" + strUrl;
        } else {
            hostUrl = host + strUrl;
        }
        URI uri = URIBuilder.fromUri(hostUrl).build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<?> requestEntity = new HttpEntity<>(postData, headers);
        try {
            return restTemplate.postForObject(uri, requestEntity, responseType);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public RestTemplate getRestTemplate() {
        return restTemplate;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        if (enabled) {
            init();
        }
    }

    @Override
    public String toString() {
        return "RestConnection{" +
                "host='" + host + '\'' +
                ", enabled=" + enabled +
                '}';
    }
}
