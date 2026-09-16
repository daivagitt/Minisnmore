package com.miniandmore.app.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Optional;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * Allowlist + JPEG thumbnail helpers for admin product previews. No network I/O.
 */
public final class ImagePreviewSupport {

    public static final int DEFAULT_WIDTH = 240;
    public static final int MAX_WIDTH = 320;
    public static final int DISPLAY_WIDTH = 800;
    public static final int MAX_DERIVATIVE_EDGE = 1250;
    public static final int MAX_SOURCE_DIMENSION = 8000;
    public static final long MAX_SOURCE_BYTES = 8_192_000L;
    public static final String THUMB_SUFFIX = ".thumb.jpg";
    public static final String DISPLAY_SUFFIX = ".display.jpg";
    public static final String PREVIEW_CACHE_CONTROL = "public, max-age=2592000";
    public static final String IMAGE_CACHE_CONTROL = "public, max-age=31536000, immutable";
    public static final float THUMB_JPEG_QUALITY = 0.72f;
    public static final float DISPLAY_JPEG_QUALITY = 0.82f;

    private ImagePreviewSupport() {}

    public static int clampWidth(Integer width) {
        if (width == null || width <= 0) {
            return DEFAULT_WIDTH;
        }
        return Math.min(MAX_WIDTH, width);
    }

    public static String thumbKey(String objectKey) {
        return objectKey + THUMB_SUFFIX;
    }

    public static String displayKey(String objectKey) {
        return objectKey + DISPLAY_SUFFIX;
    }

    /** Longest-edge clamp for stored derivatives (thumb/display). Not used by the admin preview API. */
    public static int clampDerivativeEdge(int maxEdge) {
        if (maxEdge <= 0) {
            return DEFAULT_WIDTH;
        }
        return Math.min(MAX_DERIVATIVE_EDGE, maxEdge);
    }

    /**
     * Returns the object key when {@code candidateUrl} is an https URL on the configured public base.
     * Rejects other hosts, userinfo, path traversal, and non-https schemes.
     */
    public static Optional<String> objectKeyFromPublicUrl(String publicBaseUrl, String candidateUrl) {
        if (publicBaseUrl == null || candidateUrl == null) {
            return Optional.empty();
        }
        URI base;
        URI url;
        try {
            base = URI.create(publicBaseUrl.trim());
            url = URI.create(candidateUrl.trim());
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        if (!"https".equalsIgnoreCase(nullToEmpty(url.getScheme())) || !"https".equalsIgnoreCase(nullToEmpty(base.getScheme()))) {
            return Optional.empty();
        }
        if (url.getUserInfo() != null || url.getHost() == null || base.getHost() == null) {
            return Optional.empty();
        }
        if (!url.getHost().equalsIgnoreCase(base.getHost())) {
            return Optional.empty();
        }
        if (httpsPort(url) != httpsPort(base)) {
            return Optional.empty();
        }

        String basePath = normalizePath(base.getPath());
        String rawPath = url.getRawPath() == null ? url.getPath() : url.getRawPath();
        if (rawPath == null) {
            return Optional.empty();
        }
        String decodedPath;
        try {
            decodedPath = URLDecoder.decode(rawPath, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        if (decodedPath.contains("..") || decodedPath.indexOf('\\') >= 0) {
            return Optional.empty();
        }
        decodedPath = normalizePath(decodedPath);
        if (!decodedPath.equals(basePath) && !decodedPath.startsWith(basePath.isEmpty() ? "/" : basePath + "/")) {
            return Optional.empty();
        }
        String key = basePath.isEmpty() ? decodedPath : decodedPath.substring(basePath.length());
        if (key.startsWith("/")) {
            key = key.substring(1);
        }
        if (key.isBlank() || key.contains("..")) {
            return Optional.empty();
        }
        return Optional.of(key);
    }

    public static byte[] resizeToJpeg(byte[] source, int maxEdge) {
        return resizeToJpeg(source, maxEdge, THUMB_JPEG_QUALITY);
    }

    public static byte[] resizeToJpeg(byte[] source, int maxEdge, float quality) {
        if (source == null || source.length == 0) {
            throw new UnreadableImageException("Image is empty.");
        }
        if (source.length > MAX_SOURCE_BYTES) {
            throw new UnreadableImageException("Image is too large to preview.");
        }
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(source));
        } catch (IOException ex) {
            throw new UnreadableImageException("Could not decode image.", ex);
        }
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            throw new UnreadableImageException("Could not decode image.");
        }
        int srcW = image.getWidth();
        int srcH = image.getHeight();
        if (srcW > MAX_SOURCE_DIMENSION || srcH > MAX_SOURCE_DIMENSION) {
            throw new UnreadableImageException("Image dimensions are too large to preview.");
        }
        int edge = clampDerivativeEdge(maxEdge);
        double scale = Math.min(1.0, (double) edge / Math.max(srcW, srcH));
        int dstW = Math.max(1, (int) Math.round(srcW * scale));
        int dstH = Math.max(1, (int) Math.round(srcH * scale));

        BufferedImage rgb = new BufferedImage(dstW, dstH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, dstW, dstH);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(image, 0, 0, dstW, dstH, null);
        } finally {
            g.dispose();
        }
        return writeJpeg(rgb, quality);
    }

    private static byte[] writeJpeg(BufferedImage image, float quality) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new UnreadableImageException("JPEG writer is not available.");
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(clampJpegQuality(quality));
            }
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException ex) {
            throw new UnreadableImageException("Could not encode preview JPEG.", ex);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static float clampJpegQuality(float quality) {
        if (quality <= 0f) {
            return THUMB_JPEG_QUALITY;
        }
        return Math.min(0.95f, Math.max(0.5f, quality));
    }

    private static int httpsPort(URI uri) {
        int port = uri.getPort();
        return port == -1 ? 443 : port;
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank() || "/".equals(path)) {
            return "";
        }
        String normalized = path;
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public static class UnreadableImageException extends RuntimeException {

        public UnreadableImageException(String message) {
            super(message);
        }

        public UnreadableImageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
