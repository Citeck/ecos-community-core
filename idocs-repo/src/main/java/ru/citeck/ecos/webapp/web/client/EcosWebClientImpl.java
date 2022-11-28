package ru.citeck.ecos.webapp.web.client;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.commons.promise.Promises;
import ru.citeck.ecos.webapp.api.promise.Promise;
import ru.citeck.ecos.webapp.api.web.EcosWebClient;
import ru.citeck.ecos.webapp.api.web.exception.BadResponseException;
import ru.citeck.ecos.webapp.api.web.exception.EcosWebException;
import ru.citeck.ecos.webapp.api.web.exception.InvalidPathException;
import ru.citeck.ecos.webapp.lib.web.client.router.EcosWebRoute;
import ru.citeck.ecos.webapp.lib.web.client.router.EcosWebRouter;
import ru.citeck.ecos.webapp.lib.web.http.HttpHeaders;
import ru.citeck.ecos.webapp.web.client.records.RestRequestEntity;
import ru.citeck.ecos.webapp.web.client.records.RestResponseEntity;

import java.util.concurrent.CompletableFuture;

@Component
public class EcosWebClientImpl implements EcosWebClient {

    @Autowired
    @Qualifier(WebClientConfiguration.REST_TEMPLATE_ID)
    private RestTemplate eurekaRestTemplate;

    @Autowired
    @Qualifier(WebClientConfiguration.SECURED_REST_TEMPLATE_ID)
    private RestTemplate eurekaSecuredRestTemplate;

    @Autowired
    private EcosWebRouter webRouter;

    @NotNull
    @Override
    public <R> Promise<R> execute(
        @NotNull String targetApp,
        @NotNull String path,
        int version,
        @NotNull Object request,
        @NotNull Class<R> respType
    ) {
        if (!path.startsWith("/")) {
            return Promises.reject(new InvalidPathException(targetApp, path));
        }
        CompletableFuture<R> result = new CompletableFuture<>();
        if (path.startsWith("/records/")) {
            try {
                result.complete(recordsJsonPost(targetApp, path, request, respType));
            } catch (Exception e) {
                result.completeExceptionally(new EcosWebException(targetApp, path, e));
            }
        } else {
            result.completeExceptionally(new InvalidPathException(targetApp, path));
        }
        return Promises.create(result);
    }

    @Override
    public int getApiVersion(@NotNull String targetApp, @NotNull String path) {
        return 0;
    }

    private <T> T recordsJsonPost(String appName, String path, Object request, Class<T> respType) {

        RestRequestEntity requestEntity = new RestRequestEntity();
        requestEntity.setBody(Json.getMapper().toBytesNotNull(request));

        HttpHeaders headers = new HttpHeaders();
        headers.put(HttpHeaders.CONTENT_TYPE, "application/json;charset=UTF-8");

        String scheme = "http";
        EcosWebRoute route = webRouter.getRoute(appName);
        if (route.getSecure()) {
            scheme += "s";
        }
        String recordsUrl = scheme + "://" + route.getHost() + ":" + route.getPort() + "/api" + path;

        requestEntity.setHeaders(headers);

        RestResponseEntity responseEntity = recordsJsonPost(recordsUrl, requestEntity);
        byte[] body = responseEntity.getBody();
        if (body == null || responseEntity.getStatus() != 200) {
            throw new BadResponseException(appName, path, responseEntity.getStatus(), body);
        }
        return Json.getMapper().readNotNull(body, respType);
    }

    private RestResponseEntity recordsJsonPost(String url, RestRequestEntity request) {

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        request.getHeaders().forEach(headers::put);

        HttpEntity<byte[]> httpEntity = new HttpEntity<>(request.getBody(), headers);
        RestTemplate restTemplate;
        if (url.startsWith("https")) {
            restTemplate = this.eurekaSecuredRestTemplate;
        } else {
            restTemplate = this.eurekaRestTemplate;
        }
        ResponseEntity<byte[]> result = restTemplate.exchange(url, HttpMethod.POST, httpEntity, byte[].class);

        RestResponseEntity resultEntity = new RestResponseEntity();
        resultEntity.setBody(result.getBody());
        resultEntity.setStatus(result.getStatusCode().value());
        result.getHeaders().forEach((k, v) -> resultEntity.getHeaders().put(k, v));

        return resultEntity;
    }
}
