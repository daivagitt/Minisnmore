package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.domain.Product;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerlyProductMergeServiceTest {

    @Mock
    private LedgerlyItemImageSync itemImageSync;

    @Spy
    private ProductDescriptionCodec descriptionCodec = new ProductDescriptionCodec(new ObjectMapper());

    @InjectMocks
    private LedgerlyProductMergeService mergeService;

    @Test
    void buildPushPayload_includesCostUnitTaxAndEncodedDescription() {
        when(itemImageSync.firstImageUrl(any())).thenReturn("https://cdn.example.com/p.jpg");
        Product product = new Product()
            .sku("MNM-1")
            .name("Onesie")
            .price(BigDecimal.valueOf(499))
            .stock(10)
            .compareAtPrice(BigDecimal.valueOf(699))
            .fabricType("Cotton")
            .description("Soft");

        Map<String, Object> payload = mergeService.buildPushPayload(product, "Baby");

        assertThat(payload.get("sku")).isEqualTo("MNM-1");
        assertThat(payload.get("unit")).isEqualTo("ea");
        assertThat(payload.get("taxPct")).isEqualTo(BigDecimal.valueOf(18));
        assertThat(payload.get("cost")).isEqualTo(BigDecimal.valueOf(699));
        assertThat(payload.get("imageUrl")).isEqualTo("https://cdn.example.com/p.jpg");
        assertThat(payload.get("description").toString()).contains("Category: Baby").contains("Fabric: Cotton");
    }

    @Test
    void mergeLedgerlyItem_decodesDescriptionAndSetsPrice() {
        Product product = new Product().sku("SKU-1").price(BigDecimal.ZERO).stock(0);
        Map<String, Object> item = Map.of(
            "price",
            120,
            "stock",
            4,
            "ledgerlyItemId",
            "42",
            "description",
            "Category: Essentials\nFabric: Wool\n\nBody text"
        );

        mergeService.mergeLedgerlyItem(product, item, name -> Optional.empty(), null);

        assertThat(product.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(120));
        assertThat(product.getStock()).isEqualTo(4);
        assertThat(product.getLedgerlyItemId()).isEqualTo("42");
        assertThat(product.getFabricType()).isEqualTo("Wool");
        assertThat(product.getDescription()).isEqualTo("Body text");
    }
}
