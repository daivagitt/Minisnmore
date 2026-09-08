package com.miniandmore.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.client.ZohoBooksClient;
import com.miniandmore.app.client.ZohoBooksClient.BinaryDownload;
import com.miniandmore.app.client.ZohoItemDocuments;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.Product;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import com.miniandmore.app.service.ProductVariantSupport.VariantRow;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    private final ProductVariantSupport variantSupport;
    private final ProductColorImagesSupport colorImagesSupport;
    private final HttpClient downloadClient;
    private final Map<String, String> importedHashUrls = new LinkedHashMap<>();

    public ZohoItemImageSync(
        ObjectMapper objectMapper,
        ApplicationProperties applicationProperties,
        ZohoBooksClient booksClient,
        S3StorageService s3StorageService,
        ProductVariantSupport variantSupport
    ) {
        this.objectMapper = objectMapper;
        this.applicationProperties = applicationProperties;
        this.booksClient = booksClient;
        this.s3StorageService = s3StorageService;
        this.variantSupport = variantSupport;
        this.colorImagesSupport = new ProductColorImagesSupport(objectMapper);
        this.downloadClient = HttpClient.newBuilder().connectTimeout(DOWNLOAD_TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    public boolean isEnabled() {
        return applicationProperties.getZoho().isSyncProductImages();
    }

    public void syncProductImage(String zohoItemId, Product product) {
        if (!isEnabled() || StringUtils.isBlank(zohoItemId) || product == null) {
            return;
        }
        uploadImages(zohoItemId, product, resolveProductImageUrls(product));
    }

    /**
     * A simple product can still have colour-specific galleries. The admin keeps the card/primary
     * photo in {@code images_json}, while the rest of the uploaded gallery is stored in
     * {@code colors_json}/{@code color_images_json}. Zoho has only one item for a simple product,
     * so combine all of those galleries in stable order and remove duplicates.
     */
    List<String> resolveProductImageUrls(Product product) {
        List<String> urls = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        addResolvedUrls(urls, seen, colorImagesSupport.allSimpleProductGallerySourceUrls(product));
        return urls;
    }

    /**
     * Push this variant SKU's photo to its Zoho item (not the parent mixed gallery).
     */
    public void syncVariantImage(String zohoItemId, Product product, String variantSku) {
        if (!isEnabled() || StringUtils.isBlank(zohoItemId) || product == null) {
            return;
        }
        uploadImages(zohoItemId, product, resolveVariantImageUrls(product, variantSku));
    }

    /**
     * Images are keyed by print/colour, never by size — every size of the same print pushes the
     * colour's shared gallery to Zoho (mirrors the Ledgerly image model, which has no size axis).
     */
    List<String> resolveVariantImageUrls(Product product, String variantSku) {
        List<String> urls = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (StringUtils.isBlank(variantSku)) {
            return urls;
        }
        Optional<ProductVariantSupport.VariantRow> row = variantSupport.findBySku(
            variantSupport.parseProduct(product),
            variantSku
        );
        if (row.isEmpty()) {
            return urls;
        }
        addResolvedUrls(urls, seen, colorImagesSupport.galleryUrlsForColor(product, row.orElseThrow().color()));
        return urls;
    }

    private void addResolvedUrl(List<String> urls, Set<String> seen, String raw) {
        String resolved = resolvePublicUrl(raw);
        if (StringUtils.isNotBlank(resolved) && seen.add(resolved)) {
            urls.add(resolved);
        }
    }

    private void addResolvedUrls(List<String> urls, Set<String> seen, List<String> candidates) {
        if (candidates == null) {
            return;
        }
        for (String candidate : candidates) {
            addResolvedUrl(urls, seen, candidate);
        }
    }

    private void uploadImages(String zohoItemId, Product product, List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            booksClient.deleteItemImages(zohoItemId);
            LOG.debug(
                "No public image URL for product {} ({}); check images_json, variant imageUrl, and S3_PUBLIC_BASE_URL",
                product.getId(),
                product.getName()
            );
            return;
        }
        int maxImages = maxProductImages();
        if (imageUrls.size() > maxImages) {
            LOG.debug(
                "Truncating Zoho gallery from {} to {} image(s) for product {} ({})",
                imageUrls.size(),
                maxImages,
                product.getId(),
                product.getName()
            );
            imageUrls = new ArrayList<>(imageUrls.subList(0, maxImages));
        }
        List<DownloadedImage> downloaded = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (int index = 0; index < imageUrls.size(); index++) {
            String imageUrl = imageUrls.get(index);
            try {
                downloaded.add(downloadImage(imageUrl));
            } catch (RuntimeException e) {
                failures.add(
                    "image " +
                    (index + 1) +
                    " (" +
                    safeImageReference(imageUrl) +
                    "): " +
                    safeFailureDetail(e)
                );
                LOG.debug(
                    "Zoho image candidate failed for product {} ({}): {} — {}",
                    product.getId(),
                    product.getName(),
                    safeImageReference(imageUrl),
                    safeFailureDetail(e)
                );
            }
        }
        if (!failures.isEmpty()) {
            throw new IllegalStateException(
                "Could not download " + failures.size() + " of " + imageUrls.size() + " product image(s): " + String.join("; ", failures)
            );
        }
        booksClient.uploadItemImages(
            zohoItemId,
            downloaded
                .stream()
                .map(image -> new ZohoBooksClient.ImageUpload(image.bytes(), image.filename(), image.contentType()))
                .toList()
        );
        LOG.debug(
            "Uploaded {} product image(s) to Zoho item {} for product {}",
            downloaded.size(),
            zohoItemId,
            product.getId()
        );
    }

    private int maxProductImages() {
        int configured = applicationProperties.getZoho().getMaxProductImages();
        return Math.max(1, Math.min(configured, ProductColorImagesSupport.MAX_IMAGES_PER_COLOR));
    }

    private static String safeImageReference(String imageUrl) {
        if (StringUtils.isBlank(imageUrl)) {
            return "unknown URL";
        }
        try {
            URI uri = URI.create(imageUrl);
            String path = StringUtils.defaultString(uri.getPath());
            int slash = path.lastIndexOf('/');
            String filename = slash >= 0 ? path.substring(slash + 1) : path;
            String host = StringUtils.defaultIfBlank(uri.getHost(), "image host");
            return StringUtils.isBlank(filename) ? host : host + "/" + filename;
        } catch (IllegalArgumentException ignored) {
            return "invalid URL";
        }
    }

    private static String safeFailureDetail(RuntimeException failure) {
        String detail = StringUtils.defaultIfBlank(failure.getMessage(), failure.getClass().getSimpleName());
        detail = detail.replaceAll("(?i)(https?://[^\\s?]+)\\?[^\\s;]*", "$1?<redacted>");
        return StringUtils.abbreviate(detail, 240);
    }

    public void importZohoItemImage(Product product, String zohoItemId, Map<String, Object> item) {
        List<String> imported = storeImportedImages(product, zohoItemId, item);
        for (int i = imported.size() - 1; i >= 0; i--) {
            setPrimaryImage(product, imported.get(i));
        }
    }

    /**
     * Pull this Zoho SKU's photo(s) onto its print/colour gallery. Zoho has no "print" concept of
     * its own — each size×colour combination is its own Zoho item — but the store treats a print's
     * photos as shared across every size (same model as Ledgerly's {@code colorImagesJson}, which
     * has no size axis at all). Every size of a colour is uploaded to Zoho with the same photo set,
     * so the first size we see photos for seeds the colour's one shared gallery; later same-colour
     * sizes must NOT also dump their own (duplicate) photos into it — that would union multiple
     * sizes' sets into one inflated gallery instead of leaving it as the single shared set.
     */
    public void importZohoVariantColorImage(Product product, String color, String zohoItemId, Map<String, Object> item) {
        String sku = textOf(item, "sku");
        List<String> importedUrls = StringUtils.isNotBlank(zohoItemId) ? storeImportedImages(product, zohoItemId, item) : List.of();
        if (StringUtils.isNotBlank(sku)) {
            // The variant matrix's per-SKU photo field is not the source of truth for prints —
            // clear it so stale/size-specific photos never linger after a pull.
            variantSupport.clearVariantImages(product, sku);
        }
        String colorName = StringUtils.trimToNull(color);
        if (colorName != null && !importedUrls.isEmpty() && colorImagesSupport.galleryUrlsForColor(product, colorName).isEmpty()) {
            colorImagesSupport.replaceColorGallery(product, colorName, importedUrls);
        }
        colorImagesSupport.ensureProductImagesFromColorGalleries(product);
        if (colorName == null && !importedUrls.isEmpty() && colorImagesSupport.isImagesJsonEmpty(product.getImagesJson())) {
            setPrimaryImage(product, importedUrls.get(0));
        }
    }

    private List<String> storeImportedImages(Product product, String zohoItemId, Map<String, Object> item) {
        if (!isEnabled() || StringUtils.isBlank(zohoItemId) || product == null) {
            return List.of();
        }
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        List<String> itemHttpUrls = collectItemHttpImageUrls(item);
        for (String httpUrl : itemHttpUrls) {
            String imported = importHttpImageUrl(product, httpUrl);
            if (StringUtils.isNotBlank(imported)) {
                urls.add(imported.trim());
            }
        }
        long maxBytes = applicationProperties.getStorage().getS3().getMaxUploadBytes();
        int index = 0;
        for (String documentId : ZohoItemDocuments.documentIdsForImport(item)) {
            Optional<BinaryDownload> downloaded = booksClient.downloadDocument(documentId, maxBytes);
            if (downloaded.isEmpty()) {
                throw new IllegalStateException("Zoho returned no bytes for declared document " + documentId);
            }
            if (!isImageDownload(downloaded.orElseThrow())) {
                continue;
            }
            String stored = storeBinary(product, zohoItemId, item, downloaded.orElseThrow(), documentId + "-" + index);
            if (StringUtils.isNotBlank(stored)) {
                urls.add(stored.trim());
            }
            index++;
        }
        if (urls.isEmpty() && itemDeclaresImage(item)) {
            Optional<BinaryDownload> downloaded = booksClient.downloadItemImage(zohoItemId, maxBytes);
            if (downloaded.isPresent() && isImageDownload(downloaded.orElseThrow())) {
                String stored = storeBinary(product, zohoItemId, item, downloaded.orElseThrow(), "item");
                if (StringUtils.isNotBlank(stored)) {
                    urls.add(stored.trim());
                }
            }
            if (urls.isEmpty()) {
                throw new IllegalStateException("Zoho declared an item image but returned no usable image bytes");
            }
        }
        if (urls.isEmpty()) {
            LOG.debug("No Zoho image bytes for item {} (sku={})", zohoItemId, textOf(item, "sku"));
        }
        return new ArrayList<>(urls);
    }

    private String storeBinary(
        Product product,
        String zohoItemId,
        Map<String, Object> item,
        BinaryDownload image,
        String suffix
    ) {
        String publicUrl = storeHashedImage(image.bytes(), image.contentType());
        if (publicUrl != null && !publicUrl.isBlank()) {
            return publicUrl.trim();
        }
        throw new IllegalStateException("Image storage returned no URL for Zoho item " + zohoItemId);
    }

    private String importHttpImageUrl(Product product, String sourceUrl) {
        String trimmed = sourceUrl.trim();
        if (isOnStoreCdn(trimmed)) {
            return trimmed;
        }
        try {
            DownloadedImage downloaded = downloadImage(trimmed);
            String publicUrl = storeHashedImage(downloaded.bytes(), downloaded.contentType());
            if (publicUrl != null && !publicUrl.isBlank()) {
                return publicUrl.trim();
            }
            throw new IllegalStateException("Image storage returned no URL");
        } catch (RuntimeException e) {
            LOG.warn("Zoho HTTP image import failed for product {} ({}): {}", product.getId(), product.getSku(), e.getMessage());
            throw new IllegalStateException("Zoho HTTP image import failed: " + safeFailureDetail(e), e);
        }
    }

    private String storeHashedImage(byte[] bytes, String contentType) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        String digest = sha256Prefix(bytes);
        String cached = importedHashUrls.get(digest);
        if (cached != null) {
            return cached;
        }
        String filename = "zoho-" + digest + "." + extensionForContentType(contentType);
        String publicUrl = s3StorageService.uploadObject("products", filename, bytes, contentType);
        if (publicUrl == null || publicUrl.isBlank()) {
            return null;
        }
        String trimmed = publicUrl.trim();
        importedHashUrls.put(digest, trimmed);
        return trimmed;
    }

    private static String sha256Prefix(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.substring(0, 16);
        } catch (Exception e) {
            return Integer.toHexString(java.util.Arrays.hashCode(bytes));
        }
    }

    private static String extensionForContentType(String contentType) {
        String type = StringUtils.defaultString(contentType);
        if (type.contains("png")) {
            return "png";
        }
        if (type.contains("webp")) {
            return "webp";
        }
        if (type.contains("gif")) {
            return "gif";
        }
        return "jpg";
    }

    private static List<String> collectItemHttpImageUrls(Map<String, Object> item) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        if (item == null) {
            return List.of();
        }
        for (String field : List.of("image_uri", "image_url", "item_image_url")) {
            String value = textOf(item, field);
            if (StringUtils.isNotBlank(value) && value.startsWith("http")) {
                urls.add(value.trim());
            }
        }
        return new ArrayList<>(urls);
    }

    /**
     * True when Zoho Books has a real item image ({@code image_document_id} / {@code image_name}).
     * HTTP {@code image_url} alone is not enough — leftover CDN links from an older push must not
     * be treated as a live Zoho photo on re-pull.
     */
    public boolean hasNativeZohoItemImage(Map<String, Object> item) {
        return itemDeclaresImage(item);
    }

    private static boolean itemDeclaresImage(Map<String, Object> item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        String documentId = textOf(item, "image_document_id");
        if (StringUtils.isNotBlank(documentId) && !"0".equals(documentId) && !"null".equalsIgnoreCase(documentId)) {
            return true;
        }
        String imageName = textOf(item, "image_name");
        return StringUtils.isNotBlank(imageName) && !"null".equalsIgnoreCase(imageName);
    }

    private static boolean isImageDownload(BinaryDownload download) {
        if (download == null || download.bytes() == null || download.bytes().length == 0) {
            return false;
        }
        String type = StringUtils.defaultString(download.contentType()).toLowerCase(Locale.ROOT);
        return type.startsWith("image/") || type.startsWith("application/octet-stream");
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
            Set<String> seen = new LinkedHashSet<>();
            for (String value : raw) {
                String resolved = resolvePublicUrl(value);
                if (StringUtils.isNotBlank(resolved) && seen.add(resolved)) {
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

    private void setPrimaryImage(Product product, String url) {
        if (StringUtils.isBlank(url)) {
            return;
        }
        List<String> images = parseStoredImageUrls(product.getImagesJson());
        images.removeIf(url::equals);
        images.add(0, url.trim());
        try {
            product.setImagesJson(objectMapper.writeValueAsString(images));
        } catch (Exception e) {
            LOG.warn("Failed to serialize images_json for product {}: {}", product.getId(), e.getMessage());
        }
    }

    private List<String> parseStoredImageUrls(String imagesJson) {
        if (StringUtils.isBlank(imagesJson)) {
            return new ArrayList<>();
        }
        try {
            List<String> raw = objectMapper.readValue(imagesJson, new TypeReference<List<String>>() {});
            List<String> urls = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (String value : raw) {
                if (StringUtils.isBlank(value) || !seen.add(value.trim())) {
                    continue;
                }
                urls.add(value.trim());
            }
            return urls;
        } catch (Exception e) {
            return new ArrayList<>();
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
