package com.miniandmore.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.miniandmore.integration.config.ApplicationProperties;
import com.miniandmore.integration.domain.Product;
import com.miniandmore.integration.domain.ZohoSetting;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ZohoStockSyncFullTest {

    @Mock
    private ZohoBooksClient zohoBooksClient;

    private ApplicationProperties props;
    private ZohoStockSync stockSync;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        props = new ApplicationProperties();
        props.getZoho().setSyncProductStock(true);
        stockSync = new ZohoStockSync(mapper, props, zohoBooksClient);
    }

    @Test
    void enrichItemPayload_addsOpeningStock() {
        props.getZoho().setDefaultLocationId("loc-1");
        props.getZoho().setInventoryAccountId("acct-inv");
        Product product = product(5);
        ObjectNode payload = mapper.createObjectNode();
        stockSync.enrichItemPayload(payload, new ZohoSetting(), product, true);
        assertThat(payload.path("item_type").asText()).isEqualTo("inventory");
        assertThat(payload.path("locations").isArray()).isTrue();
    }

    @Test
    void enrichItemPayload_skipsWhenStockSyncDisabled() {
        props.getZoho().setSyncProductStock(false);
        ObjectNode payload = mapper.createObjectNode();
        stockSync.enrichItemPayload(payload, new ZohoSetting(), product(1), true);
        assertThat(payload.has("item_type")).isFalse();
    }

    @Test
    void readZohoStock_fromLocationArray() {
        ObjectNode item = mapper.createObjectNode();
        var locations = item.putArray("locations");
        locations.addObject().put("location_id", "loc-a").put("location_stock_on_hand", "4");
        assertThat(ZohoStockSync.readZohoStock(item, "loc-a")).isEqualTo(4);
    }

    @Test
    void resolveInventoryAccountId_matchesStockAccountName() {
        props.getZoho().setInventoryAccountId(null);
        ObjectNode response = mapper.createObjectNode();
        var accounts = response.putArray("chartofaccounts");
        accounts.addObject().put("account_id", "a1").put("account_name", "Finished Stock");
        when(zohoBooksClient.get(any(), eq("/chartofaccounts"), any())).thenReturn(response);
        ObjectNode payload = mapper.createObjectNode();
        stockSync.enrichInventoryItemType(payload, new ZohoSetting());
        assertThat(payload.path("inventory_account_id").asText()).isEqualTo("a1");
    }

    @Test
    void reconcileStock_noOpWhenDeltaZero() {
        props.getZoho().setDefaultLocationId("loc-1");
        Product product = product(3);
        ObjectNode zohoItem = mapper.createObjectNode();
        zohoItem.put("stock_on_hand", 3);
        stockSync.reconcileStock(new ZohoSetting(), "item-3", product, zohoItem);
    }

    private static Product product(int stock) {
        Product p = new Product();
        p.setId(1L);
        p.setName("Item");
        p.setStock(stock);
        p.setPrice(BigDecimal.ONE);
        return p;
    }
}
