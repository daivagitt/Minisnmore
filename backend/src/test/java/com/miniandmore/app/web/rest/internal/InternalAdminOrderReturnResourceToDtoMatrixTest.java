package com.miniandmore.app.web.rest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.enumeration.ExchangeSettlementStatus;
import com.miniandmore.app.domain.enumeration.ManualRefundMethod;
import com.miniandmore.app.domain.enumeration.OrderRequestType;
import com.miniandmore.app.domain.enumeration.OrderReturnStatus;
import com.miniandmore.app.domain.enumeration.PackageType;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.domain.enumeration.ReturnDisposition;
import com.miniandmore.app.domain.enumeration.ReturnLogisticsMode;
import com.miniandmore.app.service.CaseSlaSupport;
import com.miniandmore.app.service.OrderReturnService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InternalAdminOrderReturnResourceToDtoMatrixTest {

    @Mock
    private OrderReturnService orderReturnService;

    @Mock
    private CaseSlaSupport caseSlaSupport;

    private InternalAdminOrderReturnResource resource;

    @BeforeEach
    void setUp() {
        resource = new InternalAdminOrderReturnResource(orderReturnService, caseSlaSupport);
        when(orderReturnService.imageUrls(any())).thenReturn(List.of());
        when(orderReturnService.returnLineBreakdown(any())).thenReturn(List.of());
        when(orderReturnService.canCompletePackage(any())).thenReturn(false);
        when(orderReturnService.canBookOutbound(any())).thenReturn(false);
        when(orderReturnService.defaultPackageWeightGrams(any())).thenReturn(250);
        when(orderReturnService.documentNumber(any(), any())).thenReturn("DOC-1");
        when(orderReturnService.shippingAddressPayload(any())).thenReturn(Map.of());
        when(orderReturnService.dispatchAddressPayload()).thenReturn(Map.of());
        when(orderReturnService.canRefundExchangeDifferenceViaRazorpay(any())).thenReturn(false);
    }

    @Test
    void toDto_withoutOrder_skipsOrderBlock() {
        OrderReturn orderReturn = base(OrderReturnStatus.REQUESTED);
        @SuppressWarnings("unchecked")
        Map<String, Object> dto = (Map<String, Object>) ReflectionTestUtils.invokeMethod(resource, "toDto", orderReturn);
        assertThat(dto.get("order_id")).isNull();
        assertThat(dto.get("cod_refund_pending")).isNull();
        assertThat(dto.get("disposition")).isNull();
        assertThat(dto.get("logistics_mode")).isNull();
        assertThat(dto.get("package_type")).isEqualTo("custom");
        assertThat(dto.get("packed_at")).isEqualTo("");
        assertThat(dto.get("settlement_status")).isNull();
        assertThat(dto.get("manual_refund_method")).isNull();
    }

    @Test
    void toDto_withOrderAndNullablePayment_coversCodAndEnums() {
        OrderReturn orderReturn = base(OrderReturnStatus.ACCEPTED);
        CustomerOrder order = new CustomerOrder().id(9L).orderNumber("MNM-9").paymentMethod("cod").paymentStatus(PaymentStatus.PAID);
        orderReturn.setOrder(order);
        orderReturn.setManualRefundMethod(ManualRefundMethod.UPI);
        orderReturn.setDisposition(ReturnDisposition.RESTOCKABLE);
        orderReturn.setLogisticsMode(ReturnLogisticsMode.XPRESSBEES);
        orderReturn.setOutboundLogisticsMode(ReturnLogisticsMode.MANUAL);
        orderReturn.setPackageType(PackageType.BOX);
        orderReturn.setPackedAt(Instant.parse("2026-01-01T00:00:00Z"));
        orderReturn.setSettlementStatus(ExchangeSettlementStatus.REFUNDED);

        @SuppressWarnings("unchecked")
        Map<String, Object> dto = (Map<String, Object>) ReflectionTestUtils.invokeMethod(resource, "toDto", orderReturn);

        assertThat(dto.get("order_id")).isEqualTo(9L);
        assertThat(dto.get("payment_status")).isEqualTo("paid");
        assertThat(dto.get("cod_refund_pending")).isEqualTo(true);
        assertThat(dto.get("manual_refund_method")).isEqualTo("upi");
        assertThat(dto.get("disposition")).isEqualTo("restockable");
        assertThat(dto.get("logistics_mode")).isEqualTo("xpressbees");
        assertThat(dto.get("outbound_logistics_mode")).isEqualTo("manual");
        assertThat(dto.get("package_type")).isEqualTo("box");
        assertThat(dto.get("packed_at")).isEqualTo("2026-01-01T00:00:00Z");
        assertThat(dto.get("settlement_status")).isEqualTo("refunded");
    }

    @Test
    void toDto_failedCodWithoutManualRefund_isPending() {
        OrderReturn orderReturn = base(OrderReturnStatus.FAILED);
        orderReturn.setOrder(new CustomerOrder().paymentMethod("COD").paymentStatus(PaymentStatus.PAID));
        @SuppressWarnings("unchecked")
        Map<String, Object> dto = (Map<String, Object>) ReflectionTestUtils.invokeMethod(resource, "toDto", orderReturn);
        assertThat(dto.get("cod_refund_pending")).isEqualTo(true);
    }

    @Test
    void toDto_codPendingFalseWhenManualRefundRecordedOrNotCollected() {
        OrderReturn recorded = base(OrderReturnStatus.ACCEPTED);
        recorded.setOrder(new CustomerOrder().paymentMethod("cod").paymentStatus(PaymentStatus.PAID));
        recorded.setManualRefundRecordedAt(Instant.now());
        @SuppressWarnings("unchecked")
        Map<String, Object> recordedDto = (Map<String, Object>) ReflectionTestUtils.invokeMethod(resource, "toDto", recorded);
        assertThat(recordedDto.get("cod_refund_pending")).isEqualTo(false);

        OrderReturn unpaid = base(OrderReturnStatus.ACCEPTED);
        unpaid.setOrder(new CustomerOrder().paymentMethod("cod").paymentStatus(PaymentStatus.PENDING));
        @SuppressWarnings("unchecked")
        Map<String, Object> unpaidDto = (Map<String, Object>) ReflectionTestUtils.invokeMethod(resource, "toDto", unpaid);
        assertThat(unpaidDto.get("cod_refund_pending")).isEqualTo(false);

        OrderReturn online = base(OrderReturnStatus.ACCEPTED);
        online.setOrder(new CustomerOrder().paymentMethod("online").paymentStatus(PaymentStatus.PAID));
        online.getOrder().setPaymentStatus(null);
        @SuppressWarnings("unchecked")
        Map<String, Object> onlineDto = (Map<String, Object>) ReflectionTestUtils.invokeMethod(resource, "toDto", online);
        assertThat(onlineDto.get("payment_status")).isNull();
        assertThat(onlineDto.get("cod_refund_pending")).isEqualTo(false);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   " })
    void parseHelpers_rejectBlank(String raw) {
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(resource, "parseManualRefundMethod", raw))
            .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(resource, "parseLogisticsMode", raw))
            .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(resource, "parseDisposition", raw))
            .isInstanceOf(ResponseStatusException.class);
    }

    @ParameterizedTest
    @CsvSource({ "upi,UPI", "BANK_TRANSFER,BANK_TRANSFER", "cash,CASH" })
    void parseManualRefundMethod_acceptsEnumNames(String raw, ManualRefundMethod expected) {
        Object parsed = ReflectionTestUtils.invokeMethod(resource, "parseManualRefundMethod", raw);
        assertThat(parsed).isEqualTo(expected);
    }

    @ParameterizedTest
    @EnumSource(ReturnLogisticsMode.class)
    void parseLogisticsMode_acceptsEachMode(ReturnLogisticsMode mode) {
        Object parsed = ReflectionTestUtils.invokeMethod(resource, "parseLogisticsMode", mode.name().toLowerCase());
        assertThat(parsed).isEqualTo(mode);
    }

    @Test
    void parseHelpers_rejectUnknownTokens() {
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(resource, "parseManualRefundMethod", "cheque"))
            .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(resource, "parseLogisticsMode", "drone"))
            .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(resource, "parseDisposition", "burn"))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void notes_nullBodyReturnsNull() {
        assertThat((String) ReflectionTestUtils.invokeMethod(resource, "notes", new Object[] { null })).isNull();
        InternalAdminOrderReturnResource.AdminNotesRequest body = new InternalAdminOrderReturnResource.AdminNotesRequest("hi");
        assertThat((String) ReflectionTestUtils.invokeMethod(resource, "notes", body)).isEqualTo("hi");
    }

    private static OrderReturn base(OrderReturnStatus status) {
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setId(1L);
        orderReturn.setReturnNumber("RET-DTO");
        orderReturn.setStatus(status);
        orderReturn.setRequestType(OrderRequestType.RETURN);
        return orderReturn;
    }
}
