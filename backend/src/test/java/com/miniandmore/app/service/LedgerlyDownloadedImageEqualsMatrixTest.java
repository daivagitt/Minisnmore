package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import org.junit.jupiter.api.Test;

class LedgerlyDownloadedImageEqualsMatrixTest {

    @Test
    void equalsAndHashCodeCoverIdentityTypeBodyAndContentType() throws Exception {
        Object jpegA = image(new byte[] { 1, 2 }, "image/jpeg");
        Object jpegA2 = image(new byte[] { 1, 2 }, "image/jpeg");
        Object jpegB = image(new byte[] { 9 }, "image/jpeg");
        Object pngA = image(new byte[] { 1, 2 }, "image/png");
        Object nullType = image(new byte[] { 1, 2 }, null);

        assertThat(jpegA).isEqualTo(jpegA);
        assertThat(jpegA).isEqualTo(jpegA2);
        assertThat(jpegA).isNotEqualTo(jpegB);
        assertThat(jpegA).isNotEqualTo(pngA);
        assertThat(jpegA).isNotEqualTo("not-an-image");
        assertThat(jpegA).isNotEqualTo(null);
        assertThat(nullType).isEqualTo(image(new byte[] { 1, 2 }, null));
        assertThat(nullType).isNotEqualTo(jpegA);
        assertThat(jpegA.hashCode()).isEqualTo(jpegA2.hashCode());
        assertThat(jpegA.toString()).contains("image/jpeg");
    }

    private static Object image(byte[] body, String contentType) throws Exception {
        Class<?> type = Class.forName("com.miniandmore.app.service.LedgerlyItemImageSync$DownloadedImage");
        Constructor<?> constructor = type.getDeclaredConstructor(byte[].class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(body, contentType);
    }
}
