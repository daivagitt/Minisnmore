package com.miniandmore.app.service;

import com.miniandmore.app.repository.SiteSettingRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Removes catalogue uploads that are no longer referenced by site settings. */
@Service
public class CatalogueStorageCleanupService {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogueStorageCleanupService.class);
    private static final Duration ORPHAN_GRACE_PERIOD = Duration.ofHours(24);
    private static final int MAX_CATALOGUES_PER_RUN = 5000;

    private final S3StorageService s3StorageService;
    private final SiteSettingRepository siteSettingRepository;

    public CatalogueStorageCleanupService(S3StorageService s3StorageService, SiteSettingRepository siteSettingRepository) {
        this.s3StorageService = s3StorageService;
        this.siteSettingRepository = siteSettingRepository;
    }

    /**
     * Newly uploaded files get a full day to be saved in settings. Afterwards, only
     * catalogue objects not referenced by any settings row are eligible for deletion.
     */
    @Scheduled(
        initialDelayString = "${application.storage.s3.catalogue-cleanup-initial-delay-ms:600000}",
        fixedDelayString = "${application.storage.s3.catalogue-cleanup-delay-ms:21600000}"
    )
    public void cleanupUnusedCatalogues() {
        try {
            Set<String> activeUrls = activeCatalogueUrls();
            Instant cutoff = Instant.now().minus(ORPHAN_GRACE_PERIOD);
            List<String> orphanKeys = s3StorageService
                .listCatalogueObjects(MAX_CATALOGUES_PER_RUN)
                .stream()
                .filter(object -> object.lastModified() != null && object.lastModified().isBefore(cutoff))
                .filter(object -> !activeUrls.contains(normalizeUrl(object.publicUrl())))
                .map(S3StorageService.StoredObject::key)
                .filter(Objects::nonNull)
                .toList();

            if (!orphanKeys.isEmpty()) {
                s3StorageService.deleteObjects(orphanKeys);
                LOG.info("Removed {} unreferenced catalogue upload(s)", orphanKeys.size());
            }
        } catch (RuntimeException ex) {
            // Cleanup must never affect catalogue reads, uploads, or settings saves.
            LOG.warn("Could not clean up unreferenced catalogue uploads", ex);
        }
    }

    private Set<String> activeCatalogueUrls() {
        Set<String> active = new HashSet<>();
        siteSettingRepository
            .findAll()
            .stream()
            .map(setting -> normalizeUrl(setting.getEcatalogueUrl()))
            .filter(url -> !url.isEmpty())
            .forEach(active::add);
        return active;
    }

    private static String normalizeUrl(String url) {
        return url == null ? "" : url.trim();
    }
}
