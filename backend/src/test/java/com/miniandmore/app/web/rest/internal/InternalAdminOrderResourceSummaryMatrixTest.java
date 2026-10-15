package com.miniandmore.app.web.rest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.enumeration.LedgerlySyncStatus;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.domain.enumeration.ZohoSyncStatus;
import com.miniandmore.app.repository.OrderItemRepository;
import com.miniandmore.app.service.LedgerlySyncService;
import com.miniandmore.app.service.OrderCancellationService;
import com.miniandmore.app.service.OrderEventService;
import com.miniandmore.app.service.OrderPackageService;
import com.miniandmore.app.service.OrderShipmentService;
import com.miniandmore.app.repository.StoreAddressRepository;
import com.miniandmore.app.service.ZohoSyncService;
import com.miniandmore.app.web.rest.v1.AccountOrderDataAccess;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InternalAdminOrderResourceSummaryMatrixTest {

    private InternalAdminOrderResource resource;

    @BeforeEach
    void setUp() {
        AccountOrderDataAccess dataAccess = mock(AccountOrderDataAccess.class);
        OrderItemRepository items = mock(OrderItemRepository.class);
        when(dataAccess.orderItemRepository()).thenReturn(items);
        when(items.findAllByOrderId(any())).thenReturn(List.of());
        OrderCancellationService cancellations = mock(OrderCancellationService.class);
        when(cancellations.hasPendingRequest(any())).thenReturn(false);
        when(cancellations.canCancel(any())).thenReturn(false);
        OrderShipmentService shipments = mock(OrderShipmentService.class);
        when(shipments.canBook(any())).thenReturn(false);
        when(shipments.canBookWithOverride(any())).thenReturn(false);
        when(shipments.canBookExpressBees(any())).thenReturn(false);
        when(shipments.canBookExpressBeesWithOverride(any())).thenReturn(false);
        OrderPackageService packages = mock(OrderPackageService.class);
        when(packages.canCompletePackage(any())).thenReturn(false);
        when(packages.defaultPackageWeightGrams(any())).thenReturn(250);
        resource = new InternalAdminOrderResource(
            dataAccess,
            new ApplicationProperties(),
            mock(LedgerlySyncService.class),
            mock(ZohoSyncService.class),
            new ObjectMapper(),
            mock(OrderEventService.class),
            mock(InternalAccountOrderResource.class),
            shipments,
            packages,
            cancellations,
            mock(StoreAddressRepository.class)
        );
    }

    @Test
    void summary_nullEnumsAndTimestampsBecomeEmptyStrings() {
        CustomerOrder order = new CustomerOrder().id(3L).orderNumber("MNM-3");
        @SuppressWarnings("unchecked")
        Map<String, Object> dto = (Map<String, Object>) ReflectionTestUtils.invokeMethod(resource, "summary", order);
        assertThat(dto.get("status")).isEqualTo("");
        assertThat(dto.get("payment_status")).isEqualTo("");
        assertThat(dto.get("placed_at")).isEqualTo("");
        assertThat(dto.get("zoho_sync_status")).isEqualTo("");
        assertThat(dto.get("ledgerly_sync_status")).isEqualTo("");
        assertThat(dto.get("zoho_last_synced_at")).isEqualTo("");
        assertThat((Boolean) dto.get("zoho_sync_eligible")).isFalse();
    }

    @Test
    void summary_populatedEnumsAndCancelledEligibility() {
        CustomerOrder order = new CustomerOrder()
            .id(4L)
            .orderNumber("MNM-4")
            .status(OrderStatus.CANCELLED)
            .paymentStatus(PaymentStatus.PAID)
            .paymentMethod("ONLINE")
            .zohoSyncStatus(ZohoSyncStatus.SYNCED)
            .ledgerlySyncStatus(LedgerlySyncStatus.FAILED)
            .placedAt(Instant.parse("2026-01-02T00:00:00Z"))
            .zohoLastSyncedAt(Instant.parse("2026-01-03T00:00:00Z"))
            .zohoSalesOrderId("SO-1");
        @SuppressWarnings("unchecked")
        Map<String, Object> dto = (Map<String, Object>) ReflectionTestUtils.invokeMethod(resource, "summary", order);
        assertThat(dto.get("status")).isEqualTo("cancelled");
        assertThat(dto.get("payment_status")).isEqualTo("paid");
        assertThat(dto.get("payment_method")).isEqualTo("online");
        assertThat(dto.get("zoho_sync_status")).isEqualTo("synced");
        assertThat(dto.get("ledgerly_sync_status")).isEqualTo("failed");
        assertThat((Boolean) dto.get("zoho_sync_eligible")).isTrue();
        assertThat((Boolean) dto.get("can_mark_cod_collected")).isFalse();
    }

    @Test
    void summary_canMarkCodCollectedOnlyForOpenPendingCod() {
        CustomerOrder pendingCod = new CustomerOrder()
            .id(5L)
            .orderNumber("MNM-5")
            .status(OrderStatus.CONFIRMED)
            .paymentStatus(PaymentStatus.PENDING)
            .paymentMethod("cod");
        @SuppressWarnings("unchecked")
        Map<String, Object> openDto = (Map<String, Object>) ReflectionTestUtils.invokeMethod(resource, "summary", pendingCod);
        assertThat((Boolean) openDto.get("can_mark_cod_collected")).isTrue();

        CustomerOrder cancelledCod = new CustomerOrder()
            .id(6L)
            .orderNumber("MNM-6")
            .status(OrderStatus.CANCELLED)
            .paymentStatus(PaymentStatus.PENDING)
            .paymentMethod("cod");
        @SuppressWarnings("unchecked")
        Map<String, Object> cancelledDto = (Map<String, Object>) ReflectionTestUtils.invokeMethod(resource, "summary", cancelledCod);
        assertThat((Boolean) cancelledDto.get("can_mark_cod_collected")).isFalse();
    }

    @Test
    void isZohoSyncEligible_coversCancelledWithoutIdsAndCodConfirmed() {
        CustomerOrder cancelled = new CustomerOrder().status(OrderStatus.CANCELLED);
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(InternalAdminOrderResource.class, "isZohoSyncEligible", cancelled)).isFalse();
        cancelled.setZohoInvoiceId("INV-1");
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(InternalAdminOrderResource.class, "isZohoSyncEligible", cancelled)).isTrue();

        CustomerOrder cod = new CustomerOrder().status(OrderStatus.CONFIRMED).paymentMethod("cod").paymentStatus(PaymentStatus.PENDING);
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(InternalAdminOrderResource.class, "isZohoSyncEligible", cod)).isTrue();
        CustomerOrder wrapped = new CustomerOrder().status(OrderStatus.HAND_WRAPPED).paymentMethod("COD").paymentStatus(PaymentStatus.PENDING);
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(InternalAdminOrderResource.class, "isZohoSyncEligible", wrapped)).isTrue();
    }
}
