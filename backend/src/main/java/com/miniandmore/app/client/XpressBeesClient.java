package com.miniandmore.app.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.config.ApplicationProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class XpressBeesClient {

    private static final Logger LOG = LoggerFactory.getLogger(XpressBeesClient.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final String USER_AGENT = "MiniSnMore/1.0 (+https://minisnmore.com)";

    private final ApplicationProperties.XpressBees config;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    public XpressBeesClient(ApplicationProperties applicationProperties, ObjectMapper objectMapper) {
        this.config = applicationProperties.getXpressbees();
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient
            .newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    }

    public boolean isConfigured() {
        return (
            StringUtils.isNotBlank(config.getEmail()) &&
            StringUtils.isNotBlank(config.getPassword()) &&
            StringUtils.isNotBlank(config.getOriginPincode())
        );
    }

    public Optional<ServiceabilityResult> checkServiceability(String destinationPincode) {
        if (!isConfigured() || destinationPincode == null || !destinationPincode.matches("^[1-9]\\d{5}$")) {
            return Optional.empty();
        }
        try {
            String token = accessToken();
            if (token == null) {
                return Optional.empty();
            }
            String body = objectMapper.writeValueAsString(
                java.util.Map.of(
                    "origin",
                    config.getOriginPincode(),
                    "destination",
                    destinationPincode,
                    "payment_type",
                    StringUtils.defaultIfBlank(config.getPaymentType(), "prepaid")
                )
            );
            String url =
                config.getBaseUrl().replaceAll("/$", "") +
                "/api/courier/serviceability/pincode/" +
                destinationPincode;
            var request = HttpRequest
                .newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .header("User-Agent", USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                LOG.debug("XpressBees serviceability HTTP {} for pincode {}", response.statusCode(), destinationPincode);
                return Optional.empty();
            }
            return parseServiceabilityResponse(response.body(), destinationPincode);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception ex) {
            LOG.debug("XpressBees serviceability failed for pincode {}: {}", destinationPincode, ex.getMessage());
            return Optional.empty();
        }
    }

    Optional<ServiceabilityResult> parseServiceabilityResponse(String body, String destinationPincode) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (!root.path("status").asBoolean(false)) {
                return Optional.empty();
            }
            JsonNode data = root.path("data");
            if (data.isTextual()) {
                return Optional.empty();
            }
            if (!data.isArray() || data.isEmpty()) {
                return Optional.empty();
            }
            JsonNode best = data.get(0);
            int minEddDays = Integer.MAX_VALUE;
            for (JsonNode option : data) {
                int eddDays = option.path("eddDays").asInt(Integer.MAX_VALUE);
                if (eddDays < minEddDays) {
                    minEddDays = eddDays;
                    best = option;
                }
            }
            String city = text(best, "destination_city");
            String state = text(best, "destination_state");
            if (city.isBlank() || state.isBlank()) {
                return Optional.empty();
            }
            int eddDays = best.path("eddDays").asInt(-1);
            return Optional.of(new ServiceabilityResult(city, state, formatEta(eddDays), eddDays, destinationPincode));
        } catch (Exception ex) {
            LOG.debug("Could not parse XpressBees serviceability response for {}: {}", destinationPincode, ex.getMessage());
            return Optional.empty();
        }
    }

    static String formatEta(int eddDays) {
        if (eddDays <= 0) {
            return "Same day";
        }
        if (eddDays == 1) {
            return "1 day";
        }
        return eddDays + " days";
    }

    private String accessToken() throws Exception {
        Instant now = Instant.now();
        if (cachedToken != null && now.isBefore(tokenExpiresAt.minusSeconds(300))) {
            return cachedToken;
        }
        synchronized (this) {
            now = Instant.now();
            if (cachedToken != null && now.isBefore(tokenExpiresAt.minusSeconds(300))) {
                return cachedToken;
            }
            String loginBody = objectMapper.writeValueAsString(
                java.util.Map.of("email", config.getEmail(), "password", config.getPassword())
            );
            String loginUrl = config.getBaseUrl().replaceAll("/$", "") + "/api/users/login";
            var request = HttpRequest
                .newBuilder()
                .uri(URI.create(loginUrl))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofString(loginBody))
                .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                LOG.warn("XpressBees login failed with HTTP {}", response.statusCode());
                return null;
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (!root.path("status").asBoolean(false)) {
                LOG.warn("XpressBees login rejected");
                return null;
            }
            String token = root.path("data").asText("");
            if (token.isBlank()) {
                return null;
            }
            cachedToken = token;
            tokenExpiresAt = jwtExpiry(token).orElse(now.plus(Duration.ofHours(2)));
            return cachedToken;
        }
    }

    private Optional<Instant> jwtExpiry(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return Optional.empty();
            }
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JsonNode payload = objectMapper.readTree(payloadJson);
            long exp = payload.path("exp").asLong(0L);
            if (exp <= 0L) {
                return Optional.empty();
            }
            return Optional.of(Instant.ofEpochSecond(exp));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("").trim();
    }

    public record ServiceabilityResult(String city, String state, String eta, int eddDays, String pincode) {}
}
