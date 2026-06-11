package com.miniandmore.store.web;

import com.miniandmore.store.client.CatalogCoreClient;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Tomcat parses multipart before the catch-all proxy can stream the raw body.
 * Re-encode uploads and forward them to catalog core.
 */
@RestController
@RequestMapping("/api/v1/admin/uploads")
public class AdminUploadProxyResource {

    private final CatalogCoreClient catalogCoreClient;

    public AdminUploadProxyResource(CatalogCoreClient catalogCoreClient) {
        this.catalogCoreClient = catalogCoreClient;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> upload(@RequestParam("folder") String folder, @RequestPart("file") MultipartFile file)
        throws IOException {
        return catalogCoreClient.forwardMultipartUpload(folder, file);
    }
}
