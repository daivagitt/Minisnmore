package com.miniandmore.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.Product;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Imports Ledgerly item images into store R2 storage on pull/webhook (mirrors legacy ZohoItemImageSync).
 * Failures are logged only so SKU/name/price sync still succeeds.
 */
@Service
public class LedgerlyItemImageSync {

    private static final Logger LOG = LoggerFactory.getLogger(LedgerlyItemImageSync.class);
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(30);

    private final ObjectMapper objectMapper;
    private final ApplicationProperties applicationProperties;
    private final S3StorageService s3StorageService;
    private final HttpClient httpClient;

    public LedgerlyItemImageSync(
        ObjectMapper objectMapper,
        ApplicationProperties applicationProperties,
        S3StorageService s3StorageService
    ) {
        this.objectMapper = objectMapper;
        this.applicationProperties = applicationProperties;
        this.s3StorageService = s3StorageService;
        this.httpClient = HttpClient.newBuilder().connectTimeout(DOWNLOAD_TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    public boolean isEnabled() {
        return applicationProperties.getLedgerly().isSyncProductImages();
    }

    /**
     * Sync Ledgerly's primary image URL onto the product. Replaces slot 0 so repeated pulls do not grow the gallery.
     */
    public void importItemImage(Product product, String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return;
        }
        String trimmed = sourceUrl.trim();
        if (alreadyPrimaryImage(product, trimmed)) {
            return;
        }
        if (isOnStoreCdn(trimmed)) {
            setPrimaryImage(product, trimmed);
            return;
        }
        if (isEnabled()) {
            try {
                DownloadedImage downloaded = download(trimmed);
                String filename = filenameFor(product, downloaded.contentType());
                String publicUrl = s3StorageService.uploadObject("products", filename, downloaded.body(), downloaded.contentType());
                setPrimaryImage(product, publicUrl);
                return;
            } catch (Exception e) {
                LOG.warn(
                    "Ledgerly image import failed for product {} ({}): {}",
                    product.getId(),
                    product.getSku(),
                    e.getMessage()
                );
            }
        }
        setPrimaryImage(product, trimmed);
    }

    /** First image URL from images_json for push to Ledgerly (prefer store CDN URL). */
    public String firstImageUrl(String imagesJson) {
        if (imagesJson == null || imagesJson.isBlank()) {
            return null;
        }
        try {
            List<String> images = objectMapper.readValue(imagesJson, new TypeReference<List<String>>() {});
            return images.isEmpty() ? null : images.get(0);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void setPrimaryImage(Product product, String url) {
        writeImages(product, List.of(url));
    }

    private boolean alreadyPrimaryImage(Product product, String url) {
        List<String> images = readImages(product);
        return !images.isEmpty() && url.equals(images.get(0));
    }

    private List<String> readImages(Product product) {
        if (product.getImagesJson() == null || product.getImagesJson().isBlank()) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(objectMapper.readValue(product.getImagesJson(), new TypeReference<List<String>>() {}));
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private void writeImages(Product product, List<String> images) {
        try {
            product.setImagesJson(objectMapper.writeValueAsString(images));
        } catch (Exception e) {
            LOG.warn("Failed to serialize images_json for product {}: {}", product.getId(), e.getMessage());
        }
    }

    private boolean isOnStoreCdn(String url) {
        String base = applicationProperties.getStorage().getS3().getPublicBaseUrl();
        if (base == null || base.isBlank()) {
            return false;
        }
        String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return url.startsWith(normalizedBase + "/") || url.equals(normalizedBase);
    }

    private DownloadedImage download(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(DOWNLOAD_TIMEOUT).GET().build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode());
        }
        byte[] body = response.body();
        long maxBytes = applicationProperties.getStorage().getS3().getMaxUploadBytes();
        if (body.length > maxBytes) {
            throw new IOException("Image exceeds max upload size (" + body.length + " bytes)");
        }
        String contentType = resolveContentType(response.headers().firstValue("content-type").orElse(null), url);
        if (!contentType.startsWith("image/")) {
            throw new IOException("Not an image content-type: " + contentType);
        }
        return new DownloadedImage(body, contentType);
    }

    private String resolveContentType(String headerValue, String url) {
        if (headerValue != null && !headerValue.isBlank()) {
            String base = headerValue.split(";")[0].trim().toLowerCase(Locale.ROOT);
            if (base.startsWith("image/")) {
                return base;
            }
        }
        String path = URI.create(url).getPath().toLowerCase(Locale.ROOT);
        if (path.endsWith(".png")) {
            return "image/png";
        }
        if (path.endsWith(".webp")) {
            return "image/webp";
        }
        if (path.endsWith(".gif")) {
            return "image/gif";
        }
        return "image/jpeg";
    }

    /** Stable per-SKU filename so re-pull overwrites the same R2 object instead of appending new URLs. */
    private String filenameFor(Product product, String contentType) {
        String sku = product.getSku() != null && !product.getSku().isBlank() ? product.getSku() : "item";
        String ext = switch (contentType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "jpg";
        };
        return sanitizeFilename(sku + "-ledgerly." + ext);
    }

    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[^A-Za-z0-9._-]", "-");
    }

    private record DownloadedImage(byte[] body, String contentType) {}
}
