package com.miniandmore.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.miniandmore.integration.config.ApplicationProperties;
import com.miniandmore.integration.domain.Product;
import com.miniandmore.integration.domain.ZohoSetting;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ZohoStockSyncTest {

    @Mock
    private ZohoBooksClient zohoBooksClient;

    private ApplicationProperties applicationProperties;
    private ZohoStockSync stockSync;
    private ObjectMapper objectMapper;
    private ZohoSetting setting;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        applicationProperties = new ApplicationProperties();
        stockSync = new ZohoStockSync(objectMapper, applicationProperties, zohoBooksClient);
        setting = new ZohoSetting();
        setting.setConnected(true);
    }

    @Test
    void readZohoStock_fromTopLevelAndLocations() {
        ObjectNode item = objectMapper.createObjectNode();
        item.put("stock_on_hand", 5);
        assertThat(ZohoStockSync.readZohoStock(item, null)).isEqualTo(5);

        ObjectNode withLocations = objectMapper.createObjectNode();
        var locations = withLocations.putArray("locations");
        ObjectNode loc = locations.addObject();
        loc.put("location_id", "loc-1");
        loc.put("location_stock_on_hand", "12");
        assertThat(ZohoStockSync.readZohoStock(withLocations, "loc-1")).isEqualTo(12);
    }

    @Test
    void enrichItemPayload_skipsWhenStockSyncDisabled() {
        applicationProperties.getZoho().setSyncProductStock(false);
        ObjectNode payload = objectMapper.createObjectNode();
        Product product = productWithStock(3);
        stockSync.enrichItemPayload(payload, setting, product, true);
        assertThat(payload.has("locations")).isFalse();
    }

    @Test
    void enrichItemPayload_addsOpeningStockWithConfiguredLocation() {
        applicationProperties.getZoho().setDefaultLocationId("loc-99");
        applicationProperties.getZoho().setInventoryAccountId("acct-1");
        ObjectNode payload = objectMapper.createObjectNode();
        Product product = productWithStock(4);
        stockSync.enrichItemPayload(payload, setting, product, true);
        assertThat(payload.path("item_type").asText()).isEqualTo("inventory");
        assertThat(payload.path("locations").get(0).path("initial_stock").asText()).isEqualTo("4");
    }

    @Test
    void reconcileStock_noOpWhenAlreadyMatched() {
        applicationProperties.getZoho().setDefaultLocationId("loc-1");
        Product product = productWithStock(10);
        ObjectNode zohoItem = objectMapper.createObjectNode();
        zohoItem.put("stock_on_hand", 10);
        stockSync.reconcileStock(setting, "item-1", product, zohoItem);
        verify(zohoBooksClient, never()).post(any(), any(), any(), any());
    }

    @Test
    void resolveLocationId_fromApi() {
        ObjectNode response = objectMapper.createObjectNode();
        var locations = response.putArray("locations");
        ObjectNode loc = locations.addObject();
        loc.put("location_id", "loc-primary");
        loc.put("location_name", "Head Office");
        loc.put("is_primary", true);
        when(zohoBooksClient.get(eq(setting), eq("/locations"), any())).thenReturn(response);

        assertThat(stockSync.resolveLocationId(setting)).contains("loc-primary");
    }

    private static Product productWithStock(int stock) {
        Product product = new Product();
        product.setId(1L);
        product.setName("Dress");
        product.setStock(stock);
        product.setPrice(BigDecimal.TEN);
        return product;
    }
}
