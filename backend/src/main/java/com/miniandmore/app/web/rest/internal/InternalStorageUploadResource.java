package com.miniandmore.app.web.rest.internal;

import com.miniandmore.app.service.S3StorageService;
import com.miniandmore.app.web.rest.v1.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
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

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadResult> upload(
        @RequestParam("folder") String folder,
        @RequestParam("file") MultipartFile file
    ) throws IOException {
        String publicUrl = s3StorageService.uploadObject(
            folder,
            file.getOriginalFilename(),
            file.getBytes(),
            file.getContentType()
        );
        return ApiResponse.ok(new UploadResult(publicUrl));
    }

    public record PresignRequest(
        @NotBlank @Size(max = 40) String folder,
        @NotBlank @Size(max = 260) String filename,
        @Size(max = 120) String contentType
    ) {}

    public record UploadResult(String publicUrl) {}
}
