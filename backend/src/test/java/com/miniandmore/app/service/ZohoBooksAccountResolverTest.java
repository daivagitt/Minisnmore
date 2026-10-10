package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.miniandmore.app.client.ZohoBooksClient;
import com.miniandmore.app.config.ApplicationProperties;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientResponseException;

@ExtendWith(MockitoExtension.class)
class ZohoBooksAccountResolverTest {

    @Mock
    private ZohoBooksClient booksClient;

    private ApplicationProperties properties;
    private ZohoBooksAccountResolver resolver;

    @BeforeEach
    void setUp() {
        properties = new ApplicationProperties();
        ApplicationProperties.Zoho zoho = properties.getZoho();
        zoho.setSyncProductStock(true);
        resolver = new ZohoBooksAccountResolver(booksClient, properties);
    }

    @Test
    void isStockAdjustmentEnabled_falseWhenSyncDisabled() {
        properties.getZoho().setSyncProductStock(false);
        assertThat(resolver.isStockAdjustmentEnabled()).isFalse();
    }

    @Test
    void resolveInventoryAccountId_usesConfiguredAccountWhenPresentInOrg() {
        properties.getZoho().setInventoryAccountId("inv-configured");
        when(booksClient.listAssetAccounts())
            .thenReturn(List.of(Map.of("account_id", "inv-configured", "account_name", "Inventory Asset")));

        assertThat(resolver.resolveInventoryAccountId()).contains("inv-configured");
    }

    @Test
    void resolveInventoryAccountId_fallsBackToInventoryAssetName() {
        when(booksClient.listAssetAccounts())
            .thenReturn(
                List.of(
                    Map.of("account_id", "other", "account_name", "Cash"),
                    Map.of("account_id", "inv-found", "account_name", "Inventory Asset")
                )
            );

        assertThat(resolver.resolveInventoryAccountId()).contains("inv-found");
    }

    @Test
    void resolveAdjustmentAccountId_prefersCostOfGoodsSoldName() {
        when(booksClient.listExpenseAccounts())
            .thenReturn(
                List.of(
                    Map.of("account_id", "exp-1", "account_name", "Office Supplies"),
                    Map.of("account_id", "cogs-1", "account_name", "Cost of Goods Sold")
                )
            );

        assertThat(resolver.resolveAdjustmentAccountId()).contains("cogs-1");
    }

    @Test
    void resolveLocationId_prefersPrimaryFlag() {
        properties.getZoho().setDefaultLocationName("Warehouse");
        when(booksClient.listLocations())
            .thenReturn(
                List.of(
                    Map.of("location_id", "loc-2", "location_name", "Secondary", "is_primary", false),
                    Map.of("location_id", "loc-primary", "location_name", "Head Office", "is_primary", true)
                )
            );

        assertThat(resolver.resolveLocationId()).contains("loc-primary");
    }

    @Test
    void resolveLocationId_usesConfiguredIdWhenLocationsListEmpty() {
        properties.getZoho().setDefaultLocationId("loc-configured");
        when(booksClient.listLocations()).thenReturn(List.of());

        assertThat(resolver.resolveLocationId()).contains("loc-configured");
    }

    @Test
    void isInvalidAdjustmentAccount_detectsMissingAccountMessage() {
        RestClientResponseException ex = new RestClientResponseException(
            "Bad Request",
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            null,
            "{\"code\":2035,\"message\":\"Account does not exist\"}".getBytes(),
            null
        );

        assertThat(ZohoBooksAccountResolver.isInvalidAdjustmentAccount(ex)).isTrue();
        assertThat(ZohoBooksAccountResolver.isInvalidLocation(ex)).isFalse();
    }

    @Test
    void isInvalidLocation_detectsLocationValidationMessage() {
        RestClientResponseException ex = new RestClientResponseException(
            "Bad Request",
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            null,
            "{\"message\":\"Please select a valid location\"}".getBytes(),
            null
        );

        assertThat(ZohoBooksAccountResolver.isInvalidLocation(ex)).isTrue();
    }

    @Test
    void resolveInventoryAccountId_rejectsUnknownConfiguredAccountThenFallsBack() {
        properties.getZoho().setInventoryAccountId("missing-inv");
        when(booksClient.listAssetAccounts())
            .thenReturn(List.of(Map.of("account_id", "other", "account_name", "Cash")))
            .thenReturn(List.of(Map.of("account_id", "inv-stock", "account_name", "Stock on Hand")));

        assertThat(resolver.resolveInventoryAccountId()).contains("inv-stock");
        assertThat(resolver.resolveInventoryAccountId()).contains("inv-stock");
    }

    @Test
    void resolveInventoryAccountId_prefersInventoryNameOverStock() {
        when(booksClient.listAssetAccounts())
            .thenReturn(
                List.of(
                    Map.of("account_id", "stock-1", "account_name", "Stock Asset"),
                    Map.of("account_id", "inv-1", "account_name", "Inventory")
                )
            );

        assertThat(resolver.resolveInventoryAccountId()).contains("inv-1");
    }

    @Test
    void resolveAdjustmentAccountId_rejectsUnknownConfiguredAccount() {
        properties.getZoho().setInventoryAdjustmentAccountId("missing-adj");
        when(booksClient.listExpenseAccounts())
            .thenReturn(List.of(Map.of("account_id", "exp-fallback", "account_name", "Misc Expense")));

        assertThat(resolver.resolveAdjustmentAccountId()).contains("exp-fallback");
    }

