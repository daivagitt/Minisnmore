package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RazorpayErrorMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void refundUserMessage_insufficientBalance_returnsActionableMessage() {
        String body =
            """
            {"error":{"code":"BAD_REQUEST_ERROR","description":"Your account does not have enough balance to carry out the refund operation. You can add funds to your account from your Razorpay dashboard or capture new payments."}}
            """;

        RazorpayErrorMapper.ParsedRazorpayError parsed = RazorpayErrorMapper.parse(objectMapper, body);
        String message = RazorpayErrorMapper.refundUserMessage(parsed, 400);

        assertThat(message).contains("does not have enough balance");
        assertThat(message).contains("Razorpay Dashboard");
        assertThat(message).doesNotContain("BAD_REQUEST_ERROR");
        assertThat(message).doesNotContain("HTTP 400");
    }

    @Test
    void isAlreadyRefundedError_fullyRefunded_returnsTrue() {
        RazorpayErrorMapper.ParsedRazorpayError parsed = new RazorpayErrorMapper.ParsedRazorpayError(
            "BAD_REQUEST_ERROR",
            "The payment has been fully refunded already",
            null
        );

        assertThat(RazorpayErrorMapper.isAlreadyRefundedError(parsed)).isTrue();
    }

    @Test
    void refundHttpStatus_insufficientBalance_returnsConflict() {
        String body =
            """
            {"error":{"description":"Your account does not have enough balance to carry out the refund operation."}}
            """;

        RazorpayErrorMapper.ParsedRazorpayError parsed = RazorpayErrorMapper.parse(objectMapper, body);

        assertThat(RazorpayErrorMapper.refundHttpStatus(parsed, 400)).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void refundUserMessage_alreadyRefunded_returnsFriendlyMessage() {
        String body = """
            {"error":{"description":"The payment has already been fully refunded."}}
            """;

        RazorpayErrorMapper.ParsedRazorpayError parsed = RazorpayErrorMapper.parse(objectMapper, body);

        assertThat(RazorpayErrorMapper.refundUserMessage(parsed, 400)).isEqualTo("This payment has already been refunded.");
    }

    @Test
    void refundUserMessage_unknownError_fallsBackToGenericMessage() {
        RazorpayErrorMapper.ParsedRazorpayError parsed = RazorpayErrorMapper.parse(objectMapper, "");

        assertThat(RazorpayErrorMapper.refundUserMessage(parsed, 502))
            .isEqualTo("Refund could not be processed. Please try again or contact support.");
    }
}
