package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.app.client.ZohoBooksClient;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
class ZohoCatalogStockAdjusterTest {

    @Mock
    private ZohoBooksClient booksClient;

    @Mock
    private ZohoBooksAccountResolver accountResolver;

    private ZohoCatalogStockAdjuster adjuster;

    @BeforeEach
    void setUp() {
        adjuster = new ZohoCatalogStockAdjuster(booksClient, accountResolver);
    }

    @Test
    void adjustItemQuantity_whenSyncDisabled_isNoOpSuccess() {
        when(accountResolver.isStockAdjustmentEnabled()).thenReturn(false);

        assertThat(adjuster.adjustItemQuantity("zoho-1", 5, BigDecimal.TEN, "REF", "label")).isTrue();
        verify(booksClient, never()).getItem(any());
    }

    @Test
    void adjustItemQuantity_blankZohoItemId_isNoOpSuccess() {
        when(accountResolver.isStockAdjustmentEnabled()).thenReturn(true);

        assertThat(adjuster.adjustItemQuantity(" ", 5, BigDecimal.TEN, "REF", "label")).isTrue();
        verify(booksClient, never()).getItem(any());
    }

    @Test
    void adjustItemQuantity_matchingStock_skipsAdjustment() {
        when(accountResolver.isStockAdjustmentEnabled()).thenReturn(true);
        when(booksClient.getItem("zoho-2"))
            .thenReturn(Map.of("item", Map.of("item_id", "zoho-2", "item_type", "inventory", "stock_on_hand", 7, "rate", 100)));

        assertThat(adjuster.adjustItemQuantity("zoho-2", 7, BigDecimal.valueOf(100), "STORE", "product")).isTrue();
        verify(booksClient, never()).createInventoryAdjustment(any());
    }

    @Test
    void adjustItemQuantity_delta_postsInventoryAdjustment() {
        when(accountResolver.isStockAdjustmentEnabled()).thenReturn(true);
        when(accountResolver.resolveAdjustmentAccountId()).thenReturn(Optional.of("adj-acct"));
        when(accountResolver.resolveLocationId()).thenReturn(Optional.of("loc-1"));
        when(booksClient.getItem("zoho-3"))
            .thenReturn(Map.of("item", Map.of("item_id", "zoho-3", "item_type", "inventory", "stock_on_hand", 2, "rate", 50)));
        when(booksClient.createInventoryAdjustment(any())).thenReturn(Map.of("code", 0));

        assertThat(adjuster.adjustItemQuantity("zoho-3", 5, BigDecimal.valueOf(50), "STORE-STOCK", "product 3")).isTrue();

        verify(booksClient).createInventoryAdjustment(
            argThat(payload -> {
                Object lines = payload.get("line_items");
                return lines instanceof java.util.List<?> list &&
                    !list.isEmpty() &&
                    list.get(0) instanceof Map<?, ?> line &&
                    "zoho-3".equals(line.get("item_id")) &&
                    Integer.valueOf(3).equals(line.get("quantity_adjusted"));
            })
        );
    }

    @Test
    void adjustItemQuantity_missingItem_returnsFalse() {
        when(accountResolver.isStockAdjustmentEnabled()).thenReturn(true);
        when(booksClient.getItem("missing")).thenReturn(Map.of());

        assertThat(adjuster.adjustItemQuantity("missing", 1, BigDecimal.ONE, "REF", "label")).isFalse();
    }

    @Test
    void adjustItemQuantity_salesItem_enablesInventoryThenAdjusts() {
        when(accountResolver.isStockAdjustmentEnabled()).thenReturn(true);
        when(accountResolver.resolveInventoryAccountId()).thenReturn(java.util.Optional.of("inv-acct"));
        when(accountResolver.resolveAdjustmentAccountId()).thenReturn(java.util.Optional.of("adj-acct"));
        when(accountResolver.resolveLocationId()).thenReturn(java.util.Optional.of("loc-1"));
        when(booksClient.getItem("zoho-sales"))
            .thenReturn(Map.of("item", Map.of("item_id", "zoho-sales", "item_type", "sales", "stock_on_hand", 1, "rate", 25, "name", "Tee", "sku", "SKU-T")))
            .thenReturn(Map.of("item", Map.of("item_id", "zoho-sales", "item_type", "inventory", "stock_on_hand", 1, "rate", 25)));
        when(booksClient.updateItem(eq("zoho-sales"), any())).thenReturn(Map.of("item", Map.of("item_id", "zoho-sales")));
        when(booksClient.createInventoryAdjustment(any())).thenReturn(Map.of("code", 0));

        assertThat(adjuster.adjustItemQuantity("zoho-sales", 4, BigDecimal.valueOf(25), "STORE", "tee")).isTrue();

        verify(booksClient).updateItem(eq("zoho-sales"), any());
        verify(booksClient).createInventoryAdjustment(any());
    }

