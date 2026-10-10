package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ExchangeSettlementAmountsEligibilityBranchTest {

    @ParameterizedTest
    @MethodSource("eligibilityMatrix")
    void prepaidRazorpayEligibility_matrix(
        CustomerOrder order,
        boolean expected
    ) {
        assertThat(ExchangeSettlementAmounts.isPrepaidRazorpayEligible(order)).isEqualTo(expected);
    }

    private static Stream<Arguments> eligibilityMatrix() {
        return Stream.of(
            Arguments.of(null, false),
            Arguments.of(order("cod", "pay_1", PaymentStatus.PAID), false),
            Arguments.of(order("COD", "pay_1", PaymentStatus.PAID), false),
            Arguments.of(order("online", null, PaymentStatus.PAID), false),
            Arguments.of(order("online", " ", PaymentStatus.PAID), false),
            Arguments.of(order("online", "pay_1", PaymentStatus.PAID), true),
            Arguments.of(order("online", "pay_1", PaymentStatus.REFUND_INITIATED), true),
            Arguments.of(order("online", "pay_1", PaymentStatus.REFUND_FAILED), true),
            Arguments.of(order("online", "pay_1", PaymentStatus.PARTIALLY_REFUNDED), true),
            Arguments.of(order("online", "pay_1", PaymentStatus.PENDING), false),
            Arguments.of(order("online", "pay_1", null), false)
        );
    }

    private static CustomerOrder order(String method, String paymentId, PaymentStatus status) {
        return new CustomerOrder()
            .paymentMethod(method)
            .razorpayPaymentId(paymentId)
            .paymentStatus(status);
    }
}
