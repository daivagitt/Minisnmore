package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.domain.Product;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ProductDescriptionCodecBranchCoverageTest {

    @Test
    void applyDecoded_metadataOnlyWithoutCategoryFabricOrStory_executesStaleBodyBranch() {
        ProductDescriptionCodec codec = new ProductDescriptionCodec(new ObjectMapper());
        Product product = new Product().description("keep-me").sizesJson("[\"S\"]");
        ProductDescriptionCodec.Decoded decoded = codec.decode("Sizes: S, M");
        codec.applyDecoded(product, decoded);
        assertThat(codec.parseDelimitedOrJson(product.getSizesJson())).containsExactly("S", "M");
        assertThat(product.getDescription()).isEqualTo("keep-me");
    }

    @Test
    void categoryNameFrom_nullDecoded_returnsNull() {
        ProductDescriptionCodec codec = new ProductDescriptionCodec(new ObjectMapper());
        assertThat(codec.categoryNameFrom(null)).isNull();
    }

    @Test
    void toJsonList_nullValues_serializesEmptyArray() {
        ProductDescriptionCodec codec = new ProductDescriptionCodec(new ObjectMapper());
        assertThat(codec.toJsonList(null)).isEqualTo("[]");
    }

    @Test
    void encode_invalidSizesJson_skipsSizesLine() {
        ProductDescriptionCodec codec = new ProductDescriptionCodec(new ObjectMapper());
        Product product = new Product().sizesJson("{bad").description("Body only");
        assertThat(codec.encode(product, null)).isEqualTo("Body only");
    }

    @Test
    void writeJsonList_failure_returnsEmptyArray() throws Exception {
        ObjectMapper mapper = Mockito.spy(new ObjectMapper());
        Mockito.doThrow(new JsonProcessingException("fail") {}).when(mapper).writeValueAsString(Mockito.anyList());
        ProductDescriptionCodec codec = new ProductDescriptionCodec(mapper);
        Product product = new Product();
        ProductDescriptionCodec.Decoded decoded = new ProductDescriptionCodec.Decoded(
            null,
            null,
            List.of("S"),
            List.of(),
            null,
            null,
            null,
            null,
            null,
            null
        );
        codec.applyDecoded(product, decoded);
        assertThat(product.getSizesJson()).isEqualTo("[]");
    }

    @Test
    void splitList_pipeSeparatedValues_parsedOnDecode() {
        ProductDescriptionCodec codec = new ProductDescriptionCodec(new ObjectMapper());
        ProductDescriptionCodec.Decoded decoded = codec.decode("Colors: Red|Blue");
        assertThat(decoded.colors()).containsExactly("Red", "Blue");
    }
}
