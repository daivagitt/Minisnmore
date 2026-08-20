package com.miniandmore.app.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.config.ApplicationProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Client for FastSMS India SMS API v1.0 ({@code https://s.fastsmsindia.com/api/sms/v1.0/{{request-for}}}).
 */
@Component
public class FastSmsClient {

    private static final Logger LOG = LoggerFactory.getLogger(FastSmsClient.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final String USER_AGENT = "MiniSnMore/1.0 (+https://minisnmore.com)";

    private final ApplicationProperties.FastSms config;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public FastSmsClient(ApplicationProperties applicationProperties, ObjectMapper objectMapper) {
        this.config = applicationProperties.getFastsms();
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient
            .newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    }

    public boolean isConfigured() {
        return (
            config.isEnabled() &&
            StringUtils.isNotBlank(config.getAccessToken()) &&
            StringUtils.isNotBlank(config.getAccessTokenKey()) &&
            StringUtils.isNotBlank(config.getSmsHeader()) &&
            StringUtils.isNotBlank(config.getRoute())
        );
    }

    /**
     * Send a single SMS via {@code send-sms}.
     *
     * @param mobile10Digit Indian mobile without country code (10 digits)
     * @param messageContent exact DLT-approved message body
     */
    public Optional<SendResult> sendSms(String mobile10Digit, String messageContent) {
        return sendSms(mobile10Digit, messageContent, config.getTemplateId(), config.getEntityId());
    }

    public Optional<SendResult> sendSms(String mobile10Digit, String messageContent, String templateId, String entityId) {
        if (!isConfigured()) {
            LOG.debug("FastSMS skipped: not configured");
            return Optional.empty();
        }
        String recipients = normalizeMobile(mobile10Digit);
        if (recipients == null) {
            LOG.warn("FastSMS skipped: invalid mobile '{}'", mobile10Digit);
            return Optional.empty();
        }
        if (StringUtils.isBlank(messageContent)) {
            LOG.warn("FastSMS skipped: empty message");
            return Optional.empty();
        }

        // Guide: trim credentials — stray whitespace causes signature mismatches.
        String accessToken = StringUtils.trimToEmpty(config.getAccessToken());
        String accessTokenKey = StringUtils.trimToEmpty(config.getAccessTokenKey());
        String smsHeader = StringUtils.trimToEmpty(config.getSmsHeader());
        String route = StringUtils.trimToEmpty(config.getRoute()).toLowerCase(Locale.ROOT);
        String resolvedEntityId = StringUtils.defaultIfBlank(StringUtils.trimToNull(entityId), StringUtils.trimToNull(config.getEntityId()));
        String resolvedTemplateId = StringUtils.trimToNull(templateId);

        // Guide expiry window: +1 hour (configurable; default 3600).
        long expire = Instant.now().getEpochSecond() + Math.max(60L, config.getExpireSeconds());
        String signature = FastSmsSignature.generate(
            accessToken,
            accessTokenKey,
            FastSmsSignature.REQUEST_SEND_SMS,
            expire
        );

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accessToken", accessToken);
        body.put("expire", String.valueOf(expire));
        body.put("authSignature", signature);
        body.put("route", route);
        body.put("smsHeader", smsHeader);
        body.put("messageContent", messageContent);
        body.put("recipients", recipients);
        body.put("contentType", "text");
        if (resolvedEntityId != null) {
            body.put("entityId", resolvedEntityId);
        }
        if (resolvedTemplateId != null) {
            body.put("templateId", resolvedTemplateId);
        }
        body.put("flashSMS", "0");
        body.put("removeDuplicateNumbers", "1");

        try {
            String json = objectMapper.writeValueAsString(body);
            String url = trimTrailingSlash(config.getBaseUrl()) + "/" + FastSmsSignature.REQUEST_SEND_SMS;
            HttpRequest request = HttpRequest
                .newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return Optional.of(parseResponse(response.statusCode(), response.body()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOG.warn("FastSMS interrupted for {}", recipients);
            return Optional.empty();
        } catch (Exception ex) {
            LOG.warn("FastSMS send failed for {}: {}", recipients, ex.getMessage());
            return Optional.empty();
        }
    }

    SendResult parseResponse(int httpStatus, String responseBody) {
        String status = "failed";
        String message = "";
        List<String> invalidNumbers = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(StringUtils.defaultIfBlank(responseBody, "{}"));
            status = text(root, "status");
            message = text(root, "message");
            JsonNode data = root.path("data");
            if (data.isObject()) {
                JsonNode invalid = data.path("invalidNumbers");
                if (invalid.isArray()) {
                    for (JsonNode n : invalid) {
                        if (n.isTextual()) {
                            invalidNumbers.add(n.asText());
                        }
                    }
                }
            }
        } catch (Exception ex) {
            message = StringUtils.abbreviate(responseBody, 200);
        }
        boolean ok = httpStatus >= 200 && httpStatus < 300 && "success".equalsIgnoreCase(status);
        if (!ok) {
            LOG.warn("FastSMS HTTP {} status={} message={}", httpStatus, status, message);
        } else {
            LOG.info("FastSMS accepted: {}", message);
        }
        return new SendResult(ok, httpStatus, status, message, List.copyOf(invalidNumbers));
    }

    /**
     * Normalize to 10-digit Indian mobile. Accepts +91 / 91 prefixes.
     */
    public static String normalizeMobile(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("\\D+", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        if (digits.matches("^[6-9]\\d{9}$")) {
            return digits;
        }
        return null;
    }

    private static String trimTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }

    public record SendResult(boolean success, int httpStatus, String status, String message, List<String> invalidNumbers) {}
}
