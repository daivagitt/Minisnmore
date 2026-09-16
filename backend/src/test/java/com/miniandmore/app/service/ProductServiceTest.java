package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.app.domain.Product;
import com.miniandmore.app.repository.ProductRepository;
import com.miniandmore.app.service.dto.ProductDTO;
import com.miniandmore.app.service.mapper.ProductMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private static final Long ID = 1L;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductMapper productMapper;

    @Spy
    private ProductVariantSupport variantSupport = StorePricingTestSupport.variantSupport(new com.fasterxml.jackson.databind.ObjectMapper());

    @Spy
    private ProductColorImagesSupport colorImagesSupport = new ProductColorImagesSupport(new com.fasterxml.jackson.databind.ObjectMapper());

    @InjectMocks
    private ProductService productService;

    @Test
    void invalidLocalChartIsRejectedBeforeAnyProductMutation() {
        ProductDTO input = new ProductDTO();
        input.setId(ID);
        input.setSizeChartJson("{\"sections\":false}");
        assertThatThrownBy(() -> productService.save(input)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> productService.update(input)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> productService.partialUpdate(input)).isInstanceOf(ResponseStatusException.class);
        org.mockito.Mockito.verifyNoInteractions(productMapper, productRepository);
    }

    @Test
    void invalidSizeNotesAreRejectedBeforeAnyProductMutation() {
        ProductDTO input = new ProductDTO();
        input.setId(ID);
        input.setSizeNotesJson("{\"0-3m\":true}");
        assertThatThrownBy(() -> productService.save(input)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> productService.update(input)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> productService.partialUpdate(input)).isInstanceOf(ResponseStatusException.class);
        org.mockito.Mockito.verifyNoInteractions(productMapper, productRepository);
    }

    @Test
    void save() {
        ProductDTO input = new ProductDTO();
        Product entity = new Product().id(ID);
        Product saved = new Product().id(ID);
        ProductDTO expected = new ProductDTO();
        expected.setId(ID);

        when(productMapper.toEntity(input)).thenReturn(entity);
        when(productRepository.save(entity)).thenReturn(saved);
        when(productMapper.toDto(saved)).thenReturn(expected);

        ProductDTO result = productService.save(input);

        assertThat(result).isSameAs(expected);
        verify(productMapper).toEntity(input);
        verify(productRepository).save(entity);
        verify(productMapper).toDto(saved);
    }

    @Test
    void update() {
        ProductDTO input = new ProductDTO();
        input.setId(ID);
        Product entity = new Product().id(ID);
        Product saved = new Product().id(ID);
        ProductDTO expected = new ProductDTO();
        expected.setId(ID);

        when(productMapper.toEntity(input)).thenReturn(entity);
        when(productRepository.save(entity)).thenReturn(saved);
        when(productMapper.toDto(saved)).thenReturn(expected);

        ProductDTO result = productService.update(input);

        assertThat(result).isSameAs(expected);
        verify(productMapper).toEntity(input);
        verify(productRepository).save(entity);
    }

    @Test
    void partialUpdate_found() {
        ProductDTO input = new ProductDTO();
        input.setId(ID);
        Product existing = new Product().id(ID);
        ProductDTO expected = new ProductDTO();
        expected.setId(ID);

        when(productRepository.findById(ID)).thenReturn(Optional.of(existing));
        when(productRepository.save(existing)).thenReturn(existing);
        when(productMapper.toDto(existing)).thenReturn(expected);

        Optional<ProductDTO> result = productService.partialUpdate(input);

        assertThat(result).contains(expected);
        verify(productMapper).partialUpdate(existing, input);
        verify(productRepository).save(existing);
    }

    @Test
    void partialUpdate_notFound() {
        ProductDTO input = new ProductDTO();
        input.setId(ID);
        when(productRepository.findById(ID)).thenReturn(Optional.empty());

        Optional<ProductDTO> result = productService.partialUpdate(input);

        assertThat(result).isEmpty();
    }


    @Test
    void findOne_notFound() {
        when(productRepository.findOneWithEagerRelationships(ID)).thenReturn(Optional.empty());

        Optional<ProductDTO> result = productService.findOne(ID);

        assertThat(result).isEmpty();
    }

    @Test
    void delete() {
        productService.delete(ID);
        verify(productRepository).deleteById(ID);
    }

    @Test
    void clearVariantPhotos_clearsAndSavesWhenVariants() {
        Product entity = new Product()
            .id(ID)
            .itemStructure("VARIANTS")
            .variantsJson("[{\"sku\":\"A\",\"imageUrl\":\"https://cdn.example/a.jpg\"}]");
        ProductDTO dto = new ProductDTO();
        dto.setId(ID);
        when(productRepository.findOneWithEagerRelationships(ID)).thenReturn(Optional.of(entity));
        when(productRepository.save(entity)).thenReturn(entity);
        when(productMapper.toDto(entity)).thenReturn(dto);

        ProductDTO result = productService.clearVariantPhotos(ID);

        assertThat(result).isSameAs(dto);
        assertThat(entity.getVariantsJson()).doesNotContain("imageUrl");
        verify(productRepository).save(entity);
    }

    @Test
    void clearVariantPhotos_missingProduct_throwsNotFound() {
        when(productRepository.findOneWithEagerRelationships(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.clearVariantPhotos(ID))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404 NOT_FOUND")
            .hasMessageContaining("Product not found");
    }

    @Test
    void findAllWithEagerRelationships() {
        Pageable pageable = PageRequest.of(0, 10);
        Product entity = new Product().id(ID);
        ProductDTO dto = new ProductDTO();
        dto.setId(ID);
        Page<Product> page = new PageImpl<>(List.of(entity));
        when(productRepository.findAllWithEagerRelationships(pageable)).thenReturn(page);
        when(productMapper.toDto(entity)).thenReturn(dto);

        Page<ProductDTO> result = productService.findAllWithEagerRelationships(pageable);

        assertThat(result.getContent()).containsExactly(dto);
        verify(productRepository).findAllWithEagerRelationships(pageable);
    }

    @Test
    void findOne_found() {
        Product entity = new Product().id(ID);
        ProductDTO dto = new ProductDTO();
        dto.setId(ID);
        when(productRepository.findOneWithEagerRelationships(ID)).thenReturn(Optional.of(entity));
        when(productMapper.toDto(entity)).thenReturn(dto);

        Optional<ProductDTO> result = productService.findOne(ID);

        assertThat(result).contains(dto);
    }

}
