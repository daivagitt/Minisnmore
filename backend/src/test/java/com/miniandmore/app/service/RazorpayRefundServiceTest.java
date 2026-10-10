package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class RazorpayRefundServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void isDemoMode_whenDemoCredentials() {
        RazorpayRefundService service = new RazorpayRefundService(objectMapper, "DEMO", "", false);

        assertThat(service.isDemoMode()).isTrue();
    }

    @Test
    void isDemoMode_whenLiveCredentials() {
        RazorpayRefundService service = new RazorpayRefundService(objectMapper, "rzp_live", "secret", false);

        assertThat(service.isDemoMode()).isFalse();
    }

    @Test
    void isDemoMode_whenRefundDemoFlagWithLiveCredentials() {
        RazorpayRefundService service = new RazorpayRefundService(objectMapper, "rzp_live", "secret", true);

        assertThat(service.isDemoMode()).isTrue();
    }

    @Test
    void refundPayment_demoMode_returnsSyntheticId() {
        RazorpayRefundService service = new RazorpayRefundService(objectMapper, "DEMO", "", false);
        CustomerOrder order = order("ORD-1001");

        String refundId = service.refundPayment(order, BigDecimal.valueOf(99));

        assertThat(refundId).isEqualTo("rfnd_demo_ORD-1001");
    }

    @Test
    void refundPayment_liveModeWithoutPaymentId_throwsBadRequest() {
        RazorpayRefundService service = new RazorpayRefundService(objectMapper, "rzp_live", "secret", false);
        CustomerOrder order = order("ORD-1002");
        order.setRazorpayPaymentId(null);

        assertThatThrownBy(() -> service.refundPayment(order, BigDecimal.TEN))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));
    }
    @Test
    void refundPayment_liveMode_httpFailure_throwsBadGateway() {
        RazorpayRefundService service = new RazorpayRefundService(objectMapper, "rzp_test_invalid", "invalid_secret", false);
        CustomerOrder order = order("ORD-LIVE");
        order.setRazorpayPaymentId("pay_invalid_12345");

        assertThatThrownBy(() -> service.refundPayment(order, BigDecimal.TEN))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isGreaterThanOrEqualTo(400));
    }

    @Test
    void isDemoMode_whenBlankSecret() {
        RazorpayRefundService service = new RazorpayRefundService(objectMapper, "rzp_live", "  ", false);

        assertThat(service.isDemoMode()).isTrue();
    }

    @Test
    void isDemoMode_whenDemoKeyCaseInsensitive() {
        RazorpayRefundService service = new RazorpayRefundService(objectMapper, "demo", "secret", false);

        assertThat(service.isDemoMode()).isTrue();
    }

    @Test
    void refundPayment_liveMode_blankPaymentId_throwsBadRequest() {
        RazorpayRefundService service = new RazorpayRefundService(objectMapper, "rzp_live", "secret", false);
        CustomerOrder order = order("ORD-1003");
        order.setRazorpayPaymentId("   ");

        assertThatThrownBy(() -> service.refundPayment(order, BigDecimal.TEN))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void refundId_helper_coverage() throws Exception {
        java.lang.reflect.Method m = RazorpayRefundService.class.getDeclaredMethod("refundId", com.fasterxml.jackson.databind.JsonNode.class);
        m.setAccessible(true);

        var nodeWithId = objectMapper.readTree("{\"id\":\"rfnd_999\"}");
        var nodeEmpty = objectMapper.readTree("{}");
        var nodeBlank = objectMapper.readTree("{\"id\":\"   \"}");

        @SuppressWarnings("unchecked")
        java.util.Optional<String> res1 = (java.util.Optional<String>) m.invoke(null, nodeWithId);
        assertThat(res1).contains("rfnd_999");

        @SuppressWarnings("unchecked")
        java.util.Optional<String> res2 = (java.util.Optional<String>) m.invoke(null, nodeEmpty);
        assertThat(res2).isEmpty();

        @SuppressWarnings("unchecked")
        java.util.Optional<String> res3 = (java.util.Optional<String>) m.invoke(null, nodeBlank);
        assertThat(res3).isEmpty();
    }

    @Test
    void findExistingRefundId_ioExceptionHandling() throws Exception {
        RazorpayRefundService service = new RazorpayRefundService(objectMapper, "rzp_live", "secret", false);
        java.lang.reflect.Method m = RazorpayRefundService.class.getDeclaredMethod(
            "findExistingRefundId",
            String.class,
            String.class,
            long.class,
            String.class,
            boolean.class
        );
        m.setAccessible(true);

        @SuppressWarnings("unchecked")
        java.util.Optional<String> result = (java.util.Optional<String>) m.invoke(service, "invalid_pay_id", "ORD-1", 1000L, null, false);
        assertThat(result).isEmpty();
    }

    @Test
    void refundPayment_liveMode_mockedHttpFlows() throws Exception {
        RazorpayRefundService service = new RazorpayRefundService(objectMapper, "rzp_live", "secret", false);
        CustomerOrder order = order("ORD-1001");
        order.setRazorpayPaymentId("pay_12345");

        try (org.mockito.MockedStatic<java.net.http.HttpClient> mockedStatic = org.mockito.Mockito.mockStatic(java.net.http.HttpClient.class)) {
            java.net.http.HttpClient.Builder builder = org.mockito.Mockito.mock(java.net.http.HttpClient.Builder.class);
            java.net.http.HttpClient client = org.mockito.Mockito.mock(java.net.http.HttpClient.class);
            @SuppressWarnings("unchecked")
            java.net.http.HttpResponse<String> response = org.mockito.Mockito.mock(java.net.http.HttpResponse.class);

            mockedStatic.when(java.net.http.HttpClient::newBuilder).thenReturn(builder);
            org.mockito.Mockito.when(builder.connectTimeout(org.mockito.ArgumentMatchers.any())).thenReturn(builder);
            org.mockito.Mockito.when(builder.build()).thenReturn(client);

            // 1. Success with id
            org.mockito.Mockito.when(response.statusCode()).thenReturn(200);
            org.mockito.Mockito.when(response.body()).thenReturn("{\"id\":\"rfnd_live_abc123\"}");
            org.mockito.Mockito.doReturn(response).when(client).send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

            String refundId = service.refundPayment(order, BigDecimal.TEN);
            assertThat(refundId).isEqualTo("rfnd_live_abc123");

            // 2. Success but missing id -> 502
            org.mockito.Mockito.when(response.body()).thenReturn("{}");
            assertThatThrownBy(() -> service.refundPayment(order, BigDecimal.TEN))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(502));

            // 3. Error with already refunded -> finds existing refund
            org.mockito.Mockito.when(response.statusCode()).thenReturn(400);
            org.mockito.Mockito.when(response.body()).thenReturn(
                "{\"error\":{\"code\":\"BAD_REQUEST_ERROR\",\"description\":\"The payment has already been fully refunded\"}}"
            );
            @SuppressWarnings("unchecked")
            java.net.http.HttpResponse<String> refundsListResponse = org.mockito.Mockito.mock(java.net.http.HttpResponse.class);
            org.mockito.Mockito.when(refundsListResponse.statusCode()).thenReturn(200);
            org.mockito.Mockito.when(refundsListResponse.body()).thenReturn(
                "{\"items\":[{\"id\":\"rfnd_prev_999\",\"amount\":1000,\"notes\":{\"order_number\":\"ORD-1001\"}}]}"
            );
            org.mockito.Mockito.doReturn(response, refundsListResponse)
                .when(client).send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

            String existingId = service.refundPayment(order, BigDecimal.TEN);
            assertThat(existingId).isEqualTo("rfnd_prev_999");
        }
    }

    @Test
    void refundPayment_networkExceptions_throwBadGateway() throws Exception {
        RazorpayRefundService service = new RazorpayRefundService(objectMapper, "rzp_live", "secret", false);
        CustomerOrder order = order("ORD-1001");
        order.setRazorpayPaymentId("pay_12345");

        try (org.mockito.MockedStatic<java.net.http.HttpClient> mockedStatic = org.mockito.Mockito.mockStatic(java.net.http.HttpClient.class)) {
            java.net.http.HttpClient.Builder builder = org.mockito.Mockito.mock(java.net.http.HttpClient.Builder.class);
            java.net.http.HttpClient client = org.mockito.Mockito.mock(java.net.http.HttpClient.class);

            mockedStatic.when(java.net.http.HttpClient::newBuilder).thenReturn(builder);
            org.mockito.Mockito.when(builder.connectTimeout(org.mockito.ArgumentMatchers.any())).thenReturn(builder);
            org.mockito.Mockito.when(builder.build()).thenReturn(client);

            // IOException
            org.mockito.Mockito.doThrow(new java.io.IOException("connection refused"))
                .when(client).send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
            assertThatThrownBy(() -> service.refundPayment(order, BigDecimal.TEN))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(502));

            // InterruptedException
            org.mockito.Mockito.doThrow(new InterruptedException("interrupted"))
                .when(client).send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
            assertThatThrownBy(() -> service.refundPayment(order, BigDecimal.TEN))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(502));
        }
    }

    @Test
    void findExistingRefundId_matchingBranches() throws Exception {
        RazorpayRefundService service = new RazorpayRefundService(objectMapper, "rzp_live", "secret", false);
        java.lang.reflect.Method m = RazorpayRefundService.class.getDeclaredMethod(
            "findExistingRefundId",
            String.class,
            String.class,
            long.class,
            String.class,
            boolean.class
        );
        m.setAccessible(true);

        try (org.mockito.MockedStatic<java.net.http.HttpClient> mockedStatic = org.mockito.Mockito.mockStatic(java.net.http.HttpClient.class)) {
            java.net.http.HttpClient.Builder builder = org.mockito.Mockito.mock(java.net.http.HttpClient.Builder.class);
            java.net.http.HttpClient client = org.mockito.Mockito.mock(java.net.http.HttpClient.class);
            @SuppressWarnings("unchecked")
            java.net.http.HttpResponse<String> response = org.mockito.Mockito.mock(java.net.http.HttpResponse.class);

            mockedStatic.when(java.net.http.HttpClient::newBuilder).thenReturn(builder);
            org.mockito.Mockito.when(builder.connectTimeout(org.mockito.ArgumentMatchers.any())).thenReturn(builder);
            org.mockito.Mockito.when(builder.build()).thenReturn(client);
            org.mockito.Mockito.doReturn(response).when(client).send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

            org.mockito.Mockito.when(response.statusCode()).thenReturn(200);
            org.mockito.Mockito.when(response.body()).thenReturn("{\"items\":[]}");
            @SuppressWarnings("unchecked")
            java.util.Optional<String> res1 = (java.util.Optional<String>) m.invoke(
                service,
                "pay_1",
                "ORD-1",
                1000L,
                RazorpayRefundService.PURPOSE_RETURN_REFUND,
                false
            );
            assertThat(res1).isEmpty();

            org.mockito.Mockito.when(response.body()).thenReturn(
                "{\"items\":[{\"id\":\"rfnd_settle\",\"amount\":1000,\"notes\":{\"order_number\":\"ORD-1\",\"purpose\":\"exchange_price_difference\"}}]}"
            );
            @SuppressWarnings("unchecked")
            java.util.Optional<String> res2 = (java.util.Optional<String>) m.invoke(
                service,
                "pay_1",
                "ORD-1",
                1000L,
                RazorpayRefundService.PURPOSE_RETURN_REFUND,
                false
            );
            assertThat(res2).isEmpty();

            @SuppressWarnings("unchecked")
            java.util.Optional<String> res3 = (java.util.Optional<String>) m.invoke(
                service,
                "pay_1",
                "ORD-1",
                1000L,
                RazorpayRefundService.PURPOSE_EXCHANGE_SETTLEMENT,
                false
            );
            assertThat(res3).contains("rfnd_settle");

            org.mockito.Mockito.when(response.body()).thenReturn(
                "{\"items\":[{\"id\":\"rfnd_amount_match\",\"amount\":1000,\"notes\":{\"order_number\":\"OTHER\"}}]}"
            );
            @SuppressWarnings("unchecked")
            java.util.Optional<String> res4 = (java.util.Optional<String>) m.invoke(service, "pay_1", "ORD-1", 1000L, null, false);
            assertThat(res4).isEmpty();

            @SuppressWarnings("unchecked")
            java.util.Optional<String> res5 = (java.util.Optional<String>) m.invoke(service, "pay_1", "ORD-1", 1000L, null, true);
            assertThat(res5).contains("rfnd_amount_match");

            org.mockito.Mockito.when(response.body()).thenReturn(
                "{\"items\":[{\"id\":\"rfnd_first\",\"amount\":500},{\"id\":\"rfnd_last\",\"amount\":700}]}"
            );
            @SuppressWarnings("unchecked")
            java.util.Optional<String> res6 = (java.util.Optional<String>) m.invoke(service, "pay_1", "ORD-1", 1000L, null, false);
            assertThat(res6).isEmpty();

            @SuppressWarnings("unchecked")
            java.util.Optional<String> res7 = (java.util.Optional<String>) m.invoke(service, "pay_1", "ORD-1", 1000L, null, true);
            assertThat(res7).contains("rfnd_last");

            org.mockito.Mockito.doThrow(new InterruptedException("interrupted"))
                .when(client).send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
            @SuppressWarnings("unchecked")
            java.util.Optional<String> res8 = (java.util.Optional<String>) m.invoke(service, "pay_1", "ORD-1", 1000L, null, true);
            assertThat(res8).isEmpty();
        }
    }

    private static CustomerOrder order(String orderNumber) {
        return new CustomerOrder()
            .orderNumber(orderNumber)
            .userLogin("user")
            .status(OrderStatus.CONFIRMED)
            .paymentStatus(PaymentStatus.PAID)
            .subtotal(BigDecimal.TEN)
            .discount(BigDecimal.ZERO)
            .shipping(BigDecimal.ZERO)
            .tax(BigDecimal.ZERO)
            .donation(BigDecimal.ZERO)
            .total(BigDecimal.TEN)
            .shippingAddressJson("{}");
    }
}
