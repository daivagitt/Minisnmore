package com.miniandmore.store.client;

import com.miniandmore.store.config.ApplicationProperties;
import com.miniandmore.store.security.SecurityUtils;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CatalogInternalClient {

    private final RestClient restClient;

    public CatalogInternalClient(ApplicationProperties applicationProperties, RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl(applicationProperties.getCatalogBaseUrl()).build();
    }

    public ResponseEntity<String> get(String internalPath) {
        return exchange(HttpMethod.GET, internalPath, null, true);
    }

    public ResponseEntity<String> getPublic(String internalPath) {
        return exchange(HttpMethod.GET, internalPath, null, false);
    }

    public ResponseEntity<String> post(String internalPath, Object body) {
        return exchange(HttpMethod.POST, internalPath, body, true);
    }

    public ResponseEntity<String> postPublic(String internalPath, Object body) {
        return exchange(HttpMethod.POST, internalPath, body, false);
    }

    public ResponseEntity<String> put(String internalPath, Object body) {
        return exchange(HttpMethod.PUT, internalPath, body, true);
    }

    public ResponseEntity<String> exchange(HttpMethod method, String internalPath, Object body, boolean forwardAuth) {
        RestClient.RequestBodySpec spec = restClient.method(method).uri(internalPath).contentType(MediaType.APPLICATION_JSON);
        if (forwardAuth) {
            String authorization = SecurityUtils.currentBearerToken();
            if (authorization != null) {
                spec = spec.header(HttpHeaders.AUTHORIZATION, authorization);
            }
        }
        if (body != null) {
            spec = spec.body(body);
        }
        return spec.retrieve().toEntity(String.class);
    }

    public ResponseEntity<String> postEmpty(String internalPath) {
        return post(internalPath, Map.of());
    }
}
