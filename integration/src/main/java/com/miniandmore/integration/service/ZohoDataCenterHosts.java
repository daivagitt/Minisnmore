package com.miniandmore.integration.service;

import java.util.Locale;
import org.apache.commons.lang3.StringUtils;

/**
 * Region-specific Zoho accounts and API Console hosts.
 * <p>
 * Data centre codes match {@code ZohoSetting.dataCenter} / admin UI values ({@code in}, {@code com}, {@code eu},
 * {@code com.au}, plus {@code jp}, {@code ca}, {@code sa}).
 */
public final class ZohoDataCenterHosts {

    private static final String DEFAULT_DATA_CENTER = "in";

    private ZohoDataCenterHosts() {}

    public static String normalize(String dataCenter) {
        if (StringUtils.isBlank(dataCenter)) {
            return DEFAULT_DATA_CENTER;
        }
        String code = switch (dataCenter.trim().toLowerCase(Locale.ROOT)) {
            case "us" -> "com";
            case "au" -> "com.au";
            default -> dataCenter.trim().toLowerCase(Locale.ROOT);
        };
        return isSupported(code) ? code : DEFAULT_DATA_CENTER;
    }

    public static boolean isSupported(String dataCenter) {
        return switch (normalizeAliasOnly(dataCenter)) {
            case "in", "com", "eu", "com.au", "jp", "ca", "sa" -> true;
            default -> false;
        };
    }

    private static String normalizeAliasOnly(String dataCenter) {
        if (StringUtils.isBlank(dataCenter)) {
            return "";
        }
        return switch (dataCenter.trim().toLowerCase(Locale.ROOT)) {
            case "us" -> "com";
            case "au" -> "com.au";
            default -> dataCenter.trim().toLowerCase(Locale.ROOT);
        };
    }

    /** e.g. {@code accounts.zoho.in}, {@code accounts.zohocloud.ca} */
    public static String accountsHost(String dataCenter) {
        return switch (normalize(dataCenter)) {
            case "com" -> "accounts.zoho.com";
            case "eu" -> "accounts.zoho.eu";
            case "com.au" -> "accounts.zoho.com.au";
            case "jp" -> "accounts.zoho.jp";
            case "ca" -> "accounts.zohocloud.ca";
            case "sa" -> "accounts.zoho.sa";
            case "in" -> "accounts.zoho.in";
            default -> "accounts.zoho." + normalize(dataCenter);
        };
    }

    public static String accountsBaseUrl(String dataCenter) {
        return "https://" + accountsHost(dataCenter);
    }

    public static String authorizationUrl(String dataCenter) {
        return accountsBaseUrl(dataCenter) + "/oauth/v2/auth";
    }

    /** e.g. {@code https://api-console.zoho.in/} */
    public static String apiConsoleUrl(String dataCenter) {
        return switch (normalize(dataCenter)) {
            case "com" -> "https://api-console.zoho.com/";
            case "eu" -> "https://api-console.zoho.eu/";
            case "com.au" -> "https://api-console.zoho.com.au/";
            case "jp" -> "https://api-console.zoho.jp/";
            case "ca" -> "https://api-console.zohocloud.ca/";
            case "sa" -> "https://api-console.zoho.sa/";
            case "in" -> "https://api-console.zoho.in/";
            default -> "https://api-console.zoho." + normalize(dataCenter) + "/";
        };
    }

    /** Domain suffix for {@code https://www.zohoapis.{suffix}/books/v3}. */
    public static String booksApiDomainSuffix(String dataCenter) {
        return switch (normalize(dataCenter)) {
            case "ca" -> "ca";
            default -> normalize(dataCenter);
        };
    }

    public static String booksApiBaseUrl(String dataCenter) {
        return "https://www.zohoapis." + booksApiDomainSuffix(dataCenter) + "/books/v3";
    }
}
