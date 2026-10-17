package com.miniandmore.app.security.vault;

import java.util.List;

/** Env var names that may be stored in {@code app_vault_secret} (bootstrap keys excluded). */
public final class VaultKnownKeys {

    public static final List<String> ALL = List.of(
        "JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET",
        "INTERNAL_API_KEY",
        "LEDGERLY_API_TOKEN",
        "LEDGERLY_WEBHOOK_SECRET",
        "ZOHO_CLIENT_ID",
        "ZOHO_CLIENT_SECRET",
        "ZOHO_ORG_ID",
        "ZOHO_STOCK_WEBHOOK_SECRET",
        "ZOHO_SHIPMENT_WEBHOOK_SECRET",
        "ZOHO_ITEM_WEBHOOK_SECRET",
        "ZOHO_ORDER_STATUS_WEBHOOK_SECRET",
        "ZOHO_INVOICE_WEBHOOK_SECRET",
        "SPRING_MAIL_PASSWORD",
        "ZOHO_MAIL_PASSWORD",
        "GOOGLE_CLIENT_SECRET",
        "APPLE_CLIENT_SECRET",
        "FACEBOOK_CLIENT_SECRET",
        "RAZORPAY_KEY_ID",
        "RAZORPAY_KEY_SECRET",
        "RAZORPAY_WEBHOOK_SECRET",
        "XPRESSBEES_PASSWORD",
        "XPRESSBEES_XB_KEY",
        "XPRESSBEES_WEBHOOK_SECRET",
        "S3_ACCESS_KEY",
        "S3_SECRET_KEY",
        "GATEWAY_RATE_LIMIT_REDIS_URL",
        "JHIPSTER_CACHE_REDIS_SERVER"
    );

    private VaultKnownKeys() {}
}
