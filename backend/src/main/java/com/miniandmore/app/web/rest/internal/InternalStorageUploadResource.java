package com.miniandmore.app.web.rest.internal;

import com.miniandmore.app.service.S3StorageService;
import com.miniandmore.app.web.rest.v1.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/internal/v1/admin/uploads")
public class InternalStorageUploadResource {

    private final S3StorageService s3StorageService;

    public InternalStorageUploadResource(S3StorageService s3StorageService) {
        this.s3StorageService = s3StorageService;
    }

    @PostMapping("/presign")
    public ApiResponse<S3StorageService.PresignedUpload> presign(@Valid @RequestBody PresignRequest request) {
        return ApiResponse.ok(s3StorageService.presignUpload(request.folder(), request.filename(), request.contentType()));
    }

    @PostMapping(value = "/catalogue-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadResult> uploadCataloguePdf(@RequestParam("file") MultipartFile file) throws IOException {
        String publicUrl;
        try (InputStream in = file.getInputStream()) {
            publicUrl = s3StorageService.uploadCataloguePdf(file.getOriginalFilename(), in, file.getSize());
        }
        auditUpload("catalogues", file.getOriginalFilename(), publicUrl, null, null);
        return ApiResponse.ok(new UploadResult(publicUrl));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadResult> upload(
        @RequestParam("folder") String folder,
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "productSku", required = false) String productSku,
        @RequestParam(value = "colorName", required = false) String colorName
    ) throws IOException {
        String publicUrl = s3StorageService.uploadObject(
            folder,
            file.getOriginalFilename(),
            file.getBytes(),
            file.getContentType()
        );
        auditUpload(folder, file.getOriginalFilename(), publicUrl, productSku, colorName);
        return ApiResponse.ok(new UploadResult(publicUrl));
    }

    @PostMapping("/derivatives")
    public ApiResponse<UploadResult> ensureDerivatives(@Valid @RequestBody DerivativesRequest request) {
        s3StorageService.ensureImageDerivativesFromPublicUrl(request.publicUrl());
        return ApiResponse.ok(new UploadResult(request.publicUrl().trim()));
    }

    @GetMapping("/objects")
    public ApiResponse<List<S3StorageService.StoredObject>> listObjects(
        @RequestParam(defaultValue = "products") String folder,
        @RequestParam(required = false) String suffix,
        @RequestParam(defaultValue = "2000") int maxKeys
    ) {
        return ApiResponse.ok(s3StorageService.listObjects(folder, suffix, maxKeys));
    }

    @PostMapping("/delete")
    public ApiResponse<Map<String, Integer>> deleteObjects(@Valid @RequestBody DeleteObjectsRequest request) {
        int deleted = s3StorageService.deleteObjects(request.keys());
        return ApiResponse.ok(Map.of("deleted", deleted));
    }

    private void auditUpload(
        String folder,
        String filename,
        String publicUrl,
        String productSku,
        String colorName
    ) {
        try {
            Path logPath = Path.of("/var/www/uat1/mini_ms_test/deploy/logs/r2-product-uploads.jsonl");
            Files.createDirectories(logPath.getParent());
            String line =
                "{\"ts\":\"" +
                Instant.now() +
                "\",\"folder\":\"" +
                escapeJson(folder) +
                "\",\"filename\":\"" +
                escapeJson(filename == null ? "" : filename) +
                "\",\"publicUrl\":\"" +
                escapeJson(publicUrl) +
                "\",\"productSku\":\"" +
                escapeJson(productSku == null ? "" : productSku) +
                "\",\"colorName\":\"" +
                escapeJson(colorName == null ? "" : colorName) +
                "\"}\n";
            Files.writeString(logPath, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
            // audit is best-effort
        }
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public record PresignRequest(
        @NotBlank @Size(max = 40) String folder,
        @NotBlank @Size(max = 260) String filename,
        @Size(max = 120) String contentType
    ) {}

    public record UploadResult(String publicUrl) {}

    public record DerivativesRequest(@NotBlank @Size(max = 2000) String publicUrl) {}

    public record DeleteObjectsRequest(@NotEmpty List<String> keys) {}
}
