package ru.citeck.ecos.webapp.web.client;

import lombok.RequiredArgsConstructor;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.AbstractClientHttpResponse;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import ru.citeck.ecos.commons.promise.Promises;
import ru.citeck.ecos.webapp.api.promise.Promise;
import ru.citeck.ecos.webapp.lib.web.client.http.EcosHttpClientBody;
import ru.citeck.ecos.webapp.lib.web.client.http.EcosHttpClientRequest;
import ru.citeck.ecos.webapp.lib.web.client.http.EcosHttpClientRequestExecution;
import ru.citeck.ecos.webapp.lib.web.client.http.EcosHttpClientResponse;
import ru.citeck.ecos.webapp.lib.web.client.interceptor.EcosHttpRequestInterceptor;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

@RequiredArgsConstructor
public class EcosHttpRequestInterceptorSpringAdapter implements ClientHttpRequestInterceptor {

    private final EcosHttpRequestInterceptor interceptor;

    @Override
    public ClientHttpResponse intercept(HttpRequest request,
                                        byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {

        SpringToEcosRequestAdapter ecosRequest = new SpringToEcosRequestAdapter(request);
        ByteArrayBody ecosBody = new ByteArrayBody(body);
        SpringToEcosExecutionAdapter ecosExecution = new SpringToEcosExecutionAdapter(execution);

        Promise<EcosHttpClientResponse> resp = interceptor.intercept(ecosRequest, ecosBody, ecosExecution);
        return new EcosToSpringRespAdapter(resp.get());
    }

    @RequiredArgsConstructor
    private static class SpringToEcosExecutionAdapter implements EcosHttpClientRequestExecution {

        private final ClientHttpRequestExecution execution;

        @NotNull
        @Override
        public Promise<EcosHttpClientResponse> execute(@NotNull EcosHttpClientRequest request,
                                                       @NotNull EcosHttpClientBody body) throws IOException {

            val resp = execution.execute(new EcosToSpringRequestAdapter(request), body.getBytes());
            return Promises.resolve(new SpringToEcosRespAdapter(resp));
        }
    }

    @RequiredArgsConstructor
    private static class EcosToSpringRespAdapter extends AbstractClientHttpResponse {

        private final EcosHttpClientResponse response;

        @Override
        public int getRawStatusCode() throws IOException {
            return response.getRawStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return response.getStatusText();
        }

        @Override
        public void close() {
            response.close();
        }

        @Override
        public InputStream getBody() throws IOException {
            return response.getBody();
        }

        @Override
        public HttpHeaders getHeaders() {
            HttpHeaders result = new HttpHeaders();
            result.putAll(response.getHeaders().getValues());
            return result;
        }
    }

    @RequiredArgsConstructor
    private static class SpringToEcosRespAdapter implements EcosHttpClientResponse {

        private final ClientHttpResponse response;

        @Override
        public void close() {
            response.close();
        }

        @NotNull
        @Override
        public InputStream getBody() throws IOException {
            return response.getBody();
        }

        @NotNull
        @Override
        public ru.citeck.ecos.webapp.lib.web.http.HttpHeaders getHeaders() {
            ru.citeck.ecos.webapp.lib.web.http.HttpHeaders headers = new ru.citeck.ecos.webapp.lib.web.http.HttpHeaders();
            headers.putAll(response.getHeaders());
            return headers;
        }

        @Override
        public int getRawStatusCode() throws IOException {
            return response.getRawStatusCode();
        }

        @NotNull
        @Override
        public String getStatusText() throws IOException {
            return response.getStatusText();
        }
    }

    @RequiredArgsConstructor
    private static class ByteArrayBody implements EcosHttpClientBody {

        @NotNull
        private final byte[] bytes;

        @NotNull
        @Override
        public byte[] getBytes() {
            return bytes;
        }
    }

    @RequiredArgsConstructor
    private static class EcosToSpringRequestAdapter implements HttpRequest {

        private final EcosHttpClientRequest request;

        @Override
        public HttpMethod getMethod() {
            return HttpMethod.valueOf(request.getMethodValue());
        }

        @Override
        public URI getURI() {
            return request.getUri();
        }

        @Override
        public HttpHeaders getHeaders() {
            HttpHeaders result = new HttpHeaders();
            result.putAll(request.getHeaders().getValues());
            return result;
        }
    }

    private static class SpringToEcosRequestAdapter implements EcosHttpClientRequest {

        private final HttpRequest request;
        private URI uri;

        public SpringToEcosRequestAdapter(HttpRequest request) {
            this.request = request;
            this.uri = request.getURI();
        }

        @NotNull
        @Override
        public ru.citeck.ecos.webapp.lib.web.http.HttpHeaders getHeaders() {
            ru.citeck.ecos.webapp.lib.web.http.HttpHeaders headers = new ru.citeck.ecos.webapp.lib.web.http.HttpHeaders();
            headers.putAll(request.getHeaders());
            return headers;
        }

        @NotNull
        @Override
        public String getMethodValue() {
            return request.getMethod().name();
        }

        @NotNull
        @Override
        public URI getUri() {
            return uri;
        }

        @Override
        public void setHeader(@NotNull String name, @NotNull String value) {
            request.getHeaders().set(name, value);
        }

        @Override
        public void setUri(@NotNull URI uri) {
            this.uri = uri;
        }
    }
}
