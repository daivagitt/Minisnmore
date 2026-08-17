package com.miniandmore.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.client.ZohoBooksClient;
import com.miniandmore.app.client.ZohoBooksClient.BinaryDownload;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.Product;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Syncs product images with Zoho Books: push store URLs to Zoho ({@code POST /items/{id}/image})
 * and pull Zoho-uploaded images into store storage on product import.
 */
@Service
public class ZohoItemImageSync {

    private static final Logger LOG = LoggerFactory.getLogger(ZohoItemImageSync.class);
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(30);

    private final ObjectMapper objectMapper;
    private final ApplicationProperties applicationProperties;
    private final ZohoBooksClient booksClient;
    private final S3StorageService s3StorageService;
    private final HttpClient downloadClient;

    public ZohoItemImageSync(
        ObjectMapper objectMapper,
        ApplicationProperties applicationProperties,
        ZohoBooksClient booksClient,
        S3StorageService s3StorageService
    ) {
        this.objectMapper = objectMapper;
        this.applicationProperties = applicationProperties;
        this.booksClient = booksClient;
        this.s3StorageService = s3StorageService;
        this.downloadClient = HttpClient.newBuilder().connectTimeout(DOWNLOAD_TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    public boolean isEnabled() {
        return applicationProperties.getZoho().isSyncProductImages();
    }

    public void syncProductImage(String zohoItemId, Product product) {
        if (!isEnabled() || StringUtils.isBlank(zohoItemId) || product == null) {
            return;
        }
        List<String> imageUrls = parseImageUrls(product.getImagesJson());
        if (imageUrls.isEmpty()) {
            LOG.debug(
                "No public image URL for product {} ({}); check images_json and S3_PUBLIC_BASE_URL",
                product.getId(),
                product.getName()
            );
            return;
        }
        RuntimeException lastFailure = null;
        for (String imageUrl : imageUrls) {
            try {
                DownloadedImage image = downloadImage(imageUrl);
                booksClient.uploadItemImage(zohoItemId, image.bytes(), image.filename(), image.contentType());
                LOG.debug("Uploaded product image to Zoho item {} for product {} from {}", zohoItemId, product.getId(), imageUrl);
                return;
            } catch (RuntimeException e) {
                lastFailure = e;
                LOG.debug(
                    "Zoho image candidate failed for product {} ({}): {} — {}",
                    product.getId(),
                    product.getName(),
                    imageUrl,
                    e.getMessage()
                );
            }
        }
        LOG.warn(
            "Zoho item image sync failed for product {} ({}): tried {} URL(s), last error: {}",
            product.getId(),
            product.getName(),
            imageUrls.size(),
            lastFailure != null ? lastFailure.getMessage() : "unknown"
        );
    }

    public void importZohoItemImage(Product product, String zohoItemId, Map<String, Object> item) {
        if (!isEnabled() || StringUtils.isBlank(zohoItemId)) {
            return;
        }
        String httpUrl = resolveHttpImageUrl(item);
        if (StringUtils.isNotBlank(httpUrl)) {
            importHttpImage(product, httpUrl);
            return;
        }
        long maxBytes = applicationProperties.getStorage().getS3().getMaxUploadBytes();
        Optional<BinaryDownload> downloaded = downloadZohoDocument(item, maxBytes);
        if (downloaded.isEmpty()) {
            downloaded = booksClient.downloadItemImage(zohoItemId, maxBytes);
        }
        if (downloaded.isEmpty()) {
            LOG.debug("No Zoho image bytes for item {} (sku={})", zohoItemId, textOf(item, "sku"));
            return;
        }
        try {
            BinaryDownload image = downloaded.orElseThrow();
            String filename = filenameForZohoImport(item, zohoItemId, image.contentType());
            String publicUrl = s3StorageService.uploadObject("products", filename, image.bytes(), image.contentType());
            setPrimaryImage(product, publicUrl);
        } catch (RuntimeException e) {
            LOG.warn("Could not import Zoho image for item {}: {}", zohoItemId, e.getMessage());
        }
    }

    private void importHttpImage(Product product, String sourceUrl) {
        String trimmed = sourceUrl.trim();
        if (isOnStoreCdn(trimmed)) {
            setPrimaryImage(product, trimmed);
            return;
        }
        try {
            DownloadedImage downloaded = downloadImage(trimmed);
            String filename = filenameFor(product, downloaded.contentType());
            String publicUrl = s3StorageService.uploadObject("products", filename, downloaded.bytes(), downloaded.contentType());
            setPrimaryImage(product, publicUrl);
        } catch (RuntimeException e) {
            LOG.warn("Zoho HTTP image import failed for product {} ({}): {}", product.getId(), product.getSku(), e.getMessage());
            setPrimaryImage(product, trimmed);
        }
    }

    private Optional<BinaryDownload> downloadZohoDocument(Map<String, Object> item, long maxBytes) {
        if (item == null || item.isEmpty()) {
            return Optional.empty();
        }
        Set<String> documentIds = new LinkedHashSet<>();
        addIfPresent(documentIds, textOf(item, "image_document_id"));
        addIfPresent(documentIds, textOf(item, "image_id"));
        addIfPresent(documentIds, textOf(item, "document_id"));
        for (String documentId : documentIds) {
            Optional<BinaryDownload> downloaded = booksClient.downloadDocument(documentId, maxBytes);
            if (downloaded.isPresent()) {
                return downloaded;
            }
        }
        return Optional.empty();
    }

    private static void addIfPresent(Set<String> ids, String value) {
        if (StringUtils.isNotBlank(value)) {
            ids.add(value.trim());
        }
    }

    private static String resolveHttpImageUrl(Map<String, Object> item) {
        if (item == null) {
            return null;
        }
        for (String field : List.of("image_uri", "image_url", "item_image_url")) {
            String value = textOf(item, field);
            if (StringUtils.isNotBlank(value) && value.startsWith("http")) {
                return value.trim();
            }
        }
        return null;
    }

    private static String textOf(Map<String, Object> item, String field) {
        if (item == null || !item.containsKey(field) || item.get(field) == null) {
            return "";
        }
        return item.get(field).toString();
    }

    private List<String> parseImageUrls(String imagesJson) {
        if (StringUtils.isBlank(imagesJson)) {
            return List.of();
        }
        try {
            List<String> raw = objectMapper.readValue(imagesJson, new TypeReference<List<String>>() {});
            List<String> urls = new ArrayList<>();
            for (String value : raw) {
                String resolved = resolvePublicUrl(value);
                if (StringUtils.isNotBlank(resolved)) {
                    urls.add(resolved);
                }
            }
            return urls;
        } catch (Exception e) {
            LOG.debug("Could not parse product images_json: {}", e.getMessage());
            return List.of();
        }
    }

    private String resolvePublicUrl(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        String base = StringUtils.trimToEmpty(applicationProperties.getStorage().getS3().getPublicBaseUrl());
        if (StringUtils.isBlank(base)) {
            return null;
        }
        if (trimmed.startsWith("/")) {
            return base.replaceAll("/+$", "") + trimmed;
        }
        return base.replaceAll("/+$", "") + "/" + trimmed;
    }

    private DownloadedImage downloadImage(String imageUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(imageUrl)).timeout(DOWNLOAD_TIMEOUT).GET().build();
            long maxBytes = applicationProperties.getStorage().getS3().getMaxUploadBytes();
            HttpResponse<InputStream> response = downloadClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                response.body().close();
                throw new IllegalStateException("Product image download failed: HTTP " + response.statusCode());
            }
            try (InputStream body = response.body()) {
                byte[] bytes = readBounded(body, maxBytes);
                if (bytes.length == 0) {
                    throw new IllegalStateException("Product image download returned empty body");
                }
                String contentType = response.headers().firstValue("Content-Type").orElse(guessContentType(imageUrl));
                return new DownloadedImage(bytes, filenameFromUrl(imageUrl, contentType), contentType);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Product image download failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Product image download interrupted", e);
        }
    }

