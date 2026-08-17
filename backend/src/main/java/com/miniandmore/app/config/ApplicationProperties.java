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

    private final Inventory inventory = new Inventory();

    private final Zoho zoho = new Zoho();

    private final Ledgerly ledgerly = new Ledgerly();

    private final XpressBees xpressbees = new XpressBees();

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

    public Zoho getZoho() {
        return zoho;
    }

    public Ledgerly getLedgerly() {
        return ledgerly;
    }

    public XpressBees getXpressbees() {
        return xpressbees;
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

    public static class Zoho {

        private final OrderRetry orderRetry = new OrderRetry();

        private final OrderTracking orderTracking = new OrderTracking();

        private final OAuth oauth = new OAuth();

        private boolean syncProductImages = true;

        private boolean syncProductStock = true;

        private String defaultLocationId;

        private String defaultLocationName = "Head Office";

        private String inventoryAccountId;

        private String inventoryAdjustmentAccountId;

        private boolean pullProductsAutoCreateCategory = false;

        private boolean autoSyncEnabled = false;

        private final ProductFields productFields = new ProductFields();

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

        public String getInventoryAdjustmentAccountId() {
            return inventoryAdjustmentAccountId;
        }

        public void setInventoryAdjustmentAccountId(String inventoryAdjustmentAccountId) {
            this.inventoryAdjustmentAccountId = inventoryAdjustmentAccountId;
        }

        public boolean isPullProductsAutoCreateCategory() {
            return pullProductsAutoCreateCategory;
        }

        public void setPullProductsAutoCreateCategory(boolean pullProductsAutoCreateCategory) {
            this.pullProductsAutoCreateCategory = pullProductsAutoCreateCategory;
        }

        public boolean isAutoSyncEnabled() {
            return autoSyncEnabled;
        }

        public void setAutoSyncEnabled(boolean autoSyncEnabled) {
            this.autoSyncEnabled = autoSyncEnabled;
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

        public ProductFields getProductFields() {
            return productFields;
        }

        public static class ProductFields {

            private String fabricTypeLabel = "Fabric Type";

            private String sizesLabel = "sizes";

            private String ageMinLabel = "age";

            private String ageMaxLabel = "Age Max (months)";

            private String categoryLabel = "category";

            /** Zoho India Books stores MRP on {@code label_rate}. */
            private String mrpApiName = "label_rate";

            public String getFabricTypeLabel() {
                return fabricTypeLabel;
            }

            public void setFabricTypeLabel(String fabricTypeLabel) {
                this.fabricTypeLabel = fabricTypeLabel;
            }

            public String getSizesLabel() {
                return sizesLabel;
            }

            public void setSizesLabel(String sizesLabel) {
                this.sizesLabel = sizesLabel;
            }

            public String getAgeMinLabel() {
                return ageMinLabel;
            }

            public void setAgeMinLabel(String ageMinLabel) {
                this.ageMinLabel = ageMinLabel;
            }

            public String getAgeMaxLabel() {
                return ageMaxLabel;
            }

            public void setAgeMaxLabel(String ageMaxLabel) {
                this.ageMaxLabel = ageMaxLabel;
            }

            public String getCategoryLabel() {
                return categoryLabel;
            }

            public void setCategoryLabel(String categoryLabel) {
                this.categoryLabel = categoryLabel;
            }

            public String getMrpApiName() {
                return mrpApiName;
            }

            public void setMrpApiName(String mrpApiName) {
                this.mrpApiName = mrpApiName;
            }

            public List<String> fabricTypeLabels() {
                return List.of(fabricTypeLabel);
            }

            public List<String> sizesLabels() {
                return List.of(sizesLabel, "Sizes");
            }

            public List<String> ageMinLabels() {
                return List.of(ageMinLabel, "Age Min (months)");
            }

            public List<String> ageMaxLabels() {
                return List.of(ageMaxLabel, "age(max)", "Age Max");
            }

            public List<String> categoryLabels() {
                return List.of(categoryLabel, "Category");
            }
        }

        public static class OAuth {

            private String homepageUrl = "http://localhost:5173";

            private List<String> redirectUris = new ArrayList<>(
                List.of("http://localhost:5173", "http://localhost:5173/admin/zoho")
            );

            private List<String> javascriptDomains = new ArrayList<>(List.of("http://localhost:5173", "http://127.0.0.1:5173"));

            private String scopes = "ZohoBooks.fullaccess.all";

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

    public static class XpressBees {

        private String baseUrl = "https://shipment.xpressbees.com";

        private String email;

        private String password;

        private String originPincode;

        private String paymentType = "prepaid";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getOriginPincode() {
            return originPincode;
        }

        public void setOriginPincode(String originPincode) {
            this.originPincode = originPincode;
        }

        public String getPaymentType() {
            return paymentType;
        }

        public void setPaymentType(String paymentType) {
            this.paymentType = paymentType;
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
