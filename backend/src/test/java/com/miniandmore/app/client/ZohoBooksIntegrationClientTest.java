package com.miniandmore.app.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.app.service.ZohoSyncService;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ZohoBooksIntegrationClientTest {

    @Mock
    private ZohoSyncService syncService;

    @InjectMocks
    private ZohoBooksIntegrationClient client;

    @Test
    void syncOrder_delegatesToSyncService() {
        client.syncOrder(42L);
        verify(syncService).syncOrder(42L);
    }

    @Test
    void syncProductAsync_delegatesToSyncService() {
        client.syncProductAsync(7L);
        verify(syncService).syncProductAsync(7L);
    }

    @Test
    void pullOrderTracking_delegatesToSyncService() {
        client.pullOrderTracking(9L);
        verify(syncService).pullOrderTracking(9L);
    }

    @Test
    void createCreditNote_returnsCreditNoteId() {
        when(syncService.createCreditNote(1L, "RET-1")).thenReturn(Optional.of("cn_123"));
        assertThat(client.createCreditNote(1L, "RET-1")).contains("cn_123");
    }

    @Test
    void createCreditNote_withAmount_delegatesWithoutUsingAmount() {
        when(syncService.createCreditNote(1L, "RET-1")).thenReturn(Optional.empty());
        assertThat(client.createCreditNote(1L, "RET-1", BigDecimal.TEN)).isEmpty();
        verify(syncService).createCreditNote(1L, "RET-1");
    }

    @Test
    void createCreditNote_withReturnedQuantities_delegates() {
        when(syncService.createCreditNote(1L, "RET-1", java.util.Map.of(11L, 1))).thenReturn(Optional.of("cn_qty"));
        assertThat(client.createCreditNote(1L, "RET-1", java.util.Map.of(11L, 1))).contains("cn_qty");
    }
}
