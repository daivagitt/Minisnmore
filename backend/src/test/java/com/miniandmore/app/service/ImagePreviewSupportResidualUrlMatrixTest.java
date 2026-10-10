package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ImagePreviewSupportResidualUrlMatrixTest {

    @Test
    void objectKeyFromPublicUrl_httpsCaseAndDefaultPortsMatch() {
        assertThat(
            ImagePreviewSupport.objectKeyFromPublicUrl("HTTPS://CDN.Example.COM/bucket", "https://cdn.example.com/bucket/a.jpg")
        ).contains("a.jpg");
        assertThat(
            ImagePreviewSupport.objectKeyFromPublicUrl("https://cdn.example.com:443/bucket", "https://cdn.example.com/bucket/a.jpg")
        ).contains("a.jpg");
    }

    @Test
    void objectKeyFromPublicUrl_rejectsMissingHostAndEncodedDotDot() {
        assertThat(ImagePreviewSupport.objectKeyFromPublicUrl("https://cdn.example.com", "https:///bucket/a.jpg")).isEmpty();
        assertThat(
            ImagePreviewSupport.objectKeyFromPublicUrl("https://cdn.example.com/bucket", "https://cdn.example.com/bucket/%2e%2e/secret")
        ).isEmpty();
    }

    @Test
    void objectKeyFromPublicUrl_acceptsUrlEncodedFilename() {
        assertThat(
            ImagePreviewSupport.objectKeyFromPublicUrl(
                "https://cdn.example.com/bucket",
                "https://cdn.example.com/bucket/products/photo%20one.jpg"
            )
        ).contains("products/photo one.jpg");
    }

    @Test
    void resizeToJpeg_rejectsOversizedPayload() {
        byte[] huge = new byte[(int) ImagePreviewSupport.MAX_SOURCE_BYTES + 1];
        assertThatThrownBy(() -> ImagePreviewSupport.resizeToJpeg(huge, 240))
            .isInstanceOf(ImagePreviewSupport.UnreadableImageException.class);
    }
}
