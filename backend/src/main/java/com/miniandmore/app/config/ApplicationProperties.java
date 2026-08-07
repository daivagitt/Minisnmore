package com.miniandmore.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Properties specific to Minisandmore.
 * <p>
 * Properties are configured in the {@code application.yml} file.
 * See {@link tech.jhipster.config.JHipsterProperties} for a good example.
 */
@ConfigurationProperties(prefix = "application", ignoreUnknownFields = false)
public class ApplicationProperties {

    private final Liquibase liquibase = new Liquibase();

    private final Storage storage = new Storage();

    private final Inventory inventory = new Inventory();

    private final Ledgerly ledgerly = new Ledgerly();

    private String internalApiKey = "";

    // jhipster-needle-application-properties-property

    public Liquibase getLiquibase() {
        return liquibase;
    }

    public Storage getStorage() {
        return storage;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public Ledgerly getLedgerly() {
        return ledgerly;
    }

    public String getInternalApiKey() {
        return internalApiKey;
    }

    public void setInternalApiKey(String internalApiKey) {
        this.internalApiKey = internalApiKey;
    }

    // jhipster-needle-application-properties-property-getter

    public static class Liquibase {

        private Boolean asyncStart = true;

        public Boolean getAsyncStart() {
            return asyncStart;
        }

        public void setAsyncStart(Boolean asyncStart) {
            this.asyncStart = asyncStart;
        }
    }

    public static class Storage {

        private final S3 s3 = new S3();

        public S3 getS3() {
            return s3;
        }

        public static class S3 {

            private String endpoint;

            private String region = "us-east-1";

            private String bucket;

            private String accessKey;

            private String secretKey;

            private String publicBaseUrl;

            private String keyPrefix;

            private Long maxUploadBytes = 5_242_880L;

            public String getEndpoint() {
                return endpoint;
            }

            public void setEndpoint(String endpoint) {
                this.endpoint = endpoint;
            }

            public String getRegion() {
                return region;
            }

            public void setRegion(String region) {
                this.region = region;
            }

            public String getBucket() {
                return bucket;
            }

            public void setBucket(String bucket) {
                this.bucket = bucket;
            }

            public String getAccessKey() {
                return accessKey;
            }

            public void setAccessKey(String accessKey) {
                this.accessKey = accessKey;
            }

            public String getSecretKey() {
                return secretKey;
            }

            public void setSecretKey(String secretKey) {
                this.secretKey = secretKey;
            }

            public String getPublicBaseUrl() {
                return publicBaseUrl;
            }

            public void setPublicBaseUrl(String publicBaseUrl) {
                this.publicBaseUrl = publicBaseUrl;
            }

            public String getKeyPrefix() {
                return keyPrefix;
            }

            public void setKeyPrefix(String keyPrefix) {
                this.keyPrefix = keyPrefix;
            }

            public Long getMaxUploadBytes() {
                return maxUploadBytes;
            }

            public void setMaxUploadBytes(Long maxUploadBytes) {
                this.maxUploadBytes = maxUploadBytes;
            }
        }
    }

    public static class Inventory {

        private final ReservationRelease reservationRelease = new ReservationRelease();

        public ReservationRelease getReservationRelease() {
            return reservationRelease;
        }

        public static class ReservationRelease {

            private Long fixedDelayMs = 300_000L;

            public Long getFixedDelayMs() {
                return fixedDelayMs;
            }

            public void setFixedDelayMs(Long fixedDelayMs) {
                this.fixedDelayMs = fixedDelayMs;
            }
        }
    }

    public static class Ledgerly {

        private String apiBaseUrl;

        private String apiToken;

        private String orgId;

        private String webhookSecret;

        private boolean syncProductImages = false;

        private boolean autoSyncEnabled = false;

        private boolean autoPullEnabled = false;

        private final Schedulers schedulers = new Schedulers();

        public String getApiBaseUrl() {
            return apiBaseUrl;
        }

        public void setApiBaseUrl(String apiBaseUrl) {
            this.apiBaseUrl = apiBaseUrl;
        }

        public String getApiToken() {
            return apiToken;
        }

        public void setApiToken(String apiToken) {
            this.apiToken = apiToken;
        }

        public String getOrgId() {
            return orgId;
        }

        public void setOrgId(String orgId) {
            this.orgId = orgId;
        }

        public String getWebhookSecret() {
            return webhookSecret;
        }

        public void setWebhookSecret(String webhookSecret) {
            this.webhookSecret = webhookSecret;
        }

        public boolean isSyncProductImages() {
            return syncProductImages;
        }

        public void setSyncProductImages(boolean syncProductImages) {
            this.syncProductImages = syncProductImages;
        }

        public boolean isAutoSyncEnabled() {
            return autoSyncEnabled;
        }

        public void setAutoSyncEnabled(boolean autoSyncEnabled) {
            this.autoSyncEnabled = autoSyncEnabled;
        }

        public boolean isAutoPullEnabled() {
            return autoPullEnabled;
        }

        public void setAutoPullEnabled(boolean autoPullEnabled) {
            this.autoPullEnabled = autoPullEnabled;
        }

        public Schedulers getSchedulers() {
            return schedulers;
        }

        public static class Schedulers {

            private Long retryOrdersMs = 300_000L;

            private Long pullTrackingMs = 3_600_000L;

            private Long pullInventoryMs;

            public Long getRetryOrdersMs() {
                return retryOrdersMs;
            }

            public void setRetryOrdersMs(Long retryOrdersMs) {
                this.retryOrdersMs = retryOrdersMs;
            }

            public Long getPullTrackingMs() {
                return pullTrackingMs;
            }

            public void setPullTrackingMs(Long pullTrackingMs) {
                this.pullTrackingMs = pullTrackingMs;
            }

            public Long getPullInventoryMs() {
                return pullInventoryMs;
            }

            public void setPullInventoryMs(Long pullInventoryMs) {
                this.pullInventoryMs = pullInventoryMs;
            }
        }
    }
}
