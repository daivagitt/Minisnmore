package com.miniandmore.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.miniandmore.integration.config.ApplicationProperties;
import com.miniandmore.integration.domain.Product;
import com.miniandmore.integration.domain.ZohoSetting;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ZohoStockSyncReadStockTest {

    @Mock
    private ZohoBooksClient zohoBooksClient;

    @Test
    void readZohoStock_allSources() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode top = mapper.createObjectNode();
        top.put("available_stock", 7);
        assertThat(ZohoStockSync.readZohoStock(top, null)).isEqualTo(7);

        ObjectNode locations = mapper.createObjectNode();
        var arr = locations.putArray("locations");
        arr.addObject().put("location_id", "loc-1").put("location_stock_on_hand", "3.0");
        arr.addObject().put("location_id", "loc-2").put("location_stock_on_hand", "bad");
        assertThat(ZohoStockSync.readZohoStock(locations, "loc-1")).isEqualTo(3);
    }

    @Test
    void resolveLocationId_byNameAndPrimary() {
        ApplicationProperties props = new ApplicationProperties();
        props.getZoho().setDefaultLocationName("Warehouse");
        ZohoStockSync sync = new ZohoStockSync(new ObjectMapper(), props, zohoBooksClient);
        ObjectNode response = new ObjectMapper().createObjectNode();
        var locations = response.putArray("locations");
        locations.addObject().put("location_id", "loc-a").put("location_name", "Other");
        locations.addObject().put("location_id", "loc-b").put("location_name", "Head Office").put("is_primary", true);
        locations.addObject().put("location_id", "loc-w").put("location_name", "Main Warehouse");
        when(zohoBooksClient.get(any(), any(), any())).thenReturn(response);

        assertThat(sync.resolveLocationId(new ZohoSetting())).contains("loc-w");
        assertThat(sync.resolveLocationId(new ZohoSetting())).contains("loc-w");
    }

    @Test
    void enrichItemPayload_skipsNullStock() {
        ApplicationProperties props = new ApplicationProperties();
        props.getZoho().setSyncProductStock(true);
        props.getZoho().setInventoryAccountId("acct");
        ZohoStockSync sync = new ZohoStockSync(new ObjectMapper(), props, zohoBooksClient);
        Product product = new Product();
        product.setStock(null);
        ObjectNode payload = new ObjectMapper().createObjectNode();
        sync.enrichItemPayload(payload, new ZohoSetting(), product, true);
        assertThat(payload.has("locations")).isFalse();
    }
}
