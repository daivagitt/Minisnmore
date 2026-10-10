package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ZohoToBigDecimalResidualMatrixTest {

    @Test
    void catalogStockAdjuster_toBigDecimalCoversNullNumberTextAndEmpty() {
        assertThat((BigDecimal) ReflectionTestUtils.invokeMethod(ZohoCatalogStockAdjuster.class, "toBigDecimal", new Object[] { null }))
            .isEqualByComparingTo("0");
        assertThat((BigDecimal) ReflectionTestUtils.invokeMethod(ZohoCatalogStockAdjuster.class, "toBigDecimal", BigDecimal.TEN))
            .isEqualByComparingTo("10");
        assertThat((BigDecimal) ReflectionTestUtils.invokeMethod(ZohoCatalogStockAdjuster.class, "toBigDecimal", 3))
            .isEqualByComparingTo("3");
        assertThat((BigDecimal) ReflectionTestUtils.invokeMethod(ZohoCatalogStockAdjuster.class, "toBigDecimal", " 4.50 "))
            .isEqualByComparingTo("4.50");
        assertThat((BigDecimal) ReflectionTestUtils.invokeMethod(ZohoCatalogStockAdjuster.class, "toBigDecimal", "   "))
            .isEqualByComparingTo("0");
    }

    @Test
    void exchangeInvoice_toBigDecimalCoversNullNumberTextAndEmpty() {
        assertThat((BigDecimal) ReflectionTestUtils.invokeMethod(ZohoExchangeInvoiceService.class, "toBigDecimal", new Object[] { null }))
            .isEqualByComparingTo("0");
        assertThat((BigDecimal) ReflectionTestUtils.invokeMethod(ZohoExchangeInvoiceService.class, "toBigDecimal", new BigDecimal("2.1")))
            .isEqualByComparingTo("2.1");
        assertThat((BigDecimal) ReflectionTestUtils.invokeMethod(ZohoExchangeInvoiceService.class, "toBigDecimal", 8L)).isEqualByComparingTo("8");
        assertThat((BigDecimal) ReflectionTestUtils.invokeMethod(ZohoExchangeInvoiceService.class, "toBigDecimal", "9")).isEqualByComparingTo("9");
        assertThat((BigDecimal) ReflectionTestUtils.invokeMethod(ZohoExchangeInvoiceService.class, "toBigDecimal", "")).isEqualByComparingTo("0");
    }
}
