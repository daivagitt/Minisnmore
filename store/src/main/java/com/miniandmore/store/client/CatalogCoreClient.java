package com.miniandmore.store.client;

import com.miniandmore.store.config.ApplicationProperties;
import com.miniandmore.store.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.LinkedMultiValueMap;

@Component
public class CatalogCoreClient {

    private static final String LEDGERLY_ADMIN_PREFIX = "/api/v1/admin/ledgerly";

    private final RestClient restClient;
    private final String catalogBaseUrl;

    public CatalogCoreClient(ApplicationProperties applicationProperties, RestClient.Builder restClientBuilder) {
        this.catalogBaseUrl = applicationProperties.getCatalogBaseUrl();
        this.restClient = restClientBuilder.baseUrl(catalogBaseUrl).build();
    }

    /**
     * Forward a generic servlet request to catalog core, translating the path:
     * - /api/v1/admin/ledgerly/** → /api/v1/admin/ledgerly/** (kept as-is; lives on catalog)
     * - /api/v1/** → /internal/v1/** (normal store-to-catalog internal path)
     */
    public ResponseEntity<byte[]> forward(HttpServletRequest request) throws IOException {
        String requestPath = request.getRequestURI();
        String catalogPath = toCatalogPath(requestPath);

        String queryString = request.getQueryString();
        String fullPath = queryString != null ? catalogPath + "?" + queryString : catalogPath;

        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        RestClient.RequestBodySpec spec = restClient
            .method(method)
            .uri(URI.create(catalogBaseUrl + fullPath))
            .contentType(MediaType.APPLICATION_JSON);

        String auth = SecurityUtils.currentBearerToken();
        if (auth != null) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, auth);
        }

        byte[] body = request.getInputStream().readAllBytes();
        if (body.length > 0) {
            spec = spec.body(body);
        }

        return spec.retrieve().toEntity(byte[].class);
    }

    /**
     * Forward a multipart upload to catalog core's admin uploads endpoint.
     */
    public ResponseEntity<byte[]> forwardMultipartUpload(String folder, MultipartFile file) throws IOException {
        LinkedMultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("folder", folder);
        parts.add("file", new org.springframework.core.io.ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        });

        RestClient.RequestBodySpec spec = restClient
            .post()
            .uri("/internal/v1/admin/uploads")
            .contentType(MediaType.MULTIPART_FORM_DATA);

        String auth = SecurityUtils.currentBearerToken();
        if (auth != null) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, auth);
        }

        return spec.body(parts).retrieve().toEntity(byte[].class);
    }

    private static String toCatalogPath(String requestPath) {
        if (requestPath.startsWith(LEDGERLY_ADMIN_PREFIX)) {
            return requestPath;
        }
        if (requestPath.startsWith("/api/v1/")) {
            return "/internal/v1/" + requestPath.substring("/api/v1/".length());
        }
        return requestPath;
    }
}
