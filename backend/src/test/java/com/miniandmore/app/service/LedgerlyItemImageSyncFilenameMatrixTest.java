package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.domain.Product;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

class LedgerlyItemImageSyncFilenameMatrixTest {

    @ParameterizedTest
    @CsvSource(
        {
            "SKU-1,image/png,SKU-1-ledgerly.png",
            "'  ',image/webp,item-ledgerly.webp",
            ",image/gif,item-ledgerly.gif",
            "A B,image/jpeg,A-B-ledgerly.jpg",
            "sku,unknown/type,sku-ledgerly.jpg",
        }
    )
    void filenameFor_coversSkuAndContentTypeSwitch(String sku, String contentType, String expected) {
        Product product = new Product();
        product.setSku(sku);
        LedgerlyItemImageSync sync = org.mockito.Mockito.mock(LedgerlyItemImageSync.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        String filename = ReflectionTestUtils.invokeMethod(sync, "filenameFor", product, contentType);
        assertThat(filename).isEqualTo(expected);
    }

    @Test
    void filenameForColor_sanitizesColorAndIndex() {
        Product product = new Product();
        product.setSku("SKU");
        LedgerlyItemImageSync sync = org.mockito.Mockito.mock(LedgerlyItemImageSync.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        String filename = ReflectionTestUtils.invokeMethod(sync, "filenameForColor", product, "Sky Blue", 0, "image/png");
        assertThat(filename).isEqualTo("SKU-sky-blue-1.png");
        String fallback = ReflectionTestUtils.invokeMethod(sync, "filenameForColor", new Product(), "Red", 2, "image/jpeg");
        assertThat(fallback).isEqualTo("item-red-3.jpg");
    }
}
