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
    }
}
