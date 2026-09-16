package com.miniandmore.app.web.rest.internal;

import com.miniandmore.app.service.ImagePreviewService;
import com.miniandmore.app.service.ImagePreviewSupport;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/media")
public class InternalMediaPreviewResource {

    private final ImagePreviewService imagePreviewService;

    public InternalMediaPreviewResource(ImagePreviewService imagePreviewService) {
        this.imagePreviewService = imagePreviewService;
    }

    @GetMapping("/preview")
    public ResponseEntity<byte[]> preview(
        @RequestParam("u") String url,
        @RequestParam(value = "w", required = false) Integer width
    ) {
        byte[] jpeg = imagePreviewService.preview(url, width);
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_JPEG)
            .header(HttpHeaders.CACHE_CONTROL, ImagePreviewSupport.PREVIEW_CACHE_CONTROL)
            .body(jpeg);
    }
}
