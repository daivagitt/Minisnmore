package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Public API branches for {@link RazorpayRefundService#findExistingRefundForOrder}. */
class RazorpayRefundServiceFindExistingForOrderBranchTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void findExistingRefundForOrder_demoMode_returnsEmpty() {
        RazorpayRefundService service = new RazorpayRefundService(objectMapper, "DEMO", "", false);
        CustomerOrder order = order("ORD-1", "pay_1");

        assertThat(service.findExistingRefundForOrder(order, BigDecimal.TEN)).isEmpty();
        assertThat(service.findExistingRefundForOrder(order, BigDecimal.TEN, RazorpayRefundService.PURPOSE_RETURN_REFUND)).isEmpty();
    }

    @Test
    void findExistingRefundForOrder_nullOrderOrInvalidAmount_returnsEmpty() {
        RazorpayRefundService service = new RazorpayRefundService(objectMapper, "rzp_live", "secret", false);

        assertThat(service.findExistingRefundForOrder(null, BigDecimal.TEN)).isEmpty();
        CustomerOrder noPayment = order("ORD-2", null);
        assertThat(service.findExistingRefundForOrder(noPayment, BigDecimal.TEN)).isEmpty();
        CustomerOrder zero = order("ORD-3", "pay_3");
        assertThat(service.findExistingRefundForOrder(zero, BigDecimal.ZERO)).isEmpty();
        assertThat(service.findExistingRefundForOrder(zero, new BigDecimal("-1"))).isEmpty();
    }

    @Test
    void findExistingRefundForOrder_liveMode_matchingRefund_returnsId() throws Exception {
        RazorpayRefundService service = new RazorpayRefundService(objectMapper, "rzp_live", "secret", false);
        CustomerOrder order = order("ORD-100", "pay_live");

        try (org.mockito.MockedStatic<java.net.http.HttpClient> mockedStatic = Mockito.mockStatic(java.net.http.HttpClient.class)) {
            java.net.http.HttpClient.Builder builder = Mockito.mock(java.net.http.HttpClient.Builder.class);
            java.net.http.HttpClient client = Mockito.mock(java.net.http.HttpClient.class);
            @SuppressWarnings("unchecked")
            java.net.http.HttpResponse<String> response = Mockito.mock(java.net.http.HttpResponse.class);

            mockedStatic.when(java.net.http.HttpClient::newBuilder).thenReturn(builder);
            Mockito.when(builder.connectTimeout(Mockito.any())).thenReturn(builder);
            Mockito.when(builder.build()).thenReturn(client);
            Mockito.when(response.statusCode()).thenReturn(200);
            Mockito.when(response.body()).thenReturn(
                "{\"items\":[{\"id\":\"rfnd_public\",\"amount\":1000,\"status\":\"processed\",\"notes\":{\"order_number\":\"ORD-100\",\"purpose\":\"return_refund\"}}]}"
            );
            Mockito.doReturn(response).when(client).send(Mockito.any(), Mockito.any());

            Optional<String> found = service.findExistingRefundForOrder(
                order,
                BigDecimal.TEN,
                RazorpayRefundService.PURPOSE_RETURN_REFUND
            );
            assertThat(found).contains("rfnd_public");
        }
    }

    @Test
    void findExistingRefundForOrder_amountScaleOverflow_returnsEmpty() {
        RazorpayRefundService service = new RazorpayRefundService(objectMapper, "rzp_live", "secret", false);
        CustomerOrder order = order("ORD-BIG", "pay_big");
        order.setTotal(new BigDecimal("999999999999999999999.99"));

        assertThat(service.findExistingRefundForOrder(order, order.getTotal())).isEmpty();
    }

    private static CustomerOrder order(String orderNumber, String paymentId) {
        CustomerOrder order = new CustomerOrder()
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
        order.setRazorpayPaymentId(paymentId);
        return order;
    }
}
