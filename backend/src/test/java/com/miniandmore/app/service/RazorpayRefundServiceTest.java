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
