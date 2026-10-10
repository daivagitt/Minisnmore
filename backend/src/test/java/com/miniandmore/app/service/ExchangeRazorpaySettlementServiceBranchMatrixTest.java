package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.enumeration.ExchangeSettlementStatus;
import com.miniandmore.app.domain.enumeration.OrderRequestType;
import com.miniandmore.app.domain.enumeration.OrderReturnStatus;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.OrderReturnRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ExchangeRazorpaySettlementServiceBranchMatrixTest {

    @Mock
    private OrderReturnRepository orderReturnRepository;

    @Mock
    private CustomerOrderRepository customerOrderRepository;

    @Mock
    private RazorpayRefundService razorpayRefundService;

    @Mock
    private OrderMailService orderMailService;

    @InjectMocks
    private ExchangeRazorpaySettlementService service;

    @ParameterizedTest
    @MethodSource("invalidSettlementCases")
    void recordRazorpayRefund_rejectsInvalidRequests(
        OrderReturn orderReturn,
        BigDecimal amount,
        HttpStatus expectedStatus,
        String messageFragment
    ) {
        when(orderReturnRepository.findOneByReturnNumberForUpdate("RET-X")).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() -> service.recordRazorpayRefund("RET-X", amount, "admin", null))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                org.assertj.core.api.Assertions.assertThat(rse.getStatusCode()).isEqualTo(expectedStatus);
                org.assertj.core.api.Assertions.assertThat(rse.getReason()).contains(messageFragment);
            });
        verify(razorpayRefundService, never()).refundPayment(any(), any(), any());
    }

    private static Stream<Arguments> invalidSettlementCases() {
        OrderReturn notExchange = baseExchange();
        notExchange.setRequestType(OrderRequestType.RETURN);

        OrderReturn wrongStatus = baseExchange();
        wrongStatus.setStatus(OrderReturnStatus.RECEIVED);

        OrderReturn positiveDiff = baseExchange();
        positiveDiff.setPriceDifference(new BigDecimal("50.00"));

        OrderReturn amountMismatch = baseExchange();

        OrderReturn codOrder = baseExchange();
        codOrder.getOrder().setPaymentMethod("cod");
        codOrder.getOrder().setRazorpayPaymentId(null);

        return Stream.of(
            Arguments.of(notExchange, new BigDecimal("300"), HttpStatus.BAD_REQUEST, "exchanges"),
            Arguments.of(wrongStatus, new BigDecimal("300"), HttpStatus.BAD_REQUEST, "approved"),
            Arguments.of(positiveDiff, new BigDecimal("300"), HttpStatus.BAD_REQUEST, "cheaper"),
            Arguments.of(amountMismatch, new BigDecimal("299"), HttpStatus.BAD_REQUEST, "must equal"),
            Arguments.of(codOrder, new BigDecimal("300"), HttpStatus.BAD_REQUEST, "eligible")
        );
    }

    private static OrderReturn baseExchange() {
        CustomerOrder order = new CustomerOrder()
            .id(1L)
            .orderNumber("ORD-1")
            .paymentMethod("online")
            .razorpayPaymentId("pay_1")
            .paymentStatus(PaymentStatus.PAID)
            .status(OrderStatus.FULFILLED);
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber("RET-X");
        orderReturn.setRequestType(OrderRequestType.EXCHANGE);
        orderReturn.setStatus(OrderReturnStatus.ACCEPTED);
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.PENDING);
        orderReturn.setPriceDifference(new BigDecimal("-300.00"));
        orderReturn.setOrder(order);
        return orderReturn;
    }
}
