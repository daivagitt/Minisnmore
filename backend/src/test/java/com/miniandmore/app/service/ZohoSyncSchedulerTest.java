package com.miniandmore.app.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.domain.enumeration.ZohoSyncStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ZohoSyncSchedulerTest {

    @Mock
    private CustomerOrderRepository customerOrderRepository;

    @Mock
    private ZohoSyncService zohoSyncService;

    private ApplicationProperties applicationProperties;
    private ZohoSyncScheduler scheduler;

    @BeforeEach
    void setUp() {
        applicationProperties = new ApplicationProperties();
        applicationProperties.getZoho().getOrderTracking().setEnabled(true);
        scheduler = new ZohoSyncScheduler(customerOrderRepository, applicationProperties, zohoSyncService);
    }

    @Test
    void retryFailedOrderSync_retriesPaidAndCodOrdersAndSwallowsFailures() {
        CustomerOrder paid = new CustomerOrder().id(1L);
        CustomerOrder cod = new CustomerOrder().id(2L);
        when(
            customerOrderRepository.findPaidOrdersForZohoRetry(
                eq(PaymentStatus.PAID),
                eq(List.of(ZohoSyncStatus.FAILED, ZohoSyncStatus.PENDING)),
                any(Pageable.class)
            )
        ).thenReturn(new PageImpl<>(List.of(paid)));
        when(
            customerOrderRepository.findConfirmedCodOrdersForZohoRetry(
                eq(List.of(ZohoSyncStatus.FAILED, ZohoSyncStatus.PENDING)),
                any(Pageable.class)
            )
        ).thenReturn(new PageImpl<>(List.of(cod)));
        doThrow(new IllegalStateException("boom")).when(zohoSyncService).syncOrder(1L);

        scheduler.retryFailedOrderSync();

        verify(zohoSyncService).syncOrder(1L);
        verify(zohoSyncService).syncOrder(2L);
    }

    @Test
    void pullOpenOrderTracking_skipsWhenDisabled() {
        applicationProperties.getZoho().getOrderTracking().setEnabled(false);

        scheduler.pullOpenOrderTracking();

        verify(customerOrderRepository, never()).findOrdersForZohoTrackingPull(any(), any(), any(), any());
    }

    @Test
    void pullOpenOrderTracking_pullsTrackingForOpenOrders() {
        CustomerOrder order = new CustomerOrder().id(9L);
        when(
            customerOrderRepository.findOrdersForZohoTrackingPull(
                eq(PaymentStatus.PAID),
                eq(List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.HAND_WRAPPED)),
                eq(OrderStatus.FULFILLED),
                any(Pageable.class)
            )
        ).thenReturn(new PageImpl<>(List.of(order)));

        scheduler.pullOpenOrderTracking();

        verify(zohoSyncService).pullOrderTracking(9L);
    }

    @Test
    void pullOpenOrderTracking_swallowsPullFailures() {
        CustomerOrder order = new CustomerOrder().id(11L);
        when(
            customerOrderRepository.findOrdersForZohoTrackingPull(any(), any(), any(), any(Pageable.class))
        ).thenReturn(new PageImpl<>(List.of(order)));
        doThrow(new RuntimeException("offline")).when(zohoSyncService).pullOrderTracking(11L);

        scheduler.pullOpenOrderTracking();

        verify(zohoSyncService).pullOrderTracking(11L);
    }
}
