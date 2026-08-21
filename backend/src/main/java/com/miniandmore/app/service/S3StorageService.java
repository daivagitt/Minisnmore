package com.miniandmore.app.service;

import com.miniandmore.app.config.ApplicationProperties;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Error;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
public class S3StorageService {

    private static final Duration PRESIGN_TTL = Duration.ofMinutes(15);
    private static final Set<String> ALLOWED_FOLDERS = Set.of(
        "products",
        "categories",
        "branding",
        "subscriptions",
        "banners",
        "home",
        "blog"
    );
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");
    /** Browser-controlled headers must not be returned to clients. */
    private static final Set<String> SKIP_RESPONSE_HEADERS = Set.of("host", "content-length", "connection");

    private final ApplicationProperties.Storage.S3 properties;
    private final Clock clock;
    private volatile S3Client s3Client;
    private volatile S3Presigner s3Presigner;

    public S3StorageService(ApplicationProperties applicationProperties) {
        this.properties = applicationProperties.getStorage().getS3();
        this.clock = Clock.systemUTC();
    }

    public PresignedUpload presignUpload(String folder, String filename) {
        return presignUpload(folder, filename, null);
    }

    public PresignedUpload presignUpload(String folder, String filename, String contentType) {
        validateConfig();
        var safeFolder = validateFolder(folder);
        var safeName = sanitizeFilename(filename);
        var objectKey = buildObjectKey(safeFolder, safeName);
        var resolvedContentType = resolveContentType(contentType, safeName);
        var bucket = properties.getBucket();

        var putObjectRequest = PutObjectRequest.builder()
            .bucket(bucket)
            .key(objectKey)
            .contentType(resolvedContentType)
            .build();

        var presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(PRESIGN_TTL)
            .putObjectRequest(putObjectRequest)
            .build();

        PresignedPutObjectRequest presigned = presigner().presignPutObject(presignRequest);
        var uploadHeaders = headersForClient(presigned);

        return new PresignedUpload(
            presigned.url().toExternalForm(),
            publicObjectUrl(objectKey),
            objectKey,
            "PUT",
            uploadHeaders,
            properties.getMaxUploadBytes()
        );
    }

    /**
     * Upload via the backend (AWS SDK). Avoids browser CORS and custom SigV4 pitfalls on R2.
     */
    public String uploadObject(String folder, String filename, byte[] body, String contentType) {
        if (body.length > properties.getMaxUploadBytes()) {
            var maxMb = Math.max(1, Math.round(properties.getMaxUploadBytes() / (1024.0 * 1024.0)));
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Image is too large. Maximum size is " + maxMb + " MB."
            );
        }
        validateConfig();
        var safeFolder = validateFolder(folder);
        var safeName = sanitizeFilename(filename);
        var objectKey = buildObjectKey(safeFolder, safeName);
        var resolvedContentType = resolveContentType(contentType, safeName);
        var bucket = properties.getBucket();

        var putObjectRequest = PutObjectRequest.builder()
            .bucket(bucket)
            .key(objectKey)
            .contentType(resolvedContentType)
            .build();

