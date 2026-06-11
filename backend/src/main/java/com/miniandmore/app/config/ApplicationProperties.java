package com.miniandmore.app.config;

import java.util.ArrayList;
import java.util.List;
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

    private final Zoho zoho = new Zoho();

    private final Inventory inventory = new Inventory();

    private String internalApiKey = "";

    private final ZohoIntegration zohoIntegration = new ZohoIntegration();

    // jhipster-needle-application-properties-property

    public Liquibase getLiquibase() {
        return liquibase;
    }

    public Storage getStorage() {
        return storage;
    }

    public Zoho getZoho() {
        return zoho;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public String getInternalApiKey() {
        return internalApiKey;
    }

    public void setInternalApiKey(String internalApiKey) {
        this.internalApiKey = internalApiKey;
    }

    public ZohoIntegration getZohoIntegration() {
        return zohoIntegration;
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

    public static class Zoho {

        private final OrderRetry orderRetry = new OrderRetry();

        private final OrderTracking orderTracking = new OrderTracking();

        private final OAuth oauth = new OAuth();

        /** Push the first store product image to Zoho Books after item create/update. */
        private boolean syncProductImages = true;

        /** Push store stock quantity to Zoho Books (locations.initial_stock / location_stock_on_hand). */
        private boolean syncProductStock = true;

        /** Optional Zoho Books location/warehouse id; if blank, the primary location is resolved via API. */
        private String defaultLocationId;

        /** Prefer a location whose name matches this (case-insensitive), e.g. Head Office. */
        private String defaultLocationName = "Head Office";

        /** Zoho Books stock account for {@code item_type=inventory}; auto-resolved from chart of accounts when blank. */
        private String inventoryAccountId;

        /** Auto-create missing categories when pulling products from Zoho into Admin. */
        private boolean pullProductsAutoCreateCategory = false;

        public boolean isSyncProductImages() {
            return syncProductImages;
        }

        public void setSyncProductImages(boolean syncProductImages) {
            this.syncProductImages = syncProductImages;
        }

        public boolean isSyncProductStock() {
            return syncProductStock;
        }

        public void setSyncProductStock(boolean syncProductStock) {
            this.syncProductStock = syncProductStock;
        }

        public String getDefaultLocationId() {
            return defaultLocationId;
        }

        public void setDefaultLocationId(String defaultLocationId) {
            this.defaultLocationId = defaultLocationId;
        }

        public String getDefaultLocationName() {
            return defaultLocationName;
        }

        public void setDefaultLocationName(String defaultLocationName) {
            this.defaultLocationName = defaultLocationName;
        }

        public String getInventoryAccountId() {
            return inventoryAccountId;
        }

        public void setInventoryAccountId(String inventoryAccountId) {
            this.inventoryAccountId = inventoryAccountId;
        }

        public boolean isPullProductsAutoCreateCategory() {
            return pullProductsAutoCreateCategory;
        }

        public void setPullProductsAutoCreateCategory(boolean pullProductsAutoCreateCategory) {
            this.pullProductsAutoCreateCategory = pullProductsAutoCreateCategory;
        }

        public OrderRetry getOrderRetry() {
            return orderRetry;
        }

        public OrderTracking getOrderTracking() {
            return orderTracking;
        }

        public OAuth getOauth() {
            return oauth;
        }

        public static class OAuth {

            /** Zoho API Console → Homepage URL (Server-based client). */
            private String homepageUrl = "http://localhost:5173";

            /** Register these in Zoho → Authorized Redirect URIs; use one when generating the refresh token. */
            private List<String> redirectUris = new ArrayList<>(
                List.of("http://localhost:5173", "http://localhost:5173/admin/zoho")
            );

            /** Register these in Zoho → JavaScript Domain (origin only, no path). */
            private List<String> javascriptDomains = new ArrayList<>(List.of("http://localhost:5173", "http://127.0.0.1:5173"));

            /** Space-separated Books scopes for the authorization URL. */
            private String scopes = "ZohoBooks.fullaccess.all";

            /** Set on the server once (PM2 / env). Client never enters these in admin UI. */
            private String clientId;

            private String clientSecret;

            private String orgId;

            private String defaultDataCenter = "in";

            public String getHomepageUrl() {
                return homepageUrl;
            }

            public void setHomepageUrl(String homepageUrl) {
                this.homepageUrl = homepageUrl;
            }

            public List<String> getRedirectUris() {
                return redirectUris;
            }

            public void setRedirectUris(List<String> redirectUris) {
                this.redirectUris = redirectUris;
            }

            public List<String> getJavascriptDomains() {
                return javascriptDomains;
            }

            public void setJavascriptDomains(List<String> javascriptDomains) {
                this.javascriptDomains = javascriptDomains;
            }

            public String getScopes() {
                return scopes;
            }

            public void setScopes(String scopes) {
                this.scopes = scopes;
            }

            public String getClientId() {
                return clientId;
            }

            public void setClientId(String clientId) {
                this.clientId = clientId;
            }

            public String getClientSecret() {
                return clientSecret;
            }

            public void setClientSecret(String clientSecret) {
                this.clientSecret = clientSecret;
            }

            public String getOrgId() {
                return orgId;
            }

            public void setOrgId(String orgId) {
                this.orgId = orgId;
            }

            public String getDefaultDataCenter() {
                return defaultDataCenter;
            }

            public void setDefaultDataCenter(String defaultDataCenter) {
                this.defaultDataCenter = defaultDataCenter;
            }
        }

        public static class OrderRetry {

            private Long fixedDelayMs = 600_000L;

            public Long getFixedDelayMs() {
                return fixedDelayMs;
            }

            public void setFixedDelayMs(Long fixedDelayMs) {
                this.fixedDelayMs = fixedDelayMs;
            }
        }

        public static class OrderTracking {

            private Long fixedDelayMs = 900_000L;

            public Long getFixedDelayMs() {
                return fixedDelayMs;
            }

            public void setFixedDelayMs(Long fixedDelayMs) {
                this.fixedDelayMs = fixedDelayMs;
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

    public static class ZohoIntegration {

        private String baseUrl = "http://127.0.0.1:8083";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    // jhipster-needle-application-properties-property-class
}
