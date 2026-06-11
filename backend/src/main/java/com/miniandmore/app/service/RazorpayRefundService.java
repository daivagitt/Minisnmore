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

    public String refundPayment(CustomerOrder order, BigDecimal amount) {
        if (isDemoMode()) {
            return "rfnd_demo_" + order.getOrderNumber();
        }
        String paymentId = StringUtils.trimToNull(order.getRazorpayPaymentId());
        if (paymentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order has no Razorpay payment id");
        }
        try {
            long amountPaise = amount.multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP).longValueExact();
            String body = objectMapper.writeValueAsString(
                Map.of("amount", amountPaise, "notes", Map.of("order_number", order.getOrderNumber()))
            );
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
                throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Razorpay refund failed: HTTP " + response.statusCode() + " " + response.body()
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
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Razorpay refund failed: " + e.getMessage());
        }
    }
}