    @Test
    void adjustItemQuantity_trackInventoryFlag_treatedAsInventory() {
        when(accountResolver.isStockAdjustmentEnabled()).thenReturn(true);
        when(accountResolver.resolveAdjustmentAccountId()).thenReturn(Optional.of("adj-acct"));
        when(booksClient.getItem("zoho-track"))
            .thenReturn(
                Map.of("item", Map.of("item_id", "zoho-track", "item_type", "sales", "track_inventory", true, "stock_on_hand", 0, "rate", 10))
            );
        when(booksClient.createInventoryAdjustment(any())).thenReturn(Map.of("code", 0));

        assertThat(adjuster.adjustItemQuantity("zoho-track", 2, null, "REF", "tracked")).isTrue();
        verify(booksClient).createInventoryAdjustment(any());
    }

    @Test
    void adjustItemQuantity_noAdjustmentAccount_skipsCreate() {
        when(accountResolver.isStockAdjustmentEnabled()).thenReturn(true);
        when(accountResolver.resolveAdjustmentAccountId()).thenReturn(Optional.empty());
        when(booksClient.getItem("zoho-4"))
            .thenReturn(Map.of("item", Map.of("item_id", "zoho-4", "item_type", "inventory", "stock_on_hand", 1, "rate", 5)));

        assertThat(adjuster.adjustItemQuantity("zoho-4", 3, BigDecimal.valueOf(5), "LONG-REF-PREFIX-".repeat(5), "product")).isTrue();
        verify(booksClient, never()).createInventoryAdjustment(any());
    }

    @Test
    void adjustItemQuantity_invalidAdjustmentAccount_retriesWithReplacement() {
        when(accountResolver.isStockAdjustmentEnabled()).thenReturn(true);
        when(accountResolver.resolveAdjustmentAccountId()).thenReturn(Optional.of("bad-acct"), Optional.of("good-acct"));
        when(booksClient.getItem("zoho-5"))
            .thenReturn(Map.of("item", Map.of("item_id", "zoho-5", "item_type", "inventory", "stock_on_hand", 1, "rate", 20)));
        RestClientResponseException invalidAccount = new RestClientResponseException(
            "bad account",
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            null,
            "Account does not exist".getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8
        );
        when(booksClient.createInventoryAdjustment(any()))
            .thenThrow(invalidAccount)
            .thenReturn(Map.of("code", 0));

        assertThat(adjuster.adjustItemQuantity("zoho-5", 4, BigDecimal.valueOf(20), "STORE", "retry")).isTrue();
        verify(accountResolver).markConfiguredAdjustmentAccountRejected();
        verify(booksClient, times(2)).createInventoryAdjustment(any());
    }

    @Test
    void adjustItemQuantity_runtimeFailure_returnsFalse() {
        when(accountResolver.isStockAdjustmentEnabled()).thenReturn(true);
        when(booksClient.getItem("zoho-boom")).thenThrow(new IllegalStateException("zoho down"));

        assertThat(adjuster.adjustItemQuantity("zoho-boom", 1, BigDecimal.ONE, "REF", "boom")).isFalse();
    }

