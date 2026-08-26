package com.miniandmore.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;

final class RazorpayErrorMapper {

    private RazorpayErrorMapper() {}

    record ParsedRazorpayError(String code, String description, String reason) {}

    static ParsedRazorpayError parse(ObjectMapper objectMapper, String body) {
        if (StringUtils.isBlank(body)) {
            return new ParsedRazorpayError(null, null, null);
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode error = root.path("error");
            if (error.isMissingNode() || error.isNull()) {
                error = root;
            }
            return new ParsedRazorpayError(
                textOrNull(error.path("code")),
                textOrNull(error.path("description")),
                textOrNull(error.path("reason"))
            );
        } catch (Exception ignored) {
            return new ParsedRazorpayError(null, null, null);
        }
    }

    static String refundUserMessage(ParsedRazorpayError error, int httpStatus) {
        String description = StringUtils.trimToNull(error.description());
        if (description != null) {
            String normalized = description.toLowerCase(Locale.ROOT);
            if (normalized.contains("enough balance") || normalized.contains("insufficient balance")) {
                return (
                    "Refund could not be processed because your Razorpay account does not have enough balance. " +
                    "Add funds in the Razorpay Dashboard or wait for recent payments to settle, then try again."
                );
            }
            if (normalized.contains("already been refunded") || normalized.contains("fully refunded")) {
                return "This payment has already been refunded.";
            }
            if (normalized.contains("not captured")) {
                return "Refund cannot be issued because the payment has not been captured yet.";
            }
            if (normalized.contains("does not exist") || normalized.contains("invalid id")) {
                return "Refund could not be processed because the payment record was not found in Razorpay.";
            }
            return description;
        }

        if (httpStatus == 401 || httpStatus == 403) {
            return "Razorpay rejected the refund request. Check your API keys and account status in the Razorpay Dashboard.";
        }
        return "Refund could not be processed. Please try again or contact support.";
    }

    static boolean isAlreadyRefundedError(ParsedRazorpayError error) {
        String description = StringUtils.defaultString(error.description()).toLowerCase(Locale.ROOT);
        return description.contains("already been refunded") || description.contains("fully refunded");
    }

    static HttpStatus refundHttpStatus(ParsedRazorpayError error, int razorpayStatus) {
        String description = StringUtils.defaultString(error.description()).toLowerCase(Locale.ROOT);
        if (description.contains("enough balance") || description.contains("insufficient balance")) {
            return HttpStatus.CONFLICT;
        }
        if (description.contains("already been refunded") || description.contains("fully refunded")) {
            return HttpStatus.CONFLICT;
        }
        if (razorpayStatus >= 400 && razorpayStatus < 500) {
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.BAD_GATEWAY;
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        return StringUtils.trimToNull(value);
    }
}
