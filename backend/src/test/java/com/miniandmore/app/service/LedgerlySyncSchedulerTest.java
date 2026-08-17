package com.miniandmore.app.service;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.enumeration.LedgerlySyncStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerlySyncSchedulerTest {

    @Mock
    private CustomerOrderRepository customerOrderRepository;

    @Mock
    private LedgerlySyncService ledgerlySyncService;

    @InjectMocks
    private LedgerlySyncScheduler scheduler;

    @Test
    void retryFailedOrderSync_retriesFailedAndPendingOrders() {
        CustomerOrder failed = new CustomerOrder().id(1L).ledgerlySyncStatus(LedgerlySyncStatus.FAILED);
        CustomerOrder pending = new CustomerOrder().id(2L).ledgerlySyncStatus(LedgerlySyncStatus.PENDING);
        CustomerOrder synced = new CustomerOrder().id(3L).ledgerlySyncStatus(LedgerlySyncStatus.SYNCED);
        when(customerOrderRepository.findAll()).thenReturn(List.of(failed, pending, synced));

        scheduler.retryFailedOrderSync();

        verify(ledgerlySyncService).syncOrder(1L);
        verify(ledgerlySyncService).syncOrder(2L);
    }

    @Test
    void retryFailedOrderSync_continuesWhenOneOrderFails() {
        CustomerOrder failed = new CustomerOrder().id(4L).ledgerlySyncStatus(LedgerlySyncStatus.FAILED);
        CustomerOrder pending = new CustomerOrder().id(5L).ledgerlySyncStatus(LedgerlySyncStatus.PENDING);
        when(customerOrderRepository.findAll()).thenReturn(List.of(failed, pending));
        doThrow(new RuntimeException("sync down")).when(ledgerlySyncService).syncOrder(4L);

        scheduler.retryFailedOrderSync();

        verify(ledgerlySyncService).syncOrder(4L);
        verify(ledgerlySyncService).syncOrder(5L);
    }

    @Test
    void pullOpenOrderTracking_pullsEveryOrder() {
        CustomerOrder first = new CustomerOrder().id(10L);
        CustomerOrder second = new CustomerOrder().id(11L);
        when(customerOrderRepository.findAll()).thenReturn(List.of(first, second));

        scheduler.pullOpenOrderTracking();

        verify(ledgerlySyncService).pullOrderTracking(10L);
        verify(ledgerlySyncService).pullOrderTracking(11L);
    }

    @Test
    void pullOpenOrderTracking_continuesWhenOnePullFails() {
        CustomerOrder first = new CustomerOrder().id(12L);
        CustomerOrder second = new CustomerOrder().id(13L);
        when(customerOrderRepository.findAll()).thenReturn(List.of(first, second));
        doThrow(new RuntimeException("pull down")).when(ledgerlySyncService).pullOrderTracking(12L);

        scheduler.pullOpenOrderTracking();

        verify(ledgerlySyncService).pullOrderTracking(12L);
        verify(ledgerlySyncService).pullOrderTracking(13L);
    }
}
