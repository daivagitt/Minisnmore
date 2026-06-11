package com.miniandmore.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
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
class ZohoStockSyncExtendedTest {

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
    }

    @Test
    void reconcileStock_postsAdjustmentWhenDifferent() {
        applicationProperties.getZoho().setDefaultLocationId("loc-1");
        Product product = product(5);
        ObjectNode zohoItem = objectMapper.createObjectNode();
        zohoItem.put("stock_on_hand", 1);

        stockSync.reconcileStock(setting, "item-5", product, zohoItem);

        verify(zohoBooksClient).post(eq(setting), eq("/inventoryadjustments"), any(), any());
    }

    @Test
    void reconcileStock_logsWarningOnFailure() {
        applicationProperties.getZoho().setDefaultLocationId("loc-1");
        Product product = product(6);
        ObjectNode zohoItem = objectMapper.createObjectNode();
        zohoItem.put("stock_on_hand", 0);
        when(zohoBooksClient.post(any(), any(), any(), any())).thenThrow(new ZohoBooksException("adjustment failed"));

        stockSync.reconcileStock(setting, "item-6", product, zohoItem);
    }

    @Test
    void enrichInventoryItemType_resolvesInventoryAccountId_fromChartOfAccounts() {
        ObjectNode response = objectMapper.createObjectNode();
        var accounts = response.putArray("chartofaccounts");
        accounts.addObject().put("account_id", "a1").put("account_name", "Cash");
        accounts.addObject().put("account_id", "a2").put("account_name", "Inventory Asset");
        when(zohoBooksClient.get(eq(setting), eq("/chartofaccounts"), any())).thenReturn(response);

        stockSync.enrichInventoryItemType(objectMapper.createObjectNode(), setting);

        verify(zohoBooksClient).get(eq(setting), eq("/chartofaccounts"), any());
    }

    @Test
    void resolveLocationId_prefersPrimaryLocation() {
        ObjectNode response = objectMapper.createObjectNode();
        var locations = response.putArray("locations");
        locations.addObject().put("location_id", "loc-a").put("location_name", "Other");
        locations.addObject().put("location_id", "loc-b").put("location_name", "Head Office").put("is_primary", true);
        when(zohoBooksClient.get(eq(setting), eq("/locations"), any())).thenReturn(response);

        assertThat(stockSync.resolveLocationId(setting)).contains("loc-b");
        assertThat(stockSync.resolveLocationId(setting)).contains("loc-b");
        verify(zohoBooksClient, times(1)).get(eq(setting), eq("/locations"), any());
    }

    private static Product product(int stock) {
        Product product = new Product();
        product.setId(1L);
        product.setName("Item");
        product.setStock(stock);
        product.setPrice(BigDecimal.ONE);
        return product;
    }
}
