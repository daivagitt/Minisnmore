package com.miniandmore.commerce.client;

import com.miniandmore.commerce.config.ApplicationProperties;
import com.miniandmore.commerce.security.SecurityUtils;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class CatalogInternalClient {

    private final RestClient restClient;

    public CatalogInternalClient(ApplicationProperties applicationProperties, RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl(applicationProperties.getCatalogBaseUrl()).build();
    }

    public static String encodedPath(String template, Object... uriVariables) {
        return UriComponentsBuilder.fromPath(template).buildAndExpand(uriVariables).encode().toUriString();
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
        RestClient.RequestBodySpec spec = restClient.method(method).uri(toUri(internalPath)).contentType(MediaType.APPLICATION_JSON);
        if (forwardAuth) {
            String authorization = SecurityUtils.currentBearerToken();
            if (authorization != null) {
                spec = spec.header(HttpHeaders.AUTHORIZATION, authorization);
            }
        }
        if (body != null) {
            spec = spec.body(body);
        }
        return spec.exchange((request, response) -> {
            byte[] bytes = response.getBody().readAllBytes();
            String text = bytes.length == 0 ? null : new String(bytes, StandardCharsets.UTF_8);
            HttpHeaders headers = new HttpHeaders();
            response.getHeaders().forEach(headers::addAll);
            return ResponseEntity.status(response.getStatusCode()).headers(headers).body(text);
        });
    }

    public ResponseEntity<String> postEmpty(String internalPath) {
        return post(internalPath, Map.of());
    }

    private static URI toUri(String internalPath) {
        int queryIndex = internalPath.indexOf('?');
        if (queryIndex >= 0) {
            String path = internalPath.substring(0, queryIndex);
            String query = internalPath.substring(queryIndex + 1);
            return UriComponentsBuilder.fromPath(path).query(query).build(true).toUri();
        }
        return UriComponentsBuilder.fromPath(internalPath).build(true).toUri();
    }
}
