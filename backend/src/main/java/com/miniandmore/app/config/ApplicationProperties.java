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

    private final FastSms fastsms = new FastSms();

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

    public FastSms getFastsms() {
        return fastsms;
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

        /** Maximum photos pushed to a single Zoho item during one sync. */
        private int maxProductImages = 10;

        private boolean syncProductStock = true;

        private boolean syncReturns = false;

        private String defaultLocationId;

        private String defaultLocationName = "Head Office";

        private String inventoryAccountId;

        private String inventoryAdjustmentAccountId;

        /** HMAC-SHA256 secret accepted by the stock-only webhook endpoint. */
        private String stockWebhookSecret;

        // Zoho tax_id for the org's 5% GST rate (Settings > Taxes in Zoho Books). When set, it is
        // attached to pushed items (so Zoho auto-applies it, splitting CGST+SGST or IGST per
        // place_of_supply) and to the COD handling fee line, which has no item_id to inherit from.
        // Leave blank to keep sales orders/invoices tax-line-free (current behaviour).
        private String gstTaxId;

        private boolean pullProductsAutoCreateCategory = false;

        private boolean autoSyncEnabled = false;

        private final ProductFields productFields = new ProductFields();

        public boolean isSyncProductImages() {
            return syncProductImages;
        }

        public void setSyncProductImages(boolean syncProductImages) {
            this.syncProductImages = syncProductImages;
        }

        public int getMaxProductImages() {
            return maxProductImages;
        }

        public void setMaxProductImages(int maxProductImages) {
            this.maxProductImages = maxProductImages;
        }

        public boolean isSyncProductStock() {
            return syncProductStock;
        }

        public void setSyncProductStock(boolean syncProductStock) {
            this.syncProductStock = syncProductStock;
        }

        public boolean isSyncReturns() {
            return syncReturns;
        }

        public void setSyncReturns(boolean syncReturns) {
            this.syncReturns = syncReturns;
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

        public String getStockWebhookSecret() {
            return stockWebhookSecret;
        }

        public void setStockWebhookSecret(String stockWebhookSecret) {
            this.stockWebhookSecret = stockWebhookSecret;
        }

        public String getGstTaxId() {
            return gstTaxId;
        }

        public void setGstTaxId(String gstTaxId) {
            this.gstTaxId = gstTaxId;
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

            private String scopes = "ZohoBooks.fullaccess.all,ZohoInventory.packages.READ";

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

    /**
     * FastSMS India (RITS SMS API v1.0) transactional SMS gateway.
     * Signature: MD5(MD5(accessToken + MD5(requestFor + "sms@rits-v1.0" + expire)) + accessTokenKey).
     */
    public static class FastSms {

        private boolean enabled = false;

        private String baseUrl = "https://s.fastsmsindia.com/api/sms/v1.0";

        private String accessToken = "";

        private String accessTokenKey = "";

        /** DLT-registered sender header / sender ID (guide: MININM). */
        private String smsHeader = "MININM";

        /** promotional | transactional | international */
        private String route = "transactional";

        /** DLT principal entity id (guide fixed value). */
        private String entityId = "1701178669941271239";

        /** Optional default DLT template id; prefer per-event template ids under templates. */
        private String templateId = "";

        /** Signature / request expiry window in seconds from now (guide: +1 hour). */
        private long expireSeconds = 3600L;

        private final Templates templates = new Templates();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public String getAccessTokenKey() {
            return accessTokenKey;
        }

        public void setAccessTokenKey(String accessTokenKey) {
            this.accessTokenKey = accessTokenKey;
        }

        public String getSmsHeader() {
            return smsHeader;
        }

        public void setSmsHeader(String smsHeader) {
            this.smsHeader = smsHeader;
        }

        public String getRoute() {
            return route;
        }

        public void setRoute(String route) {
            this.route = route;
        }

        public String getEntityId() {
            return entityId;
        }

        public void setEntityId(String entityId) {
            this.entityId = entityId;
        }

        public String getTemplateId() {
            return templateId;
        }

        public void setTemplateId(String templateId) {
            this.templateId = templateId;
        }

        public long getExpireSeconds() {
            return expireSeconds;
        }

        public void setExpireSeconds(long expireSeconds) {
            this.expireSeconds = expireSeconds;
        }

        public Templates getTemplates() {
            return templates;
        }

        /**
         * DLT-registered message bodies from SMS_API_Integration_Guide.md.pdf §4.
         * Only placeholder values may change; wording/punctuation must match exactly.
         */
        public static class Templates {

            private String orderConfirmed =
                "Dear {name}, your order {orderId} from Minis n More has been confirmed. Order amount: Rs.{amount}. Thank you for shopping with us.";

            private String orderConfirmedTemplateId = "1777178696636494530";

            private String orderCancelled =
                "Dear {name}, your Minis n More order {orderId} has been cancelled successfully as requested. If payment was made online, the applicable refund will be processed as per our refund policy.";

            private String orderCancelledTemplateId = "1777178696692470339";

            private String returnApproved =
                "Dear {name}, your return/exchange for order {orderId} has been approved. We'll arrange pickup shortly. — Minis n More";

            private String returnApprovedTemplateId;

            private String returnRejected =
                "Dear {name}, we're unable to approve your return/exchange request for order {orderId}. Contact support for details. — Minis n More";

            private String returnRejectedTemplateId;

            private String returnPickupScheduled =
                "Dear {name}, pickup for your return/exchange on order {orderId} is scheduled. Please keep the item ready. — Minis n More";

            private String returnPickupScheduledTemplateId;

            private String returnRefunded =
                "Dear {name}, your refund of Rs.{amount} for order {orderId} has been processed. — Minis n More";

            private String returnRefundedTemplateId;

            private String returnFailed =
                "Dear {name}, we could not process your refund for order {orderId}. Our team will contact you shortly. — Minis n More";

            private String returnFailedTemplateId;

            public String getOrderConfirmed() {
                return orderConfirmed;
            }

            public void setOrderConfirmed(String orderConfirmed) {
                this.orderConfirmed = orderConfirmed;
            }

            public String getOrderConfirmedTemplateId() {
                return orderConfirmedTemplateId;
            }

            public void setOrderConfirmedTemplateId(String orderConfirmedTemplateId) {
                this.orderConfirmedTemplateId = orderConfirmedTemplateId;
            }

            public String getOrderCancelled() {
                return orderCancelled;
            }

            public void setOrderCancelled(String orderCancelled) {
                this.orderCancelled = orderCancelled;
            }

            public String getOrderCancelledTemplateId() {
                return orderCancelledTemplateId;
            }

            public void setOrderCancelledTemplateId(String orderCancelledTemplateId) {
                this.orderCancelledTemplateId = orderCancelledTemplateId;
            }

            public String getReturnApproved() {
                return returnApproved;
            }

            public void setReturnApproved(String returnApproved) {
                this.returnApproved = returnApproved;
            }

            public String getReturnApprovedTemplateId() {
                return returnApprovedTemplateId;
            }

            public void setReturnApprovedTemplateId(String returnApprovedTemplateId) {
                this.returnApprovedTemplateId = returnApprovedTemplateId;
            }

            public String getReturnRejected() {
                return returnRejected;
            }

            public void setReturnRejected(String returnRejected) {
                this.returnRejected = returnRejected;
            }

            public String getReturnRejectedTemplateId() {
                return returnRejectedTemplateId;
            }

            public void setReturnRejectedTemplateId(String returnRejectedTemplateId) {
                this.returnRejectedTemplateId = returnRejectedTemplateId;
            }

            public String getReturnPickupScheduled() {
                return returnPickupScheduled;
            }

            public void setReturnPickupScheduled(String returnPickupScheduled) {
                this.returnPickupScheduled = returnPickupScheduled;
            }

            public String getReturnPickupScheduledTemplateId() {
                return returnPickupScheduledTemplateId;
            }

            public void setReturnPickupScheduledTemplateId(String returnPickupScheduledTemplateId) {
                this.returnPickupScheduledTemplateId = returnPickupScheduledTemplateId;
            }

            public String getReturnRefunded() {
                return returnRefunded;
            }

            public void setReturnRefunded(String returnRefunded) {
                this.returnRefunded = returnRefunded;
            }

            public String getReturnRefundedTemplateId() {
                return returnRefundedTemplateId;
            }

            public void setReturnRefundedTemplateId(String returnRefundedTemplateId) {
                this.returnRefundedTemplateId = returnRefundedTemplateId;
            }

            public String getReturnFailed() {
                return returnFailed;
            }

            public void setReturnFailed(String returnFailed) {
                this.returnFailed = returnFailed;
            }

            public String getReturnFailedTemplateId() {
                return returnFailedTemplateId;
            }

            public void setReturnFailedTemplateId(String returnFailedTemplateId) {
                this.returnFailedTemplateId = returnFailedTemplateId;
            }
        }
    }

    public static class Ledgerly {

        private String apiBaseUrl;

        private String apiToken;

        private String orgId;

        private String webhookSecret;

        private boolean syncProductImages = false;

        private boolean syncReturns = false;

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

        public boolean isSyncReturns() {
            return syncReturns;
        }

        public void setSyncReturns(boolean syncReturns) {
            this.syncReturns = syncReturns;
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
