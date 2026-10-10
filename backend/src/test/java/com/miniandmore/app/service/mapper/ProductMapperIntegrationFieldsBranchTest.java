package com.miniandmore.app.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.domain.Product;
import com.miniandmore.app.domain.enumeration.LedgerlySyncStatus;
import com.miniandmore.app.domain.enumeration.ZohoSyncStatus;
import com.miniandmore.app.service.dto.ProductDTO;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProductMapperIntegrationFieldsBranchTest {

    @Test
    void partialUpdate_mapsEveryZohoAndLedgerlyField() {
        Product entity = new Product();
        ProductDTO patch = new ProductDTO();
        Instant zohoAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant ledgerlyAt = Instant.parse("2026-02-01T00:00:00Z");
        patch.setZohoItemId("zoho-1");
        patch.setZohoSyncStatus(ZohoSyncStatus.SYNCED);
        patch.setZohoLastSyncedAt(zohoAt);
        patch.setZohoSyncError("zoho-error");
        patch.setLedgerlyItemId("ledgerly-1");
        patch.setLedgerlySyncStatus(LedgerlySyncStatus.SYNCED);
        patch.setLedgerlyLastSyncedAt(ledgerlyAt);
        patch.setLedgerlySyncError("ledgerly-error");

        MapperTestSupport.productMapper().partialUpdate(entity, patch);

        assertThat(entity.getZohoItemId()).isEqualTo("zoho-1");
        assertThat(entity.getZohoSyncStatus()).isEqualTo(ZohoSyncStatus.SYNCED);
        assertThat(entity.getZohoLastSyncedAt()).isEqualTo(zohoAt);
        assertThat(entity.getZohoSyncError()).isEqualTo("zoho-error");
        assertThat(entity.getLedgerlyItemId()).isEqualTo("ledgerly-1");
        assertThat(entity.getLedgerlySyncStatus()).isEqualTo(LedgerlySyncStatus.SYNCED);
        assertThat(entity.getLedgerlyLastSyncedAt()).isEqualTo(ledgerlyAt);
        assertThat(entity.getLedgerlySyncError()).isEqualTo("ledgerly-error");
    }
}
