package com.miniandmore.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.integration.config.ApplicationProperties;
import com.miniandmore.integration.domain.ZohoSetting;
import com.miniandmore.integration.service.ZohoBooksClient.BinaryDownload;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ZohoItemImageSyncImportPathsTest {

    @Mock
    private ZohoBooksClient zohoBooksClient;

    @Mock
    private S3StorageService s3StorageService;

    private ZohoItemImageSync imageSync;

    @BeforeEach
    void setUp() {
        ApplicationProperties props = new ApplicationProperties();
        props.getZoho().setSyncProductImages(true);
        props.getStorage().getS3().setMaxUploadBytes(10_000L);
        imageSync = new ZohoItemImageSync(new ObjectMapper(), props, zohoBooksClient, s3StorageService);
    }

    @Test
    void importZohoItemImage_triesDocumentIds() throws Exception {
        ZohoSetting setting = new ZohoSetting();
        var item = new ObjectMapper().createObjectNode();
        item.put("image_id", "img-1");
        item.put("document_id", "doc-2");
        when(zohoBooksClient.downloadDocument(setting, "img-1", 10_000L)).thenReturn(Optional.empty());
        when(zohoBooksClient.downloadDocument(setting, "doc-2", 10_000L))
            .thenReturn(Optional.of(new BinaryDownload(new byte[] { 1 }, "image/webp")));
        when(s3StorageService.uploadObject(any(), any(), any(), any())).thenReturn("https://cdn.example.com/x.webp");

        assertThat(imageSync.importZohoItemImage(setting, "z-1", item)).isPresent();
    }

    @Test
    void parseImageUrls_skipsNonTextualNodes() {
        assertThat(imageSync.parseImageUrls("[\"https://cdn.example.com/a.jpg\", 42]")).hasSize(1);
    }

    @Test
    void importZohoItemImage_downloadFailureReturnsEmpty() {
        ZohoSetting setting = new ZohoSetting();
        var item = new ObjectMapper().createObjectNode();
        item.put("image_id", "bad-doc");
        when(zohoBooksClient.downloadDocument(setting, "bad-doc", 10_000L))
            .thenThrow(new ZohoBooksException("Zoho Books download failed: HTTP 500"));

        assertThat(imageSync.importZohoItemImage(setting, "z-bad", item)).isEmpty();
    }
}
