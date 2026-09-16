package com.miniandmore.app.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ImagePreviewService {

    private static final int MEMORY_CACHE_MAX = 256;
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(15);

    private final S3StorageService storage;
    private final HttpClient httpClient;
    private final Map<String, byte[]> memoryCache = Collections.synchronizedMap(
        new LinkedHashMap<>(32, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
                return size() > MEMORY_CACHE_MAX;
            }
        }
    );

    @Autowired
    public ImagePreviewService(S3StorageService storage) {
        this(
            storage,
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(5)).build()
        );
    }

    ImagePreviewService(S3StorageService storage, HttpClient httpClient) {
        this.storage = storage;
        this.httpClient = httpClient;
    }

    public byte[] preview(String rawUrl, Integer width) {
        int maxEdge = ImagePreviewSupport.clampWidth(width);
        String publicBase = storage.publicBaseUrl();
        String objectKey = ImagePreviewSupport.objectKeyFromPublicUrl(publicBase, rawUrl)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image URL is not a catalog storage URL"));

        String cacheKey = objectKey + "|" + maxEdge;
        byte[] cached = memoryCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        if (maxEdge == ImagePreviewSupport.DEFAULT_WIDTH) {
            Optional<byte[]> storedThumb = storage.getObjectBytes(ImagePreviewSupport.thumbKey(objectKey));
            if (storedThumb.isPresent()) {
                byte[] thumb = storedThumb.orElseThrow();
                memoryCache.put(cacheKey, thumb);
                return thumb;
            }
        }

        byte[] original = storage.getObjectBytes(objectKey).orElseGet(() -> downloadPublic(rawUrl.trim()));
        byte[] jpeg;
        try {
            jpeg = ImagePreviewSupport.resizeToJpeg(original, maxEdge);
        } catch (ImagePreviewSupport.UnreadableImageException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image could not be previewed");
        }

        memoryCache.put(cacheKey, jpeg);
        try {
            storage.ensureImageDerivatives(objectKey, original);
        } catch (RuntimeException ignored) {
            // Best-effort write-back; the in-memory cache still serves this request.
        }
        return jpeg;
    }

    private byte[] downloadPublic(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .GET()
            .timeout(HTTP_TIMEOUT)
            .header("Accept", "image/*")
            .build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image could not be previewed");
            }
            Optional<String> contentLength = response.headers().firstValue("Content-Length");
            contentLength.ifPresent(raw -> {
                try {
                    long length = Long.parseLong(raw.trim());
                    if (length > ImagePreviewSupport.MAX_SOURCE_BYTES) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image is too large to preview.");
                    }
                } catch (NumberFormatException ignored) {
                    // fall through and cap while reading
                }
            });
            int cap = (int) ImagePreviewSupport.MAX_SOURCE_BYTES;
            try (InputStream body = response.body()) {
                byte[] data = body.readNBytes(cap + 1);
                if (data.length > cap) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image is too large to preview.");
                }
                if (data.length == 0) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image could not be previewed");
                }
                return data;
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image could not be previewed");
        }
    }
}
