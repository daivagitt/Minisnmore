package com.miniandmore.app.service;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerlyInventoryPullSchedulerTest {

    @Mock
    private LedgerlySyncService ledgerlySyncService;

    @InjectMocks
    private LedgerlyInventoryPullScheduler scheduler;

    @Test
    void pullInventory_delegatesToSyncService() {
        scheduler.pullInventory();

        verify(ledgerlySyncService).pullInventory();
    }

    @Test
    void pullInventory_swallowsFailures() {
        doThrow(new RuntimeException("ledgerly down")).when(ledgerlySyncService).pullInventory();

        scheduler.pullInventory();

        verify(ledgerlySyncService).pullInventory();
    }
}
