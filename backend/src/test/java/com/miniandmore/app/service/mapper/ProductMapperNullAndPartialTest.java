package com.miniandmore.app.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.domain.Category;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.service.dto.CategoryDTO;
import com.miniandmore.app.service.dto.ProductDTO;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ProductMapperNullAndPartialTest {

    @Test
    void productMapper_nullInputs_returnNull() {
        ProductMapper mapper = MapperTestSupport.productMapper();
        assertThat(mapper.toDto((Product) null)).isNull();
        assertThat(mapper.toEntity((ProductDTO) null)).isNull();
        assertThat(mapper.toDto(java.util.List.of())).isEmpty();
    }

    @Test
    void productMapper_partialUpdateCategoryWhenEntityHasNoCategory() {
        ProductMapper mapper = MapperTestSupport.productMapper();
        Product entity = new Product().slug("p").name("P").price(BigDecimal.TEN).currency("INR").active(true);
        CategoryDTO categoryDto = new CategoryDTO();
        categoryDto.setId(3L);
        categoryDto.setName("Dresses");
        categoryDto.setSlug("dresses");
        ProductDTO patch = new ProductDTO();
        patch.setCategory(categoryDto);

        mapper.partialUpdate(entity, patch);

        assertThat(entity.getCategory()).isNotNull();
        assertThat(entity.getCategory().getName()).isEqualTo("Dresses");
    }

    @Test
    void productMapper_partialUpdateCategoryWhenEntityAlreadyHasCategory() {
        ProductMapper mapper = MapperTestSupport.productMapper();
        Category existing = new Category().id(1L).name("Old").slug("old");
        Product entity = new Product()
            .slug("p")
            .name("P")
            .price(BigDecimal.TEN)
            .currency("INR")
            .active(true)
            .category(existing);
        CategoryDTO categoryDto = new CategoryDTO();
        categoryDto.setName("New");
        ProductDTO patch = new ProductDTO();
        patch.setCategory(categoryDto);

        mapper.partialUpdate(entity, patch);

        assertThat(entity.getCategory().getName()).isEqualTo("New");
    }

    @Test
    void productMapper_partialUpdateEveryScalarField() {
        ProductMapper mapper = MapperTestSupport.productMapper();
        Product entity = new Product().slug("p").name("P").price(BigDecimal.ONE).currency("INR").active(true);
        ProductDTO patch = new ProductDTO();
        patch.setId(99L);
        patch.setSlug("new");
        patch.setName("New");
        patch.setSku("SKU");
        patch.setFabricType("Cotton");
        patch.setDescription("Desc");
        patch.setStory("Story");
        patch.setPrice(BigDecimal.TEN);
        patch.setCompareAtPrice(BigDecimal.valueOf(20));
        patch.setCurrency("USD");
        patch.setAgeMinMonths(1);
        patch.setAgeMaxMonths(12);
        patch.setSizesJson("[]");
        patch.setColorsJson("[]");
        patch.setImagesJson("[]");
        patch.setBadge("Hot");
        patch.setStock(9);
        patch.setSortOrder(2);
        patch.setFeatured(true);
        patch.setTrending(true);
        patch.setNewArrival(true);
        patch.setBestSeller(true);
        patch.setActive(false);

        mapper.partialUpdate(entity, patch);

        assertThat(entity.getSku()).isEqualTo("SKU");
        assertThat(entity.getBestSeller()).isTrue();
        assertThat(entity.getActive()).isFalse();
        mapper.partialUpdate(entity, null);
    }

    @Test
    void productMapper_partialUpdateCatalogAndMediaJsonFields() {
        ProductMapper mapper = MapperTestSupport.productMapper();
        Product entity = new Product().slug("p").name("P").price(BigDecimal.TEN).currency("INR").active(true);
        ProductDTO patch = new ProductDTO();
        patch.setUnits("pair");
        patch.setSizeChartJson("{\"sections\":[]}");
        patch.setSizeNotesJson("[]");
        patch.setColorImagesJson("{}");
        patch.setItemStructure("VARIANTS");
        patch.setVariantsJson("[]");
        patch.setImagesJson("[]");
        patch.setDetailsKicker("Kicker");

        mapper.partialUpdate(entity, patch);

        assertThat(entity.getUnits()).isEqualTo("pair");
        assertThat(entity.getSizeChartJson()).isEqualTo("{\"sections\":[]}");
        assertThat(entity.getSizeNotesJson()).isEqualTo("[]");
        assertThat(entity.getColorImagesJson()).isEqualTo("{}");
        assertThat(entity.getItemStructure()).isEqualTo("VARIANTS");
        assertThat(entity.getVariantsJson()).isEqualTo("[]");
        assertThat(entity.getImagesJson()).isEqualTo("[]");
        assertThat(entity.getDetailsKicker()).isEqualTo("Kicker");
    }

    @Test
    void productMapper_toDtoCategoryName_nullCategory() {
        ProductMapper mapper = MapperTestSupport.productMapper();
        assertThat(mapper.toDtoCategoryName(null)).isNull();
    }

    @Test
    void productMapper_roundTrip_preservesExtendedFields() {
        ProductMapper mapper = MapperTestSupport.productMapper();
        ProductDTO dto = new ProductDTO();
        dto.setSlug("round-trip");
        dto.setName("Round Trip");
        dto.setSku("RT-SKU");
        dto.setUnits("piece");
        dto.setPrice(BigDecimal.valueOf(499));
        dto.setCompareAtPrice(BigDecimal.valueOf(599));
        dto.setCurrency("INR");
        dto.setItemStructure("VARIANTS");
        dto.setVariantsJson("[]");
        dto.setZohoItemId("zoho-rt");
        dto.setLedgerlyItemId("ledgerly-rt");
        dto.setActive(true);

        Product entity = mapper.toEntity(dto);
        ProductDTO back = mapper.toDto(entity);

        assertThat(back.getSlug()).isEqualTo("round-trip");
        assertThat(back.getUnits()).isEqualTo("piece");
        assertThat(back.getZohoItemId()).isEqualTo("zoho-rt");
        assertThat(back.getLedgerlyItemId()).isEqualTo("ledgerly-rt");
        assertThat(mapper.toDto((Product) null)).isNull();
        assertThat(mapper.toEntity((ProductDTO) null)).isNull();
    }
}
