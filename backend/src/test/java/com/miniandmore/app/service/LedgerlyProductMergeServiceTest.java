package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.domain.Category;
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
            .description("Soft")
            .active(true);

        Map<String, Object> payload = mergeService.buildPushPayload(product, "Baby");

        assertThat(payload.get("sku")).isEqualTo("MNM-1");
        assertThat(payload.get("price")).isEqualTo(BigDecimal.valueOf(699));
        assertThat(payload.get("unit")).isEqualTo("ea");
        assertThat(payload.get("cost")).isEqualTo(new BigDecimal("419.40"));
        assertThat(payload.get("imageUrl")).isEqualTo("https://cdn.example.com/p.jpg");
        assertThat(payload.get("description").toString()).contains("Category: Baby").contains("Fabric: Cotton");
        assertThat(payload.get("active")).isEqualTo(true);
        assertThat(payload).doesNotContainKey("hsn");
    }

    @Test
    void mergeLedgerlyItem_appliesTopLevelMerchandisingFields() {
        Product product = new Product().sku("SKU-1").price(BigDecimal.ZERO).stock(0);
        Category category = new Category().name("Babywear").slug("babywear");
        Map<String, Object> item = Map.of(
            "price",
            499,
            "stock",
            8,
            "taxPct",
            12,
            "category",
            "Babywear",
            "sizes",
            "0-3M, 3-6M",
            "colors",
            "Blush, Sand",
            "fabricType",
            "Organic Cotton"
        );

        mergeService.mergeLedgerlyItem(product, item, name -> Optional.of(category), null);

        assertThat(product.getPrice()).isEqualByComparingTo(new BigDecimal("399.20"));
        assertThat(product.getStock()).isEqualTo(8);
        assertThat(product.getCompareAtPrice()).isEqualByComparingTo(BigDecimal.valueOf(499));
        assertThat(product.getTaxPct()).isNull();
        assertThat(product.getCategory()).isEqualTo(category);
        assertThat(product.getFabricType()).isEqualTo("Organic Cotton");
        assertThat(product.getSizesJson()).contains("0-3M").contains("3-6M");
        assertThat(product.getColorsJson()).contains("Blush").contains("Sand");
    }

    @Test
    void mergeLedgerlyItem_usesSellingPriceForCompareAt_notPurchaseCost() {
        Product product = new Product().sku("SKU-1").price(BigDecimal.ZERO).stock(0);
        Map<String, Object> item = Map.of("price", 499, "cost", 150, "stock", 5);

        mergeService.mergeLedgerlyItem(product, item, name -> Optional.empty(), null);

        assertThat(product.getPrice()).isEqualByComparingTo(new BigDecimal("399.20"));
        assertThat(product.getCompareAtPrice()).isEqualByComparingTo(BigDecimal.valueOf(499));
    }

    @Test
    void mergeLedgerlyItem_prefersCompareAtLineInDescription() {
        Product product = new Product().sku("SKU-1").price(BigDecimal.ZERO).stock(0);
        Map<String, Object> item = Map.of(
            "price",
            499,
            "cost",
            150,
            "stock",
            5,
            "description",
            "CompareAt: 799\n\nSoft cotton"
        );

        mergeService.mergeLedgerlyItem(product, item, name -> Optional.empty(), null);

        assertThat(product.getPrice()).isEqualByComparingTo(new BigDecimal("639.20"));
        assertThat(product.getCompareAtPrice()).isEqualByComparingTo(BigDecimal.valueOf(799));
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

        assertThat(product.getPrice()).isEqualByComparingTo(new BigDecimal("96.00"));
        assertThat(product.getCompareAtPrice()).isEqualByComparingTo(BigDecimal.valueOf(120));
        assertThat(product.getStock()).isEqualTo(4);
        assertThat(product.getLedgerlyItemId()).isEqualTo("42");
        assertThat(product.getFabricType()).isEqualTo("Wool");
        assertThat(product.getDescription()).isEqualTo("Body text");
    }

    @Test
    void mergeLedgerlyItem_importsImageWhenPresent() {
        Product product = new Product().sku("SKU-1").price(BigDecimal.ZERO).stock(0);
        Map<String, Object> item = Map.of("price", 100, "stock", 1, "imageUrl", "https://ledgerly.example.com/item.jpg");

        mergeService.mergeLedgerlyItem(product, item, name -> Optional.empty(), null);

        verify(itemImageSync).importItemImage(product, "https://ledgerly.example.com/item.jpg");
    }

    @Test
    void mergeLedgerlyItem_appliesAgeFromItemFields() {
        Product product = new Product().sku("SKU-1").price(BigDecimal.ZERO).stock(0);
        Map<String, Object> item = Map.of("price", 100, "stock", 1, "ageMinMonths", 0, "ageMaxMonths", 6);

        mergeService.mergeLedgerlyItem(product, item, name -> Optional.empty(), null);

        assertThat(product.getAgeMinMonths()).isEqualTo(0);
        assertThat(product.getAgeMaxMonths()).isEqualTo(6);
    }

    @Test
    void buildPushPayload_usesSellingPriceForLedgerlyRateAndEstimatedCost() {
        when(itemImageSync.firstImageUrl(any())).thenReturn(null);
        Product product = new Product()
            .sku("MNM-2")
            .name("Onesie")
            .price(BigDecimal.valueOf(499))
            .compareAtPrice(BigDecimal.valueOf(699))
            .stock(10);

        Map<String, Object> payload = mergeService.buildPushPayload(product, "Baby");

        assertThat(payload.get("price")).isEqualTo(BigDecimal.valueOf(699));
        assertThat(payload.get("cost")).isEqualTo(new BigDecimal("419.40"));
    }
}