    @Test
    void resolveAdjustmentAccountId_listFailure_returnsEmpty() {
        when(booksClient.listExpenseAccounts()).thenThrow(new IllegalStateException("Zoho down"));

        assertThat(resolver.resolveAdjustmentAccountId()).isEmpty();
    }

    @Test
    void resolveLocationId_matchesPreferredNameSubstring() {
        properties.getZoho().setDefaultLocationName("warehouse");
        when(booksClient.listLocations())
            .thenReturn(
                List.of(
                    Map.of("location_id", "loc-1", "location_name", "Main Warehouse East", "is_primary", false),
                    Map.of("location_id", "loc-2", "location_name", "Retail", "is_primary", true)
                )
            );

        assertThat(resolver.resolveLocationId()).contains("loc-1");
    }

    @Test
    void resolveLocationId_rejectsUnknownConfiguredIdThenUsesPrimary() {
        properties.getZoho().setDefaultLocationId("loc-missing");
        when(booksClient.listLocations())
            .thenReturn(List.of(Map.of("location_id", "loc-primary", "location_name", "HQ", "is_primary", "true")));

        assertThat(resolver.resolveLocationId()).contains("loc-primary");
    }

    @Test
    void resolveLocationId_listFailure_usesConfiguredWhenStillAllowed() {
        properties.getZoho().setDefaultLocationId("loc-fallback");
        when(booksClient.listLocations()).thenThrow(new IllegalStateException("timeout"));

        assertThat(resolver.resolveLocationId()).contains("loc-fallback");
    }

    @Test
    void markConfiguredFlags_clearCaches() {
        properties.getZoho().setInventoryAccountId("inv-1");
        when(booksClient.listAssetAccounts()).thenReturn(List.of(Map.of("account_id", "inv-1", "account_name", "Inventory")));
        assertThat(resolver.resolveInventoryAccountId()).contains("inv-1");

        resolver.markConfiguredInventoryAccountRejected();
        when(booksClient.listAssetAccounts()).thenReturn(List.of(Map.of("account_id", "inv-2", "account_name", "Inventory Asset")));
        assertThat(resolver.resolveInventoryAccountId()).contains("inv-2");
    }

    @Test
    void resolveInventoryAccountId_syncDisabledWithoutConfigured_returnsEmpty() {
        properties.getZoho().setSyncProductStock(false);
        properties.getZoho().setInventoryAccountId(null);
        assertThat(resolver.resolveInventoryAccountId()).isEmpty();
    }

    @Test
    void resolveAdjustmentAccountId_secondCallUsesCachedAccountId() {
        when(booksClient.listExpenseAccounts())
            .thenReturn(List.of(Map.of("account_id", "exp-cached", "account_name", "Misc Expense")));

        assertThat(resolver.resolveAdjustmentAccountId()).contains("exp-cached");
        assertThat(resolver.resolveAdjustmentAccountId()).contains("exp-cached");
        org.mockito.Mockito.verify(booksClient, org.mockito.Mockito.times(1)).listExpenseAccounts();
    }

    @Test
    void resolveLocationId_secondCallUsesCachedLocationId() {
        when(booksClient.listLocations())
            .thenReturn(List.of(Map.of("location_id", "loc-cached", "location_name", "HQ", "is_primary", true)));

        assertThat(resolver.resolveLocationId()).contains("loc-cached");
        assertThat(resolver.resolveLocationId()).contains("loc-cached");
        org.mockito.Mockito.verify(booksClient, org.mockito.Mockito.times(1)).listLocations();
    }

    @Test
    void resolveAdjustmentAccountId_blankAccountIdInList_skipsAndUsesFallback() {
        when(booksClient.listExpenseAccounts())
            .thenReturn(
                List.of(
                    Map.of("account_id", "  ", "account_name", "Blank"),
                    Map.of("account_id", "exp-real", "account_name", "Real Expense")
                )
            );

        assertThat(resolver.resolveAdjustmentAccountId()).contains("exp-real");
    }

    @Test
    void resolveLocationId_keepsConfiguredIdWhenPresentInOrgList() {
        properties.getZoho().setDefaultLocationId("loc-known");
        when(booksClient.listLocations())
            .thenReturn(List.of(Map.of("location_id", "loc-known", "location_name", "Known", "is_primary", false)));

        assertThat(resolver.resolveLocationId()).contains("loc-known");
    }

    @Test
    void resolveLocationId_emptyListAfterConfiguredRejected_returnsEmpty() {
        properties.getZoho().setDefaultLocationId("loc-missing");
        resolver.markConfiguredLocationRejected();
        when(booksClient.listLocations()).thenReturn(List.of());

        assertThat(resolver.resolveLocationId()).isEmpty();
    }

    @Test
    void resolveLocationId_skipsBlankIdsAndUsesFirstFallback() {
        when(booksClient.listLocations())
            .thenReturn(
                List.of(
                    Map.of("location_id", "  ", "location_name", "Blank"),
                    Map.of("location_id", "loc-fb", "location_name", "Fallback", "is_primary", false)
                )
            );

        assertThat(resolver.resolveLocationId()).contains("loc-fb");
    }

    @Test
    void isInvalidAdjustmentAccount_non400_returnsFalse() {
        RestClientResponseException ex = new RestClientResponseException(
            "Server error",
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "Error",
            null,
            "{}".getBytes(),
            null
        );
        assertThat(ZohoBooksAccountResolver.isInvalidAdjustmentAccount(ex)).isFalse();
    }
}