    private static byte[] readBounded(InputStream input, long maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IOException("Product image exceeds max size (" + maxBytes + " bytes)");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String filenameFromUrl(String imageUrl, String contentType) {
        String path = URI.create(imageUrl).getPath();
        if (StringUtils.isNotBlank(path)) {
            int slash = path.lastIndexOf('/');
            String name = slash >= 0 ? path.substring(slash + 1) : path;
            if (StringUtils.isNotBlank(name) && name.contains(".")) {
                return name;
            }
        }
        if (contentType != null && contentType.contains("png")) {
            return "product.png";
        }
        if (contentType != null && contentType.contains("webp")) {
            return "product.webp";
        }
        return "product.jpg";
    }

    private static String guessContentType(String imageUrl) {
        String path = URI.create(imageUrl).getPath().toLowerCase(Locale.ROOT);
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

    private static String filenameForZohoImport(Map<String, Object> item, String zohoItemId, String contentType) {
        String sku = textOf(item, "sku");
        String base = StringUtils.isNotBlank(sku) ? "zoho-" + sku : "zoho-" + zohoItemId;
        String safeBase = base.replaceAll("[^A-Za-z0-9._-]", "-");
        if (contentType != null && contentType.contains("png")) {
            return safeBase + ".png";
        }
        if (contentType != null && contentType.contains("webp")) {
            return safeBase + ".webp";
        }
        if (contentType != null && contentType.contains("gif")) {
            return safeBase + ".gif";
        }
        return safeBase + ".jpg";
    }

    private String filenameFor(Product product, String contentType) {
        String sku = product.getSku() != null && !product.getSku().isBlank() ? product.getSku() : "item";
        String ext = switch (StringUtils.defaultString(contentType)) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "jpg";
        };
        return sku.replaceAll("[^A-Za-z0-9._-]", "-") + "-zoho." + ext;
    }

    private void setPrimaryImage(Product product, String url) {
        try {
            product.setImagesJson(objectMapper.writeValueAsString(List.of(url)));
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

    private record DownloadedImage(byte[] bytes, String filename, String contentType) {}
}
