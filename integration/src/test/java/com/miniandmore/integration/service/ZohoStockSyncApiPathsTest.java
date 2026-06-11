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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ZohoStockSyncApiPathsTest {

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
    void enrichItemPayload_skipsOpeningStockWhenNotOpening() {
        props.getZoho().setDefaultLocationId("loc");
        props.getZoho().setInventoryAccountId("acct");
        ObjectNode payload = mapper.createObjectNode();
        stockSync.enrichItemPayload(payload, new ZohoSetting(), product(3), false);
        assertThat(payload.has("locations")).isFalse();
    }

    @Test
    void enrichItemPayload_warnsWhenNoLocation() {
        props.getZoho().setInventoryAccountId("acct");
        ObjectNode payload = mapper.createObjectNode();
        ObjectNode response = mapper.createObjectNode();
        response.putArray("locations");
        when(zohoBooksClient.get(any(), eq("/locations"), any())).thenReturn(response);
        stockSync.enrichItemPayload(payload, new ZohoSetting(), product(2), true);
        assertThat(payload.has("locations")).isFalse();
    }

    @Test
    void reconcileStock_disabledOrMissingInputs() {
        props.getZoho().setSyncProductStock(false);
        stockSync.reconcileStock(new ZohoSetting(), "z-1", product(1), mapper.createObjectNode());

        props.getZoho().setSyncProductStock(true);
        stockSync.reconcileStock(new ZohoSetting(), "", product(1), mapper.createObjectNode());

        Product noStock = product(1);
        noStock.setStock(null);
        stockSync.reconcileStock(new ZohoSetting(), "z-1", noStock, mapper.createObjectNode());
    }

    @Test
    void reconcileStock_skipsWhenLocationMissing() {
        ObjectNode response = mapper.createObjectNode();
        response.putArray("locations");
        when(zohoBooksClient.get(any(), eq("/locations"), any())).thenReturn(response);
        stockSync.reconcileStock(new ZohoSetting(), "z-1", product(4), mapper.createObjectNode());
    }

    @Test
    void resolveLocationId_prefersNameMatch() {
        props.getZoho().setDefaultLocationName("Warehouse");
        ObjectNode response = mapper.createObjectNode();
        var locations = response.putArray("locations");
        locations.addObject().put("location_id", "loc-a").put("location_name", "Other");
        locations.addObject().put("location_id", "loc-wh").put("location_name", "Main Warehouse");
        when(zohoBooksClient.get(any(), eq("/locations"), any())).thenReturn(response);

        assertThat(stockSync.resolveLocationId(new ZohoSetting())).contains("loc-wh");
    }

    @Test
    void resolveLocationId_usesPrimaryThenFallback() {
        ObjectNode response = mapper.createObjectNode();
        var locations = response.putArray("locations");
        locations.addObject().put("location_id", "loc-fb").put("location_name", "Secondary");
        locations.addObject().put("location_id", "loc-pr").put("location_name", "Primary").put("is_primary", true);
        when(zohoBooksClient.get(any(), eq("/locations"), any())).thenReturn(response);

        assertThat(stockSync.resolveLocationId(new ZohoSetting())).contains("loc-pr");
    }

    @Test
    void resolveLocationId_apiFailureReturnsEmpty() {
        when(zohoBooksClient.get(any(), eq("/locations"), any())).thenThrow(new RuntimeException("down"));
        assertThat(stockSync.resolveLocationId(new ZohoSetting())).isEmpty();
    }

    @Test
    void resolveLocationId_usesCachedValue() {
        ReflectionTestUtils.setField(stockSync, "cachedLocationId", "cached-loc");
        assertThat(stockSync.resolveLocationId(new ZohoSetting())).contains("cached-loc");
    }

    @Test
    void resolveInventoryAccountId_prefersInventoryName() {
        ObjectNode response = mapper.createObjectNode();
        var accounts = response.putArray("chartofaccounts");
        accounts.addObject().put("account_id", "a-stock").put("account_name", "Raw Stock");
        accounts.addObject().put("account_id", "a-inv").put("account_name", "Inventory Asset");
        when(zohoBooksClient.get(any(), eq("/chartofaccounts"), any())).thenReturn(response);

        assertThat(stockSync.resolveInventoryAccountId(new ZohoSetting())).contains("a-inv");
    }

    @Test
    void resolveInventoryAccountId_apiFailureReturnsEmpty() {
        when(zohoBooksClient.get(any(), eq("/chartofaccounts"), any())).thenThrow(new RuntimeException("down"));
        assertThat(stockSync.resolveInventoryAccountId(new ZohoSetting())).isEmpty();
    }

    @Test
    void resolveInventoryAccountId_usesCachedValue() {
        ReflectionTestUtils.setField(stockSync, "cachedInventoryAccountId", "cached-acct");
        assertThat(stockSync.resolveInventoryAccountId(new ZohoSetting())).contains("cached-acct");
    }

    @Test
    void reconcileStock_postsAdjustmentOnDelta() {
        props.getZoho().setDefaultLocationId("loc-1");
        Product product = product(5);
        ObjectNode zohoItem = mapper.createObjectNode();
        zohoItem.put("stock_on_hand", 1);
        stockSync.reconcileStock(new ZohoSetting(), "item-5", product, zohoItem);
        verify(zohoBooksClient).post(any(), eq("/inventoryadjustments"), any(), any());
    }

    @Test
    void readZohoStock_skipsInvalidLocationQuantity() {
        ObjectNode item = mapper.createObjectNode();
        var locations = item.putArray("locations");
        locations.addObject().put("location_id", "loc-a").put("location_stock_on_hand", "n/a");
        locations.addObject().put("location_id", "loc-b").put("location_stock_on_hand", "3");
        assertThat(ZohoStockSync.readZohoStock(item, "loc-b")).isEqualTo(3);
    }

    @Test
    void readZohoStock_returnsZeroForMissingItem() {
        assertThat(ZohoStockSync.readZohoStock(null, "loc")).isZero();
    }

    @Test
    void enrichItemPayload_skipsWhenItemTypeNotInventory() {
        props.getZoho().setDefaultLocationId("loc");
        props.getZoho().setInventoryAccountId(null);
        when(zohoBooksClient.get(any(), eq("/chartofaccounts"), any())).thenReturn(mapper.createObjectNode());
        ObjectNode payload = mapper.createObjectNode();
        payload.put("item_type", "sales");
        stockSync.enrichItemPayload(payload, new ZohoSetting(), product(2), true);
        assertThat(payload.has("locations")).isFalse();
    }

    @Test
    void readZohoStock_usesFirstLocationWhenNoMatch() {
        ObjectNode item = mapper.createObjectNode();
        var locations = item.putArray("locations");
        locations.addObject().put("location_id", "other").put("location_stock_on_hand", "2");
        assertThat(ZohoStockSync.readZohoStock(item, "missing")).isEqualTo(2);
    }

    @Test
    void readZohoStock_skipsBlankLocationStock() {
        ObjectNode item = mapper.createObjectNode();
        item.putArray("locations").addObject().put("location_id", "loc").put("location_stock_on_hand", " ");
        assertThat(ZohoStockSync.readZohoStock(item, "loc")).isZero();
    }

    @Test
    void enrichInventoryItemType_warnsWhenAccountMissing() {
        ObjectNode payload = mapper.createObjectNode();
        when(zohoBooksClient.get(any(), eq("/chartofaccounts"), any())).thenReturn(mapper.createObjectNode());
        stockSync.enrichInventoryItemType(payload, new ZohoSetting());
        assertThat(payload.has("item_type")).isFalse();
    }

    private static Product product(int stock) {
        Product p = new Product();
        p.setId(1L);
        p.setName("Item");
        p.setStock(stock);
        p.setPrice(null);
        return p;
    }
}
