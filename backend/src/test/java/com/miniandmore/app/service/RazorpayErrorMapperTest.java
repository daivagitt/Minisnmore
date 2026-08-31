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

    @Test
    void parse_blankBody_returnsEmptyError() {
        RazorpayErrorMapper.ParsedRazorpayError parsed = RazorpayErrorMapper.parse(objectMapper, "   ");

        assertThat(parsed.code()).isNull();
        assertThat(parsed.description()).isNull();
        assertThat(parsed.reason()).isNull();
    }

    @Test
    void parse_invalidJson_returnsEmptyError() {
        RazorpayErrorMapper.ParsedRazorpayError parsed = RazorpayErrorMapper.parse(objectMapper, "{not-json");

        assertThat(parsed.code()).isNull();
        assertThat(parsed.description()).isNull();
    }

    @Test
    void parse_flatErrorShape_readsFields() {
        String body = """
            {"code":"BAD_REQUEST_ERROR","description":"Payment does not exist","reason":"invalid_id"}
            """;

        RazorpayErrorMapper.ParsedRazorpayError parsed = RazorpayErrorMapper.parse(objectMapper, body);

        assertThat(parsed.code()).isEqualTo("BAD_REQUEST_ERROR");
        assertThat(parsed.description()).isEqualTo("Payment does not exist");
        assertThat(parsed.reason()).isEqualTo("invalid_id");
    }

    @Test
    void refundUserMessage_notCaptured_returnsFriendlyMessage() {
        RazorpayErrorMapper.ParsedRazorpayError parsed = new RazorpayErrorMapper.ParsedRazorpayError(
            null,
            "Payment is not captured",
            null
        );

        assertThat(RazorpayErrorMapper.refundUserMessage(parsed, 400))
            .isEqualTo("Refund cannot be issued because the payment has not been captured yet.");
    }

    @Test
    void refundUserMessage_invalidId_returnsFriendlyMessage() {
        RazorpayErrorMapper.ParsedRazorpayError parsed = new RazorpayErrorMapper.ParsedRazorpayError(
            null,
            "The id provided does not exist",
            null
        );

        assertThat(RazorpayErrorMapper.refundUserMessage(parsed, 400))
            .isEqualTo("Refund could not be processed because the payment record was not found in Razorpay.");
    }

    @Test
    void refundUserMessage_authFailure_returnsDashboardHint() {
        RazorpayErrorMapper.ParsedRazorpayError parsed = RazorpayErrorMapper.parse(objectMapper, "{}");

        assertThat(RazorpayErrorMapper.refundUserMessage(parsed, 401))
            .contains("Check your API keys");
        assertThat(RazorpayErrorMapper.refundUserMessage(parsed, 403))
            .contains("Check your API keys");
    }

    @Test
    void refundUserMessage_unknownDescription_returnsDescription() {
        RazorpayErrorMapper.ParsedRazorpayError parsed = new RazorpayErrorMapper.ParsedRazorpayError(
            null,
            "Gateway timeout from upstream",
            null
        );

        assertThat(RazorpayErrorMapper.refundUserMessage(parsed, 502)).isEqualTo("Gateway timeout from upstream");
    }

    @Test
    void isAlreadyRefundedError_otherError_returnsFalse() {
        RazorpayErrorMapper.ParsedRazorpayError parsed = new RazorpayErrorMapper.ParsedRazorpayError(
            "BAD_REQUEST_ERROR",
            "Insufficient balance",
            null
        );

        assertThat(RazorpayErrorMapper.isAlreadyRefundedError(parsed)).isFalse();
    }

    @Test
    void refundHttpStatus_alreadyRefunded_returnsConflict() {
        RazorpayErrorMapper.ParsedRazorpayError parsed = new RazorpayErrorMapper.ParsedRazorpayError(
            null,
            "Payment has already been refunded",
            null
        );

        assertThat(RazorpayErrorMapper.refundHttpStatus(parsed, 400)).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void refundHttpStatus_clientError_returnsBadRequest() {
        RazorpayErrorMapper.ParsedRazorpayError parsed = new RazorpayErrorMapper.ParsedRazorpayError(null, "Some client error", null);

        assertThat(RazorpayErrorMapper.refundHttpStatus(parsed, 404)).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void refundHttpStatus_serverError_returnsBadGateway() {
        RazorpayErrorMapper.ParsedRazorpayError parsed = new RazorpayErrorMapper.ParsedRazorpayError(null, null, null);

        assertThat(RazorpayErrorMapper.refundHttpStatus(parsed, 502)).isEqualTo(HttpStatus.BAD_GATEWAY);
    }
}
