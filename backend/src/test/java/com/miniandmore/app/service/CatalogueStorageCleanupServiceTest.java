package com.miniandmore.app.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.app.domain.SiteSetting;
import com.miniandmore.app.repository.SiteSettingRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogueStorageCleanupServiceTest {

    @Mock
    private S3StorageService s3StorageService;

    @Mock
    private SiteSettingRepository siteSettingRepository;

    @InjectMocks
    private CatalogueStorageCleanupService cleanupService;

    @Test
    void cleanupDeletesOnlyOldUnreferencedCatalogues() {
        String activeUrl = "https://cdn.example.com/catalogues/active.pdf";
        var active = new S3StorageService.StoredObject(
            "catalogues/active.pdf",
            activeUrl,
            Instant.now().minus(2, ChronoUnit.DAYS),
            10L
        );
        var orphan = new S3StorageService.StoredObject(
            "catalogues/orphan.pdf",
            "https://cdn.example.com/catalogues/orphan.pdf",
            Instant.now().minus(2, ChronoUnit.DAYS),
            10L
        );
        var recent = new S3StorageService.StoredObject(
            "catalogues/recent.pdf",
            "https://cdn.example.com/catalogues/recent.pdf",
            Instant.now().minus(1, ChronoUnit.HOURS),
            10L
        );
        when(siteSettingRepository.findAll()).thenReturn(List.of(new SiteSetting().ecatalogueUrl("  " + activeUrl + "  ")));
        when(s3StorageService.listCatalogueObjects(5000)).thenReturn(List.of(active, orphan, recent));

        cleanupService.cleanupUnusedCatalogues();

        verify(s3StorageService).deleteObjects(List.of("catalogues/orphan.pdf"));
    }

    @Test
    void cleanupDoesNothingWhenEveryOldCatalogueIsActive() {
        String activeUrl = "https://cdn.example.com/catalogues/active.pdf";
        var active = new S3StorageService.StoredObject(
            "catalogues/active.pdf",
            activeUrl,
            Instant.now().minus(2, ChronoUnit.DAYS),
            10L
        );
        when(siteSettingRepository.findAll()).thenReturn(List.of(new SiteSetting().ecatalogueUrl(activeUrl)));
        when(s3StorageService.listCatalogueObjects(5000)).thenReturn(List.of(active));

        cleanupService.cleanupUnusedCatalogues();

        verify(s3StorageService, never()).deleteObjects(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void cleanupFailureIsContained() {
        when(siteSettingRepository.findAll()).thenThrow(new IllegalStateException("database unavailable"));

        cleanupService.cleanupUnusedCatalogues();

        verify(s3StorageService, never()).listCatalogueObjects(org.mockito.ArgumentMatchers.anyInt());
    }
}