        try {
            client().putObject(putObjectRequest, RequestBody.fromBytes(body));
        } catch (Exception ex) {
            throw storageUploadException(ex);
        }
        return publicObjectUrl(objectKey);
    }

    /**
     * Caches one Zoho invoice PDF to the "invoices" folder. Deliberately separate from
     * {@link #uploadObject} — that method's folder/extension whitelists are an image-only
     * invariant for admin uploads and must not be loosened to let PDFs through everywhere.
     */
    public String uploadInvoicePdf(String filename, byte[] body) {
        if (body.length > properties.getMaxUploadBytes()) {
            var maxMb = Math.max(1, Math.round(properties.getMaxUploadBytes() / (1024.0 * 1024.0)));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invoice PDF is too large. Maximum size is " + maxMb + " MB.");
        }
        validateConfig();
        var safeName = filename == null ? "invoice.pdf" : filename.replaceAll("[^A-Za-z0-9._-]", "-");
        if (!safeName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            safeName = safeName + ".pdf";
        }
        var objectKey = buildObjectKey("invoices", safeName);
        var bucket = properties.getBucket();
        var putObjectRequest = PutObjectRequest.builder().bucket(bucket).key(objectKey).contentType("application/pdf").build();
        try {
            client().putObject(putObjectRequest, RequestBody.fromBytes(body));
        } catch (Exception ex) {
            throw storageUploadException(ex);
        }
        return publicObjectUrl(objectKey);
    }

    /**
     * List stored objects under a folder (for gallery recovery). Requires R2 token with ListBucket permission.
     */
    public List<StoredObject> listObjects(String folder, String suffix, int maxKeys) {
        validateConfig();
        int limit = maxKeys <= 0 ? 1000 : Math.min(maxKeys, 5000);
        var safeFolder = validateFolder(folder);
        var prefix = normalizeKeyPrefix(properties.getKeyPrefix());
        var fullPrefix = prefix.isEmpty() ? safeFolder + "/" : prefix + "/" + safeFolder + "/";
        var suffixNorm = suffix == null || suffix.isBlank() ? "" : suffix.trim().toLowerCase(Locale.ROOT);
        List<StoredObject> out = new ArrayList<>();
        String continuationToken = null;
        do {
            var requestBuilder = ListObjectsV2Request.builder()
                .bucket(properties.getBucket())
                .prefix(fullPrefix)
                .maxKeys(Math.min(limit - out.size(), 1000));
            if (continuationToken != null) {
                requestBuilder.continuationToken(continuationToken);
            }
            ListObjectsV2Response response;
            try {
                response = client().listObjectsV2(requestBuilder.build());
            } catch (Exception ex) {
                throw storageListException(ex);
            }
            for (S3Object object : response.contents()) {
                if (object.key() == null) {
                    continue;
                }
                String key = object.key();
                if (!suffixNorm.isEmpty() && !key.toLowerCase(Locale.ROOT).contains(suffixNorm)) {
                    continue;
                }
                out.add(
                    new StoredObject(
                        key,
                        publicObjectUrl(key),
                        object.lastModified(),
                        object.size() == null ? 0L : object.size()
                    )
                );
                if (out.size() >= limit) {
                    return out;
                }
            }
            continuationToken = response.isTruncated() ? response.nextContinuationToken() : null;
        } while (continuationToken != null && out.size() < limit);
        return out;
    }

    /** Delete objects by key. Returns count successfully removed. */
    public int deleteObjects(List<String> keys) {
        validateConfig();
        if (keys == null || keys.isEmpty()) {
            return 0;
        }
        var bucket = properties.getBucket();
        int deleted = 0;
        for (int offset = 0; offset < keys.size(); offset += 1000) {
            var batch = keys.subList(offset, Math.min(offset + 1000, keys.size()));
            var identifiers = new ArrayList<ObjectIdentifier>();
            for (String key : batch) {
                if (key == null || key.isBlank()) {
                    continue;
                }
                identifiers.add(ObjectIdentifier.builder().key(key.trim()).build());
            }
            if (identifiers.isEmpty()) {
                continue;
            }
            try {
                DeleteObjectsResponse response = client()
                    .deleteObjects(
                        DeleteObjectsRequest.builder()
                            .bucket(bucket)
                            .delete(Delete.builder().objects(identifiers).quiet(true).build())
                            .build()
                    );
                deleted += response.deleted() == null ? 0 : response.deleted().size();
                if (response.errors() != null) {
                    for (S3Error error : response.errors()) {
                        if (error.key() != null && !error.key().isBlank()) {
                            throw new ResponseStatusException(
                                HttpStatus.BAD_GATEWAY,
                                "R2 delete failed for " + error.key() + ": " + error.message()
                            );
                        }
                    }
                }
            } catch (ResponseStatusException ex) {
                throw ex;
            } catch (Exception ex) {
                throw storageDeleteException(ex);
            }
        }
        return deleted;
    }

    private ResponseStatusException storageDeleteException(Exception ex) {
        var message = ex.getMessage() == null ? "" : ex.getMessage();
        if (message.contains("AccessDenied") || message.contains("Access Denied") || message.contains("403")) {
            return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "R2 delete denied — grant Object Read & Write on API token",
                ex
            );
        }
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "R2 delete failed: " + message, ex);
    }

    private ResponseStatusException storageListException(Exception ex) {
        var message = ex.getMessage() == null ? "" : ex.getMessage();
        if (message.contains("AccessDenied") || message.contains("Access Denied") || message.contains("403")) {
            return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "R2 list denied — grant ListBucket on API token (Cloudflare R2 → Manage API Tokens)",
                ex
            );
        }
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "R2 list failed: " + message, ex);
    }

    public record StoredObject(String key, String publicUrl, Instant lastModified, long sizeBytes) {}

    private Map<String, String> headersForClient(PresignedPutObjectRequest presigned) {
        var headers = new LinkedHashMap<String, String>();
        presigned
            .httpRequest()
            .headers()
            .forEach((name, values) -> {
                if (values.isEmpty() || SKIP_RESPONSE_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                    return;
                }
                headers.put(name, values.get(0));
            });
        return headers;
    }

    private S3Client client() {
        if (s3Client == null) {
            synchronized (this) {
                if (s3Client == null) {
                    s3Client = buildClient();
                }
            }
        }
        return s3Client;
    }

    private S3Presigner presigner() {
        if (s3Presigner == null) {
            synchronized (this) {
                if (s3Presigner == null) {
                    s3Presigner = buildPresigner();
                }
            }
        }
        return s3Presigner;
    }

    private S3Configuration r2ServiceConfiguration() {
        return S3Configuration.builder().pathStyleAccessEnabled(true).chunkedEncodingEnabled(false).build();
    }

    private S3Client buildClient() {
        return S3Client.builder()
            .endpointOverride(storageEndpointUri())
            .region(storageRegion())
            .credentialsProvider(credentialsProvider())
            .serviceConfiguration(r2ServiceConfiguration())
            .build();
    }

    private S3Presigner buildPresigner() {
        return S3Presigner.builder()
            .endpointOverride(storageEndpointUri())
            .region(storageRegion())
            .credentialsProvider(credentialsProvider())
            .serviceConfiguration(
                S3Configuration.builder().pathStyleAccessEnabled(true).chunkedEncodingEnabled(false).build()
            )
            .build();
    }

    private StaticCredentialsProvider credentialsProvider() {
        return StaticCredentialsProvider.create(
            AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())
        );
    }

    private Region storageRegion() {
        return Region.of(properties.getRegion().trim());
    }

    private URI storageEndpointUri() {
        return URI.create(resolveEndpoint(properties.getBucket()));
    }

    private void validateConfig() {
        if (
            isBlank(properties.getEndpoint()) ||
            isBlank(properties.getBucket()) ||
            isBlank(properties.getAccessKey()) ||
            isBlank(properties.getSecretKey()) ||
            isBlank(properties.getRegion())
        ) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "S3 storage is not configured");
        }
    }

    private String buildObjectKey(String safeFolder, String safeName) {
        var relativeKey = safeFolder + "/" + Instant.now(clock).toEpochMilli() + "-" + UUID.randomUUID() + "-" + safeName;
        var prefix = normalizeKeyPrefix(properties.getKeyPrefix());
        return prefix.isEmpty() ? relativeKey : prefix + "/" + relativeKey;
    }

    private String normalizeKeyPrefix(String prefix) {
        if (isBlank(prefix)) {
            return "";
        }
        var normalized = prefix.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String resolveEndpoint(String bucket) {
        var endpoint = trimTrailingSlash(properties.getEndpoint());
        if (!isBlank(bucket) && endpoint.endsWith("/" + bucket)) {
            return endpoint.substring(0, endpoint.length() - bucket.length() - 1);
        }
        return endpoint;
    }

    private String publicObjectUrl(String objectKey) {
        var base = effectivePublicBaseUrl();
        // Keys we generate are already URL-safe (alphanumeric, hyphen, dot, slash).
        return base + "/" + objectKey;
    }

    /**
     * Browser-visible image URLs must use the public bucket host (r2.dev or custom CDN),
     * never the private S3 API endpoint (*.cloudflarestorage.com).
     */
    private String effectivePublicBaseUrl() {
        var configured = properties.getPublicBaseUrl();
        if (isBlank(configured)) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Public image base URL is not configured (set S3_PUBLIC_BASE_URL to your R2 public bucket URL)"
            );
        }
        var base = trimTrailingSlash(configured.trim());
        if (base.contains("cloudflarestorage.com")) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "S3_PUBLIC_BASE_URL must be the public bucket URL (r2.dev or custom domain), not the R2 API endpoint"
            );
        }
        return base;
    }

    private String validateFolder(String folder) {
        if (!ALLOWED_FOLDERS.contains(folder)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid upload folder");
        }
        return folder;
    }

    private String resolveContentType(String contentType, String filename) {
        var normalized = normalizeImageContentType(contentType);
        if (normalized != null) {
            return normalized;
        }
        var extension = filename.contains(".") ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            default -> "application/octet-stream";
        };
    }

    private String normalizeImageContentType(String contentType) {
        if (isBlank(contentType)) {
            return null;
        }
        var lower = contentType.trim().toLowerCase(Locale.ROOT);
        var base = lower.contains(";") ? lower.substring(0, lower.indexOf(';')).trim() : lower;
        return switch (base) {
            case "image/jpg", "image/jpeg", "image/pjpeg" -> "image/jpeg";
            case "image/png", "image/x-png" -> "image/png";
            case "image/webp" -> "image/webp";
            case "image/gif" -> "image/gif";
            default -> base.startsWith("image/") ? base : null;
        };
    }

    private String sanitizeFilename(String filename) {
        if (isBlank(filename)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filename is required");
        }
        var sanitized = filename.replaceAll("[^A-Za-z0-9._-]", "-");
        var extension = sanitized.contains(".") ? sanitized.substring(sanitized.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Image type not accepted. Use JPG, PNG, WebP, or GIF only."
            );
        }
        return sanitized;
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ResponseStatusException storageUploadException(Exception ex) {
        var message = ex.getMessage() == null ? "" : ex.getMessage();
        if (message.contains("AccessDenied") || message.contains("Access Denied") || message.contains("403")) {
            return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "R2 storage denied upload (check S3_ACCESS_KEY / S3_SECRET_KEY have Object Read & Write on bucket "
                    + properties.getBucket()
                    + ")",
                ex
            );
        }
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Upload to storage failed: " + message, ex);
    }

    public record PresignedUpload(
        String uploadUrl,
        String publicUrl,
        String objectKey,
        String method,
        Map<String, String> headers,
        Long maxUploadBytes
    ) {}
}
