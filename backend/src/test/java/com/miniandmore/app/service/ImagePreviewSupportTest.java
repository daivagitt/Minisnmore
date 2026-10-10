package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ImagePreviewSupportTest {

    @Test
    void objectKeyFromPublicUrl_acceptsUrlOnPublicBase() {
        assertThat(
            ImagePreviewSupport.objectKeyFromPublicUrl(
                "https://cdn.example.com/my-bucket",
                "https://cdn.example.com/my-bucket/products/photo.jpg"
            )
        ).contains("products/photo.jpg");
    }

    @Test
    void objectKeyFromPublicUrl_acceptsR2DevHost() {
        assertThat(
            ImagePreviewSupport.objectKeyFromPublicUrl(
                "https://pub-abc.r2.dev",
                "https://pub-abc.r2.dev/uat/products/romper.jpg"
            )
        ).contains("uat/products/romper.jpg");
    }

    @Test
    void objectKeyFromPublicUrl_rejectsOtherHosts() {
        assertThat(
            ImagePreviewSupport.objectKeyFromPublicUrl(
                "https://cdn.example.com/my-bucket",
                "https://evil.example/my-bucket/products/photo.jpg"
            )
        ).isEmpty();
    }

    @Test
    void objectKeyFromPublicUrl_rejectsHttp() {
        assertThat(
            ImagePreviewSupport.objectKeyFromPublicUrl(
                "https://cdn.example.com/my-bucket",
                "http://cdn.example.com/my-bucket/products/photo.jpg"
            )
        ).isEmpty();
    }

    @Test
    void objectKeyFromPublicUrl_rejectsUserinfo() {
        assertThat(
            ImagePreviewSupport.objectKeyFromPublicUrl(
                "https://cdn.example.com/my-bucket",
                "https://user:pass@cdn.example.com/my-bucket/products/photo.jpg"
            )
        ).isEmpty();
    }

    @Test
    void objectKeyFromPublicUrl_rejectsPathTraversal() {
        assertThat(
            ImagePreviewSupport.objectKeyFromPublicUrl(
                "https://cdn.example.com/my-bucket",
                "https://cdn.example.com/my-bucket/../secret.jpg"
            )
        ).isEmpty();
    }

    @Test
    void objectKeyFromPublicUrl_rejectsOutsideBasePath() {
        assertThat(
            ImagePreviewSupport.objectKeyFromPublicUrl(
                "https://cdn.example.com/my-bucket",
                "https://cdn.example.com/other/products/photo.jpg"
            )
        ).isEmpty();
    }

    @Test
    void clampWidth_defaultsAndCaps() {
        assertThat(ImagePreviewSupport.clampWidth(null)).isEqualTo(240);
        assertThat(ImagePreviewSupport.clampWidth(0)).isEqualTo(240);
        assertThat(ImagePreviewSupport.clampWidth(999)).isEqualTo(320);
        assertThat(ImagePreviewSupport.clampWidth(160)).isEqualTo(160);
    }

    @Test
    void thumbKey_appendsSuffix() {
        assertThat(ImagePreviewSupport.thumbKey("products/a.jpg")).isEqualTo("products/a.jpg.thumb.jpg");
    }

    @Test
    void displayKey_appendsSuffix() {
        assertThat(ImagePreviewSupport.displayKey("products/a.jpg")).isEqualTo("products/a.jpg.display.jpg");
    }

    @Test
    void clampDerivativeEdge_allowsDisplayWidth() {
        assertThat(ImagePreviewSupport.clampDerivativeEdge(800)).isEqualTo(800);
        assertThat(ImagePreviewSupport.clampDerivativeEdge(9999)).isEqualTo(1250);
        assertThat(ImagePreviewSupport.clampDerivativeEdge(0)).isEqualTo(240);
    }

    @Test
    void resizeToJpeg_fitsLongestEdgeAndNeverUpscales() throws Exception {
        byte[] source = jpeg(400, 200, Color.RED);
        byte[] preview = ImagePreviewSupport.resizeToJpeg(source, 240);
        BufferedImage out = ImageIO.read(new ByteArrayInputStream(preview));

        assertThat(out.getWidth()).isEqualTo(240);
        assertThat(out.getHeight()).isEqualTo(120);

        byte[] small = ImagePreviewSupport.resizeToJpeg(jpeg(80, 60, Color.BLUE), 240);
        BufferedImage smallOut = ImageIO.read(new ByteArrayInputStream(small));
        assertThat(smallOut.getWidth()).isEqualTo(80);
        assertThat(smallOut.getHeight()).isEqualTo(60);
    }

    @Test
    void resizeToJpeg_displayWidthIsNotClampedToPreviewMax() throws Exception {
        byte[] source = jpeg(1000, 500, Color.RED);
        byte[] display = ImagePreviewSupport.resizeToJpeg(source, 800, ImagePreviewSupport.DISPLAY_JPEG_QUALITY);
        BufferedImage out = ImageIO.read(new ByteArrayInputStream(display));

        assertThat(out.getWidth()).isEqualTo(800);
        assertThat(out.getHeight()).isEqualTo(400);
    }

    @Test
    void resizeToJpeg_rejectsEmptyBytes() {
        assertThatThrownBy(() -> ImagePreviewSupport.resizeToJpeg(new byte[0], 240))
            .isInstanceOf(ImagePreviewSupport.UnreadableImageException.class);
        assertThatThrownBy(() -> ImagePreviewSupport.resizeToJpeg(null, 240))
            .isInstanceOf(ImagePreviewSupport.UnreadableImageException.class);
    }

    @Test
    void resizeToJpeg_rejectsCorruptBytes() {
        assertThatThrownBy(() -> ImagePreviewSupport.resizeToJpeg(new byte[] { 1, 2, 3, 4 }, 240))
            .isInstanceOf(ImagePreviewSupport.UnreadableImageException.class);
    }

    @Test
    void resizeToJpeg_qualityClamping() throws Exception {
        byte[] source = jpeg(100, 100, Color.GREEN);
        // quality <= 0
        byte[] low = ImagePreviewSupport.resizeToJpeg(source, 100, -1f);
        assertThat(low).isNotEmpty();
        // quality > 0.95
        byte[] high = ImagePreviewSupport.resizeToJpeg(source, 100, 1.0f);
        assertThat(high).isNotEmpty();
        // quality between 0 and 0.5
        byte[] mid = ImagePreviewSupport.resizeToJpeg(source, 100, 0.2f);
        assertThat(mid).isNotEmpty();
    }

    @Test
    void objectKeyFromPublicUrl_edgeCases() {
        // Null inputs
        assertThat(ImagePreviewSupport.objectKeyFromPublicUrl(null, "https://cdn.example.com/a.jpg")).isEmpty();
        assertThat(ImagePreviewSupport.objectKeyFromPublicUrl("https://cdn.example.com", null)).isEmpty();

        // Invalid URI syntax
        assertThat(ImagePreviewSupport.objectKeyFromPublicUrl("not a uri :::", "https://cdn.example.com/a.jpg")).isEmpty();

        // Port mismatch
        assertThat(ImagePreviewSupport.objectKeyFromPublicUrl("https://cdn.example.com:8443", "https://cdn.example.com/a.jpg")).isEmpty();

        // Backslash in path
        assertThat(ImagePreviewSupport.objectKeyFromPublicUrl("https://cdn.example.com", "https://cdn.example.com/a\\b.jpg")).isEmpty();

        // Empty key or dot-dot
        assertThat(ImagePreviewSupport.objectKeyFromPublicUrl("https://cdn.example.com/bucket", "https://cdn.example.com/bucket/")).isEmpty();
    }

    @Test
    void unreadableImageException_withCause() {
        Throwable cause = new IOException("disk error");
        ImagePreviewSupport.UnreadableImageException ex = new ImagePreviewSupport.UnreadableImageException("failed", cause);
        assertThat(ex.getMessage()).isEqualTo("failed");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    static byte[] jpeg(int width, int height, Color color) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }
}
