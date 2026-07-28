package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.domain.Category;
import com.miniandmore.app.domain.Product;
import java.math.BigDecimal;
import java.util.List;
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

    @Spy
    private ProductVariantSupport variantSupport = new ProductVariantSupport(new ObjectMapper());

    @Spy
    private ProductColorImagesSupport colorImagesSupport = new ProductColorImagesSupport(new ObjectMapper());

    @InjectMocks
    private LedgerlyProductMergeService mergeService;

    @Test
    void buildPushPayload_includesCostUnitTaxAndPlainDescription() {
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
        assertThat(payload.get("price")).isEqualTo(BigDecimal.valueOf(499));
        assertThat(payload.get("mrp")).isEqualTo(BigDecimal.valueOf(699));
        assertThat(payload.get("compareAtPrice")).isEqualTo(BigDecimal.valueOf(699));
        assertThat(payload.get("unit")).isEqualTo("ea");
        assertThat(payload.get("cost")).isEqualTo(new BigDecimal("299.40"));
        assertThat(payload.get("imageUrl")).isEqualTo("https://cdn.example.com/p.jpg");
        assertThat(payload.get("description")).isEqualTo("Soft");
        assertThat(payload.get("active")).isEqualTo(true);
        assertThat(payload).doesNotContainKey("hsn");
        assertThat(payload).doesNotContainKey("taxPct");
        assertThat(payload.get("itemStructure")).isEqualTo("SIMPLE");
    }

    @Test
    void buildPushPayload_includesHsnAndTaxPctWhenSet() {
        when(itemImageSync.firstImageUrl(any())).thenReturn(null);
        Product product = new Product()
            .sku("MNM-TAX")
            .name("Taxed")
            .price(BigDecimal.valueOf(400))
            .compareAtPrice(BigDecimal.valueOf(500))
            .stock(1)
            .hsn("6109")
            .taxPct(new BigDecimal("5.00"))
            .active(true);

        Map<String, Object> payload = mergeService.buildPushPayload(product, "Baby");

        assertThat(payload.get("hsn")).isEqualTo("6109");
        assertThat((BigDecimal) payload.get("taxPct")).isEqualByComparingTo("5.00");
    }

    @Test
    void mergeAndPush_variantsMatrix() {
        when(itemImageSync.firstImageUrl(any())).thenReturn(null);
        Product product = new Product().sku("HORIZON").name("Horizon Tee").price(BigDecimal.ZERO).stock(0).active(true);
        String variantsJson =
            "[{\"sku\":\"HORIZON-S-NAVY\",\"size\":\"S\",\"color\":\"Navy\",\"rate\":999,\"stock\":3,\"active\":true}," +
            "{\"sku\":\"HORIZON-M-NAVY\",\"size\":\"M\",\"color\":\"Navy\",\"rate\":1199,\"stock\":4,\"active\":true}]";
        Map<String, Object> item = Map.of(
            "price",
            999,
            "stock",
            7,
            "itemStructure",
            "VARIANTS",
            "variantsJson",
            variantsJson
        );

        mergeService.mergeLedgerlyItem(product, item, name -> Optional.empty(), null);

        assertThat(product.getItemStructure()).isEqualTo("VARIANTS");
        assertThat(product.getStock()).isEqualTo(7);
        assertThat(product.getCompareAtPrice()).isEqualByComparingTo("999");
        assertThat(product.getPrice()).isEqualByComparingTo("799.20");

        Map<String, Object> payload = mergeService.buildPushPayload(product, "Baby");
        assertThat(payload.get("itemStructure")).isEqualTo("VARIANTS");
        assertThat(payload.get("variantsJson").toString()).contains("HORIZON-M-NAVY");
    }

    @Test
    void mergeLedgerlyItem_appliesTopLevelMerchandisingFields() {
        Product product = new Product().sku("SKU-1").price(BigDecimal.ZERO).stock(0);
        Category category = new Category().name("Babywear").slug("babywear");
        Map<String, Object> item = Map.of(
            "price",
            499,
            "mrp",
            699,
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

        assertThat(product.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(499));
        assertThat(product.getStock()).isEqualTo(8);
        assertThat(product.getCompareAtPrice()).isEqualByComparingTo(BigDecimal.valueOf(699));
        assertThat(product.getTaxPct()).isNull();
        assertThat(product.getCategory()).isEqualTo(category);
        assertThat(product.getFabricType()).isEqualTo("Organic Cotton");
        assertThat(product.getSizesJson()).contains("0-3M").contains("3-6M");
        assertThat(product.getColorsJson()).contains("Blush").contains("Sand");
    }

    @Test
    void mergeLedgerlyItem_mapsSellingAndMrpSeparately() {
        Product product = new Product().sku("SKU-1").price(BigDecimal.ZERO).stock(0);
        Map<String, Object> item = Map.of("price", 499, "mrp", 699, "cost", 150, "stock", 5);

        mergeService.mergeLedgerlyItem(product, item, name -> Optional.empty(), null);

        assertThat(product.getPrice()).isEqualByComparingTo(new BigDecimal("499"));
        assertThat(product.getCompareAtPrice()).isEqualByComparingTo(BigDecimal.valueOf(699));
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

        assertThat(product.getPrice()).isEqualByComparingTo(new BigDecimal("499"));
        assertThat(product.getCompareAtPrice()).isEqualByComparingTo(BigDecimal.valueOf(799));
    }

    @Test
    void mergeLedgerlyItem_decodesDescriptionAndSetsPrice() {
        Product product = new Product().sku("SKU-1").price(BigDecimal.ZERO).stock(0);
        Map<String, Object> item = Map.of(
            "price",
            120,
            "mrp",
            150,
            "stock",
            4,
            "ledgerlyItemId",
            "42",
            "description",
            "Category: Essentials\nFabric: Wool\n\nBody text"
        );

        mergeService.mergeLedgerlyItem(product, item, name -> Optional.empty(), null);

        assertThat(product.getPrice()).isEqualByComparingTo(new BigDecimal("120"));
        assertThat(product.getCompareAtPrice()).isEqualByComparingTo(BigDecimal.valueOf(150));
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
    void mergeLedgerlyItem_allowsMissingDescription() {
        Product product = new Product().sku("SKU-NODESC").price(BigDecimal.ZERO).stock(0);
        Map<String, Object> item = Map.of("price", 100, "stock", 2, "name", "No Desc Item");

        mergeService.mergeLedgerlyItem(product, item, name -> Optional.empty(), null);

        assertThat(product.getDescription()).isNull();
        assertThat(product.getPrice()).isEqualByComparingTo("100");
        assertThat(product.getStock()).isEqualTo(2);
    }

    @Test
    void mergeLedgerlyItem_allowsBlankDescription() {
        Product product = new Product().sku("SKU-BLANK").price(BigDecimal.ZERO).stock(0);
        Map<String, Object> item = Map.of("price", 100, "stock", 1, "description", "   ");

        mergeService.mergeLedgerlyItem(product, item, name -> Optional.empty(), null);

        assertThat(product.getDescription()).isNull();
    }

    @Test
    void buildPushPayload_omitsBlankDescription() {
        when(itemImageSync.firstImageUrl(any())).thenReturn(null);
        Product product = new Product()
            .sku("MNM-NODESC")
            .name("No Desc")
            .price(BigDecimal.valueOf(199))
            .stock(3)
            .description("  ")
            .active(true);

        Map<String, Object> payload = mergeService.buildPushPayload(product, "Baby");

        assertThat(payload).doesNotContainKey("description");
        assertThat(payload.get("sku")).isEqualTo("MNM-NODESC");
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

        assertThat(payload.get("price")).isEqualTo(BigDecimal.valueOf(499));
        assertThat(payload.get("mrp")).isEqualTo(BigDecimal.valueOf(699));
        assertThat(payload.get("cost")).isEqualTo(new BigDecimal("299.40"));
    }

    @Test
    void buildPushPayload_includesColorGalleryMap() {
        when(itemImageSync.firstImageUrl(any())).thenReturn(null);
        Product product = new Product()
            .sku("MNM-GALLERY")
            .name("Gallery Tee")
            .price(BigDecimal.valueOf(499))
            .stock(5)
            .active(true)
            .colorsJson(
                "[{\"name\":\"Print 1\",\"swatchUrl\":\"https://cdn.example.com/swatch.jpg\",\"imageUrls\":[\"https://cdn.example.com/g1.jpg\"]}]"
            );

        Map<String, Object> payload = mergeService.buildPushPayload(product, "Baby");

        @SuppressWarnings("unchecked")
        Map<String, List<String>> colorImages = (Map<String, List<String>>) payload.get("colorImagesJson");
        assertThat(colorImages).containsEntry("Print 1", List.of("https://cdn.example.com/g1.jpg"));
        assertThat(colorImages.get("Print 1")).doesNotContain("https://cdn.example.com/swatch.jpg");
    }
}