    @Test
    void adjustItemQuantity_enableInventoryWithoutLocation_usesUpdateOnly() {
        when(accountResolver.isStockAdjustmentEnabled()).thenReturn(true);
        when(accountResolver.resolveInventoryAccountId()).thenReturn(Optional.of("inv-acct"));
        when(accountResolver.resolveLocationId()).thenReturn(Optional.empty());
        when(accountResolver.resolveAdjustmentAccountId()).thenReturn(Optional.of("adj-acct"));
        when(booksClient.getItem("zoho-noloc"))
            .thenReturn(
                Map.of(
                    "item",
                    Map.of("item_id", "zoho-noloc", "item_type", "sales", "stock_on_hand", 0, "rate", 15, "name", "Hat", "unit", "pcs")
                )
            )
            .thenReturn(Map.of("item", Map.of("item_id", "zoho-noloc", "item_type", "inventory", "stock_on_hand", 0, "rate", 15)));
        when(booksClient.updateItem(eq("zoho-noloc"), any())).thenReturn(Map.of("item", Map.of("item_id", "zoho-noloc")));
        when(booksClient.createInventoryAdjustment(any())).thenReturn(Map.of("code", 0));

        assertThat(adjuster.adjustItemQuantity("zoho-noloc", 2, BigDecimal.ZERO, "REF", "hat")).isTrue();
        verify(booksClient).updateItem(eq("zoho-noloc"), argThat(p -> !p.containsKey("locations")));
    }

    @Test
    void adjustItemQuantity_invalidLocation_retriesAfterMarkingLocationRejected() {
        when(accountResolver.isStockAdjustmentEnabled()).thenReturn(true);
        when(accountResolver.resolveAdjustmentAccountId()).thenReturn(Optional.of("adj-acct"));
        when(accountResolver.resolveLocationId()).thenReturn(Optional.of("bad-loc"));
        when(booksClient.getItem("zoho-loc"))
            .thenReturn(Map.of("item", Map.of("item_id", "zoho-loc", "item_type", "inventory", "stock_on_hand", 1, "rate", 10)));
        RestClientResponseException invalidLocation = new RestClientResponseException(
            "invalid location",
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            null,
            "The location does not exist".getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8
        );
        when(booksClient.createInventoryAdjustment(any())).thenThrow(invalidLocation).thenReturn(Map.of("code", 0));

        assertThat(adjuster.adjustItemQuantity("zoho-loc", 3, BigDecimal.TEN, "STORE", "loc-retry")).isTrue();

        verify(accountResolver).markConfiguredLocationRejected();
        verify(booksClient, times(2)).createInventoryAdjustment(any());
    }

    @Test
    void adjustItemQuantity_enableInventoryFailsStillNotInventory_returnsFalse() {
        when(accountResolver.isStockAdjustmentEnabled()).thenReturn(true);
        when(accountResolver.resolveInventoryAccountId()).thenReturn(Optional.of("inv-acct"));
        when(accountResolver.resolveLocationId()).thenReturn(Optional.of("loc-1"));
        when(booksClient.getItem("zoho-sales-only"))
            .thenReturn(Map.of("item", Map.of("item_id", "zoho-sales-only", "item_type", "sales", "stock_on_hand", 0, "rate", 5, "name", "Sale")));
        when(booksClient.updateItem(eq("zoho-sales-only"), any())).thenThrow(new IllegalStateException("zoho rejected"));

        assertThat(adjuster.adjustItemQuantity("zoho-sales-only", 2, BigDecimal.valueOf(5), "REF", "non-inv")).isFalse();
        verify(booksClient, never()).createInventoryAdjustment(any());
    }

    @Test
    void adjustItemQuantity_veryLongReferencePrefix_truncatesToFiftyChars() {
        when(accountResolver.isStockAdjustmentEnabled()).thenReturn(true);
        when(accountResolver.resolveAdjustmentAccountId()).thenReturn(Optional.of("adj-acct"));
        when(booksClient.getItem("zoho-ref"))
            .thenReturn(Map.of("item", Map.of("item_id", "zoho-ref", "item_type", "inventory", "stock_on_hand", 0, "rate", 1)));
        when(booksClient.createInventoryAdjustment(any())).thenReturn(Map.of("code", 0));

        String longPrefix = "X".repeat(80);
        assertThat(adjuster.adjustItemQuantity("zoho-ref", 1, BigDecimal.ONE, longPrefix, "ref")).isTrue();

        verify(booksClient)
            .createInventoryAdjustment(argThat(payload -> {
                String ref = String.valueOf(payload.get("reference_number"));
                return ref.length() <= 50;
            }));
    }
}
