package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

class ProductSizeChartValidationShapeBranchTest {

    @Test
    void rejectsJsonNullRoot() {
        assertThatThrownBy(() -> ProductSizeChartValidation.validate("null")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsNonObjectTitleAndCollectionShapes() {
        assertThatThrownBy(() ->
            ProductSizeChartValidation.validate(
                "{\"sections\":[{\"title\":1,\"bands\":[\"S\"],\"rows\":[{\"label\":\"Chest\",\"values\":{\"S\":1}}]}]}"
            )
        ).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() ->
            ProductSizeChartValidation.validate(
                "{\"sections\":[{\"title\":\"Top\",\"bands\":{\"S\":1},\"rows\":[{\"label\":\"Chest\",\"values\":{\"S\":1}}]}]}"
            )
        ).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() ->
            ProductSizeChartValidation.validate(
                "{\"sections\":[{\"title\":\"Top\",\"bands\":[\"S\"],\"rows\":{\"label\":\"Chest\"}}]}"
            )
        ).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsNonTextualBandAndLabel() {
        assertThatThrownBy(() ->
            ProductSizeChartValidation.validate(
                "{\"sections\":[{\"title\":\"Top\",\"bands\":[12],\"rows\":[{\"label\":\"Chest\",\"values\":{\"12\":1}}]}]}"
            )
        ).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() ->
            ProductSizeChartValidation.validate(
                "{\"sections\":[{\"title\":\"Top\",\"bands\":[\"S\"],\"rows\":[{\"label\":9,\"values\":{\"S\":1}}]}]}"
            )
        ).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void allowsMissingBandValueAndExplicitNull() {
        assertThatCode(() ->
            ProductSizeChartValidation.validate(
                "{\"sections\":[{\"title\":\"Top\",\"bands\":[\"S\",\"M\"],\"rows\":[{\"label\":\"Chest\",\"values\":{\"S\":22}}]}]}"
            )
        ).doesNotThrowAnyException();
        assertThatCode(() ->
            ProductSizeChartValidation.validate(
                "{\"sections\":[{\"title\":\"Top\",\"bands\":[\"S\",\"M\"],\"rows\":[{\"label\":\"Chest\",\"values\":{\"S\":22,\"M\":null}}]}]}"
            )
        ).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = { "\"n/a\"", "\"N/A\"", "\"-\"", "\"–\"", "\"21 to 23\"", "\"21-23cm\"", "\"32 inches\"" })
    void acceptsTextualSkipAndRangeForms(String cell) {
        String json =
            "{\"sections\":[{\"title\":\"Top\",\"bands\":[\"S\",\"M\"],\"rows\":[{\"label\":\"Chest\",\"values\":{\"S\":" +
            cell +
            ",\"M\":24}}]}]}";
        assertThatCode(() -> ProductSizeChartValidation.validate(json)).doesNotThrowAnyException();
    }

    @Test
    void rejectsValuesArrayAndBooleanObject() {
        assertThatThrownBy(() ->
            ProductSizeChartValidation.validate(
                "{\"sections\":[{\"title\":\"Top\",\"bands\":[\"S\"],\"rows\":[{\"label\":\"Chest\",\"values\":[]}]}]}"
            )
        ).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() ->
            ProductSizeChartValidation.validate(
                "{\"sections\":[{\"title\":\"Top\",\"bands\":[\"S\"],\"rows\":[{\"label\":\"Chest\",\"values\":{\"S\":{\"cm\":1}}}]}]}"
            )
        ).isInstanceOf(ResponseStatusException.class);
    }
}
