package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.repository.ProductRepository;
import com.miniandmore.app.service.dto.ProductDTO;
import com.miniandmore.app.service.mapper.ProductMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductServiceVariantsNormalizationTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductMapper productMapper;

    @Spy
    private ProductVariantSupport variantSupport = StorePricingTestSupport.variantSupport(new ObjectMapper());

    @Spy
    private ProductColorImagesSupport colorImagesSupport = new ProductColorImagesSupport(new ObjectMapper());

    @InjectMocks
    private ProductService productService;

    @Test
    void update_preservesPrintSwatchesWhenVariantMatrixRebuildsColors() {
        String swatch = "https://cdn.example.com/bee-swatch.jpg";
        String colorsJson =
            "[{\"name\":\"BEE\",\"swatchUrl\":\"" + swatch + "\"},{\"name\":\"BLUE\",\"swatchUrl\":\"https://cdn.example.com/blue.jpg\"}]";
        String variantsJson =
            "[{\"sku\":\"SKU-BEE-S\",\"size\":\"0-3m\",\"color\":\"BEE\",\"rate\":999,\"stock\":2,\"active\":true}," +
            "{\"sku\":\"SKU-BLUE-S\",\"size\":\"0-3m\",\"color\":\"BLUE\",\"rate\":999,\"stock\":1,\"active\":true}]";

        ProductDTO input = new ProductDTO();
        input.setId(1L);
        input.setItemStructure("VARIANTS");
        input.setColorsJson(colorsJson);
        input.setVariantsJson(variantsJson);
        input.setImagesJson("[]");

        Product entity = new Product()
            .id(1L)
            .itemStructure("VARIANTS")
            .colorsJson(colorsJson)
            .variantsJson(variantsJson)
            .imagesJson("[]");

        Product existing = new Product()
            .id(1L)
            .itemStructure("VARIANTS")
            .colorsJson(colorsJson)
            .variantsJson(variantsJson)
            .imagesJson("[]");

        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productMapper.toEntity(input)).thenReturn(entity);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productMapper.toDto(any(Product.class))).thenReturn(input);

        productService.update(input);

        assertThat(entity.getColorsJson()).contains("swatchUrl").contains("bee-swatch.jpg").contains("blue.jpg");
        assertThat(entity.getImagesJson()).contains("bee-swatch.jpg");
    }

    @Test
    void update_freezesVariantColorNamesAndStoredSwatches() {
        String storedSwatch = "https://cdn.example.com/aqua-swatch.jpg";
        String tamperedSwatch = "https://cdn.example.com/tampered-swatch.jpg";
        String storedVariants =
            "[{\"sku\":\"SKU-AQUA-S\",\"size\":\"0-3m\",\"color\":\"AQUA\",\"rate\":999,\"stock\":2,\"active\":true}]";
        String storedColors = "[{\"name\":\"AQUA\",\"swatchUrl\":\"" + storedSwatch + "\"}]";
        String tamperedVariants =
            "[{\"sku\":\"SKU-AQUA-S\",\"size\":\"0-3m\",\"color\":\"Manual Blue\",\"rate\":999,\"stock\":2,\"active\":true}]";
        String adminColors = "[{\"name\":\"Manual Blue\",\"swatchUrl\":\"" + tamperedSwatch + "\"}]";

        Product existing = new Product()
            .id(1L)
            .itemStructure("VARIANTS")
            .variantsJson(storedVariants)
            .colorsJson(storedColors)
            .imagesJson("[]");

        ProductDTO input = new ProductDTO();
        input.setId(1L);
        input.setItemStructure("VARIANTS");
        input.setColorsJson(adminColors);
        input.setVariantsJson(tamperedVariants);
        input.setImagesJson("[]");

        Product entity = new Product()
            .id(1L)
            .itemStructure("VARIANTS")
            .colorsJson(adminColors)
            .variantsJson(tamperedVariants)
            .imagesJson("[]");

        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productMapper.toEntity(input)).thenReturn(entity);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productMapper.toDto(any(Product.class))).thenReturn(input);

        productService.update(input);

        assertThat(entity.getVariantsJson()).contains("AQUA").doesNotContain("Manual Blue");
        assertThat(entity.getColorsJson()).contains("AQUA").contains(storedSwatch).doesNotContain(tamperedSwatch);
    }
}
