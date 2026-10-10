package com.miniandmore.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.domain.CustomerOrder;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RazorpayRefundService {

    public static final String PURPOSE_EXCHANGE_SETTLEMENT = "exchange_price_difference";
    public static final String PURPOSE_RETURN_REFUND = "return_refund";

    private final ObjectMapper objectMapper;
    private final String razorpayKeyId;
    private final String razorpayKeySecret;
    private final boolean refundDemoMode;

    public RazorpayRefundService(
        ObjectMapper objectMapper,
        @Value("${razorpay.key-id:DEMO}") String razorpayKeyId,
        @Value("${razorpay.key-secret:}") String razorpayKeySecret,
        @Value("${razorpay.refund-demo:false}") boolean refundDemoMode
    ) {
        this.objectMapper = objectMapper;
        this.razorpayKeyId = razorpayKeyId;
        this.razorpayKeySecret = razorpayKeySecret;
        this.refundDemoMode = refundDemoMode;
    }

    public boolean isDemoMode() {
        return refundDemoMode || StringUtils.isBlank(razorpayKeySecret) || "DEMO".equalsIgnoreCase(razorpayKeyId);
    }

    public Optional<String> findExistingRefundForOrder(CustomerOrder order, BigDecimal amount) {
        return findExistingRefundForOrder(order, amount, null);
    }

    /**
     * Retry helper for the same money movement. Matches order number + amount + optional purpose.
     * Does not fall back to "any prior refund on this payment" — that would skip a second payout.
     */
    public Optional<String> findExistingRefundForOrder(CustomerOrder order, BigDecimal amount, String purpose) {
        if (isDemoMode() || order == null) {
            return Optional.empty();
        }
        String paymentId = StringUtils.trimToNull(order.getRazorpayPaymentId());
        if (paymentId == null || amount == null || amount.signum() <= 0) {
            return Optional.empty();
        }
        try {
            long amountPaise = amount.multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP).longValueExact();
            return findExistingRefundId(paymentId, order.getOrderNumber(), amountPaise, purpose, false);
        } catch (ArithmeticException e) {
            return Optional.empty();
        }
    }

    public String refundPayment(CustomerOrder order, BigDecimal amount) {
        return refundPayment(order, amount, null);
    }

    public String refundPayment(CustomerOrder order, BigDecimal amount, String purpose) {
        if (isDemoMode()) {
            return "rfnd_demo_" + order.getOrderNumber();
        }
        String paymentId = StringUtils.trimToNull(order.getRazorpayPaymentId());
        if (paymentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order has no Razorpay payment id");
        }
        try {
            long amountPaise = amount.multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP).longValueExact();
            Map<String, String> notes = new LinkedHashMap<>();
            notes.put("order_number", StringUtils.defaultString(order.getOrderNumber()));
            if (StringUtils.isNotBlank(purpose)) {
                notes.put("purpose", purpose.trim());
            }
            String body = objectMapper.writeValueAsString(Map.of("amount", amountPaise, "notes", notes));
            String token = Base64.getEncoder().encodeToString(
                (razorpayKeyId + ":" + razorpayKeySecret).getBytes(StandardCharsets.UTF_8)
            );
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.razorpay.com/v1/payments/" + paymentId + "/refund"))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + token)
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                RazorpayErrorMapper.ParsedRazorpayError razorpayError = RazorpayErrorMapper.parse(objectMapper, response.body());
                if (RazorpayErrorMapper.isAlreadyRefundedError(razorpayError)) {
                    Optional<String> existingRefundId = findExistingRefundId(
                        paymentId,
                        order.getOrderNumber(),
                        amountPaise,
                        purpose,
                        true
                    );
                    if (existingRefundId.isPresent()) {
                        return existingRefundId.orElseThrow();
                    }
                }
                throw new ResponseStatusException(
                    RazorpayErrorMapper.refundHttpStatus(razorpayError, response.statusCode()),
                    RazorpayErrorMapper.refundUserMessage(razorpayError, response.statusCode())
                );
            }
            JsonNode root = objectMapper.readTree(response.body());
            return Optional.ofNullable(root.path("id").asText(null)).filter(StringUtils::isNotBlank).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Razorpay refund response missing id")
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Razorpay refund interrupted");
        } catch (IOException | ArithmeticException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Refund could not be processed. Please try again.");
        }
    }

    /**
     * When Razorpay reports a payment is already refunded (e.g. refund issued outside this flow),
     * look up the existing refund id so the return can still be completed.
     */
    private Optional<String> findExistingRefundId(
        String paymentId,
        String orderNumber,
        long amountPaise,
        String purpose,
        boolean allowLastItemFallback
    ) {
        try {
            String token = Base64.getEncoder().encodeToString(
                (razorpayKeyId + ":" + razorpayKeySecret).getBytes(StandardCharsets.UTF_8)
            );
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.razorpay.com/v1/payments/" + paymentId + "/refunds"))
                .header(HttpHeaders.AUTHORIZATION, "Basic " + token)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                return Optional.empty();
            }
            JsonNode items = objectMapper.readTree(response.body()).path("items");
            if (!items.isArray() || items.isEmpty()) {
                return Optional.empty();
            }
            String orderNote = StringUtils.trimToEmpty(orderNumber);
            String wantedPurpose = StringUtils.trimToEmpty(purpose);
            for (JsonNode refund : items) {
                if (isFailedRefund(refund)) {
                    continue;
                }
                String notesOrder = StringUtils.trimToEmpty(refund.path("notes").path("order_number").asText(""));
                String notesPurpose = StringUtils.trimToEmpty(refund.path("notes").path("purpose").asText(""));
                long refundAmount = refund.path("amount").asLong(-1);
                if (!orderNote.equals(notesOrder) || refundAmount != amountPaise) {
                    continue;
                }
                if (wantedPurpose.isEmpty() || wantedPurpose.equals(notesPurpose)) {
                    return refundId(refund);
                }
            }
            if (!allowLastItemFallback) {
                return Optional.empty();
            }
            for (JsonNode refund : items) {
                if (isFailedRefund(refund)) {
                    continue;
                }
                if (refund.path("amount").asLong(-1) == amountPaise) {
                    return refundId(refund);
                }
            }
            for (int i = items.size() - 1; i >= 0; i--) {
                if (!isFailedRefund(items.get(i))) {
                    return refundId(items.get(i));
                }
            }
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static boolean isFailedRefund(JsonNode refund) {
        return "failed".equalsIgnoreCase(StringUtils.trimToEmpty(refund.path("status").asText("")));
    }

    private static Optional<String> refundId(JsonNode refund) {
        return Optional.ofNullable(refund.path("id").asText(null)).filter(StringUtils::isNotBlank);
    }
}
