package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.app.domain.Product;
import com.miniandmore.app.repository.ProductRepository;
import com.miniandmore.app.service.mapper.ProductMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductServiceNormalizeVariantsMatrixTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private ProductVariantSupport variantSupport;

    @Mock
    private ProductColorImagesSupport colorImagesSupport;

    private ProductService service;

    @BeforeEach
    void setUp() {
        service = new ProductService(productRepository, productMapper, variantSupport, colorImagesSupport);
        when(colorImagesSupport.galleryMapFromColorsJson(any())).thenReturn(Map.of());
        when(colorImagesSupport.parseMap(any())).thenReturn(Map.of());
        when(colorImagesSupport.applyGalleryMapToColorsJson(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(colorImagesSupport.mergeFrozenColorMedia(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(colorImagesSupport.freezeSwatchesFromStored(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(colorImagesSupport.buildPositionalColorRename(any(), any())).thenReturn(Map.of());
        when(variantSupport.isVariantsStructure(any())).thenReturn(false);
        when(variantSupport.applyMatrixToProduct(any(), any(), any())).thenReturn(true);
    }

    @Test
    void normalizeVariants_nullProductReturns() {
        ReflectionTestUtils.invokeMethod(service, "normalizeVariants", null, "{}", "{}");
        verify(variantSupport, never()).isVariantsStructure(any());
    }

    @Test
    void normalizeVariants_blankUnitsAndStructureBecomeDefaults() {
        Product product = new Product();
        product.setUnits("  ");
        product.setItemStructure("");
        product.setColorsJson("{}");

        ReflectionTestUtils.invokeMethod(service, "normalizeVariants", product, null, null);

        assertThat(product.getUnits()).isEqualTo("ea");
        assertThat(product.getItemStructure()).isEqualTo(ProductVariantSupport.STRUCTURE_SIMPLE);
        assertThat(product.getVariantsJson()).isNull();
        verify(colorImagesSupport).syncProductColorImages(product);
    }

    @Test
    void normalizeVariants_emptyGalleryFallsBackToColorImagesJson() {
        Product product = new Product();
        product.setUnits("pcs");
        product.setItemStructure(ProductVariantSupport.STRUCTURE_SIMPLE);
        product.setColorImagesJson("{\"Red\":[\"a.jpg\"]}");
        when(colorImagesSupport.galleryMapFromColorsJson(any())).thenReturn(Map.of());
        when(colorImagesSupport.parseMap("{\"Red\":[\"a.jpg\"]}")).thenReturn(Map.of("Red", List.of("a.jpg")));

        ReflectionTestUtils.invokeMethod(service, "normalizeVariants", product, null, null);

        verify(colorImagesSupport).parseMap("{\"Red\":[\"a.jpg\"]}");
        verify(variantSupport, never()).applyMatrixToProduct(any(), any(), any());
    }

    @Test
    void normalizeVariants_variantsStructureAppliesMatrixAndGallery() {
        Product product = new Product();
        product.setItemStructure(ProductVariantSupport.STRUCTURE_VARIANTS);
        product.setVariantsJson("[]");
        product.setColorsJson("{\"nodes\":[]}");
        when(variantSupport.isVariantsStructure(ProductVariantSupport.STRUCTURE_VARIANTS)).thenReturn(true);
        when(colorImagesSupport.galleryMapFromColorsJson(any())).thenReturn(Map.of("Blue", List.of("b.jpg")));
        when(variantSupport.applyMatrixToProduct(product, ProductVariantSupport.STRUCTURE_VARIANTS, "[]")).thenReturn(true);

        ReflectionTestUtils.invokeMethod(service, "normalizeVariants", product, "{\"frozen\":true}", "{\"swatch\":true}");

        verify(variantSupport).freezeVariantColorsFromStored(product, "{\"frozen\":true}");
        verify(colorImagesSupport).applyGalleryMapToColorsJson(any(), any());
        verify(colorImagesSupport).mergeFrozenColorMedia(any(), any());
        verify(colorImagesSupport).freezeSwatchesFromStored(any(), anyString());
        verify(variantSupport).renameVariantColors(any(), any());
    }

    @Test
    void normalizeVariants_invalidMatrixThrows() {
        Product product = new Product();
        product.setItemStructure("VARIANTS");
        product.setVariantsJson("{}");
        when(variantSupport.isVariantsStructure("VARIANTS")).thenReturn(true);
        when(variantSupport.applyMatrixToProduct(product, "VARIANTS", "{}")).thenReturn(false);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "normalizeVariants", product, null, null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Invalid variants matrix");
    }
}
