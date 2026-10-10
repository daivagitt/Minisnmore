package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

class ProductSizeChartValidationTest {
    private String chart(String cell) {
        return "{\"sections\":[{\"title\":\"Vest\",\"bands\":[\"0-3M\",\"3-6M\"],\"rows\":[{\"label\":\"Chest\",\"values\":{\"0-3M\":" + cell + ",\"3-6M\":24}}]}]}";
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "true", "false", "[]", "{}", "\"-2-4\"", "\"garbage\"", "\"Infinity\""})
    void rejectsInvalidMeasurementsEvenAlongsideValidCells(String cell) {
        assertThatThrownBy(() -> ProductSizeChartValidation.validate(chart(cell))).isInstanceOf(ResponseStatusException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "22.5", "\"21-23 cm\"", "\"23–21\"", "\"\"", "\"N/A\"", "null"})
    void allowsNonNegativeMeasurementsRangesAndBlanks(String cell) {
        assertThatCode(() -> ProductSizeChartValidation.validate(chart(cell))).doesNotThrowAnyException();
    }

    @Test
    void rejectsMalformedAndDuplicateBands() {
        assertThatThrownBy(() -> ProductSizeChartValidation.validate("not json")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> ProductSizeChartValidation.validate(chart("22").replace("3-6M", "0-3 Months")))
            .isInstanceOf(ResponseStatusException.class);
        assertThatCode(() -> ProductSizeChartValidation.validate(null)).doesNotThrowAnyException();
        assertThatCode(() -> ProductSizeChartValidation.validate("   ")).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingSectionsAndIncompleteSectionShape() {
        assertThatThrownBy(() -> ProductSizeChartValidation.validate("{\"sections\":[]}"))
            .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> ProductSizeChartValidation.validate("{\"sections\":[{\"title\":\"\",\"bands\":[\"S\"],\"rows\":[{\"label\":\"Chest\",\"values\":{\"S\":1}}]}]}"))
            .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> ProductSizeChartValidation.validate("{\"sections\":[{\"title\":\"Top\",\"bands\":[],\"rows\":[{\"label\":\"Chest\",\"values\":{\"S\":1}}]}]}"))
            .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> ProductSizeChartValidation.validate("{\"sections\":[{\"title\":\"Top\",\"bands\":[\"S\"],\"rows\":[]}]}"))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsDuplicateRowLabelsAndEmptyBands() {
        String duplicateRows =
            "{\"sections\":[{\"title\":\"Top\",\"bands\":[\"S\"],\"rows\":[{\"label\":\"Chest\",\"values\":{\"S\":1}},{\"label\":\"chest\",\"values\":{\"S\":2}}]}]}";
        assertThatThrownBy(() -> ProductSizeChartValidation.validate(duplicateRows)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> ProductSizeChartValidation.validate(chart("22").replace("\"0-3M\"", "\"\"")))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsChartWithOnlyBlankOrSkippedMeasurements() {
        String onlyBlanks =
            "{\"sections\":[{\"title\":\"Top\",\"bands\":[\"S\"],\"rows\":[{\"label\":\"Chest\",\"values\":{\"S\":\"N/A\"}}]}]}";
        assertThatThrownBy(() -> ProductSizeChartValidation.validate(onlyBlanks)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void acceptsInchUnitsOnTextualMeasurements() {
        String inches =
            "{\"sections\":[{\"title\":\"Top\",\"bands\":[\"S\",\"M\"],\"rows\":[{\"label\":\"Chest\",\"values\":{\"S\":\"32 in\",\"M\":\"33 inch\"}}]}]}";
        assertThatCode(() -> ProductSizeChartValidation.validate(inches)).doesNotThrowAnyException();
    }

    @Test
    void rejectsDuplicateNormalizedCommonSizeBands() {
        assertThatThrownBy(() ->
            ProductSizeChartValidation.validate(
                "{\"sections\":[{\"title\":\"Top\",\"bands\":[\"ONE SIZE\",\"OS\"],\"rows\":[{\"label\":\"Chest\",\"values\":{\"ONE SIZE\":10,\"OS\":11}}]}]}"
            )
        ).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void acceptsDashOnlyTextualMeasurements() {
        assertThatCode(() -> ProductSizeChartValidation.validate(chart("\"—\""))).doesNotThrowAnyException();
    }

    @Test
    void rejectsNonObjectChartAndRowsWithoutValuesObject() {
        assertThatThrownBy(() -> ProductSizeChartValidation.validate("[1,2]")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() ->
            ProductSizeChartValidation.validate(
                "{\"sections\":[{\"title\":\"Top\",\"bands\":[\"S\"],\"rows\":[{\"label\":\"Chest\"}]}]}"
            )
        ).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsNonFiniteNumericMeasurements() {
        assertThatThrownBy(() -> ProductSizeChartValidation.validate(chart("NaN"))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> ProductSizeChartValidation.validate(chart("1e309"))).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void acceptsQuoteInchTextualMeasurements() {
        String inches =
            "{\"sections\":[{\"title\":\"Top\",\"bands\":[\"S\"],\"rows\":[{\"label\":\"Chest\",\"values\":{\"S\":\"32\\\"\"}}]}]}";
        assertThatCode(() -> ProductSizeChartValidation.validate(inches)).doesNotThrowAnyException();
    }

    @Test
    void rejectsBooleanMeasurementValues() {
        assertThatThrownBy(() -> ProductSizeChartValidation.validate(chart("true"))).isInstanceOf(ResponseStatusException.class);
    }
}
