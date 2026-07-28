package com.miniandmore.store.web;

import com.miniandmore.store.client.CatalogCoreClient;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Tomcat parses multipart before the catch-all proxy can stream the raw body.
 * Re-encode the SKU-wise print import upload and forward it to catalog core.
 */
@RestController
@RequestMapping("/api/v1/admin/import/sku-wise-print")
public class AdminImportProxyResource {

    private final CatalogCoreClient catalogCoreClient;

    public AdminImportProxyResource(CatalogCoreClient catalogCoreClient) {
        this.catalogCoreClient = catalogCoreClient;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> importSkuWisePrint(@RequestPart("file") MultipartFile file) throws IOException {
        return catalogCoreClient.forwardSkuWiseImport(file);
    }
}
