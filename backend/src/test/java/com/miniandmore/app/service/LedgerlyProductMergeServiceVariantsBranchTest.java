package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.domain.Product;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** Branch coverage for Ledgerly variant merge paths. */
@ExtendWith(MockitoExtension.class)
class LedgerlyProductMergeServiceVariantsBranchTest {

    @Mock
    private LedgerlyItemImageSync itemImageSync;

    @Spy
    private ProductDescriptionCodec descriptionCodec = new ProductDescriptionCodec(new ObjectMapper());

    @Spy
    private ProductVariantSupport variantSupport = StorePricingTestSupport.variantSupport(new ObjectMapper());

    @Spy
    private ProductColorImagesSupport colorImagesSupport = new ProductColorImagesSupport(new ObjectMapper());

    @Mock
    private StorePricingService storePricingService;

    @InjectMocks
    private LedgerlyProductMergeService mergeService;

    @BeforeEach
    void stubPricing() {
        lenient().when(storePricingService.sellingFromMrp(any())).thenReturn(BigDecimal.TEN);
        lenient().when(storePricingService.mrpFromSelling(any())).thenReturn(BigDecimal.valueOf(12.5));
    }

    @Test
    void applyVariantsFromItem_noStructureOrVariants_isNoOp() {
        Product product = new Product().sku("PLAIN").variantsJson("[]");
        ReflectionTestUtils.invokeMethod(mergeService, "applyVariantsFromItem", product, Map.of("name", "x"));
        assertThat(product.getVariantsJson()).isEqualTo("[]");
    }

    @Test
    void applyVariantsFromItem_infersVariantsStructureFromJson() {
        Product product = new Product().sku("VAR");
        Map<String, Object> item = Map.of(
            "variants_json",
            "[{\"sku\":\"VAR-S\",\"size\":\"S\",\"color\":\"Red\",\"rate\":10,\"stock\":1,\"active\":true}]"
        );
        ReflectionTestUtils.invokeMethod(mergeService, "applyVariantsFromItem", product, item);
        assertThat(product.getItemStructure()).isEqualTo(ProductVariantSupport.STRUCTURE_VARIANTS);
        assertThat(product.getVariantsJson()).contains("VAR-S");
    }

    @Test
    void applyVariantsFromItem_preservesPhotosWhenSizeColorUnchanged() {
        Product product = new Product()
            .sku("KEEP-PHOTO")
            .itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS)
            .variantsJson(
                """
                [{"sku":"K-S","size":"S","color":"Blue","rate":10,"stock":1,"active":true,
                  "imageUrl":"https://cdn.example.com/old.jpg"}]
                """
            );
        Map<String, Object> item = Map.of(
            "itemStructure",
            ProductVariantSupport.STRUCTURE_VARIANTS,
            "variantsJson",
            "[{\"sku\":\"K-S\",\"size\":\"S\",\"color\":\"Blue\",\"rate\":11,\"stock\":2,\"active\":true}]"
        );
        ReflectionTestUtils.invokeMethod(mergeService, "applyVariantsFromItem", product, item);
        assertThat(product.getVariantsJson()).contains("https://cdn.example.com/old.jpg").contains("\"stock\":2");
    }

    @Test
    void applyVariantsFromItem_dropsPhotosWhenSizeColorChanged() {
        Product product = new Product()
            .sku("DROP-PHOTO")
            .itemStructure(ProductVariantSupport.STRUCTURE_VARIANTS)
            .variantsJson(
                """
                [{"sku":"D-S","size":"S","color":"Blue","rate":10,"stock":1,"active":true,
                  "imageUrl":"https://cdn.example.com/old.jpg"}]
                """
            );
        Map<String, Object> item = Map.of(
            "itemStructure",
            ProductVariantSupport.STRUCTURE_VARIANTS,
            "variantsJson",
            "[{\"sku\":\"D-S\",\"size\":\"M\",\"color\":\"Blue\",\"rate\":10,\"stock\":1,\"active\":true}]"
        );
        ReflectionTestUtils.invokeMethod(mergeService, "applyVariantsFromItem", product, item);
        assertThat(product.getVariantsJson()).doesNotContain("old.jpg");
    }

    @Test
    void applyVariantsFromItem_badMatrix_keepsParentStructureOnly() {
        Product product = new Product().sku("BAD");
        Map<String, Object> item = Map.of(
            "itemStructure",
            ProductVariantSupport.STRUCTURE_VARIANTS,
            "variantsJson",
            "not-json"
        );
        ReflectionTestUtils.invokeMethod(mergeService, "applyVariantsFromItem", product, item);
        assertThat(product.getItemStructure()).isEqualTo(ProductVariantSupport.STRUCTURE_VARIANTS);
    }
}
