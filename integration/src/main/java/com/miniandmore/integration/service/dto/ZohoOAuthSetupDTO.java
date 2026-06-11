package com.miniandmore.integration.service.dto;

import java.util.List;

/**
 * Read-only Zoho API Console registration hints (redirect URIs, JS domains) for admins.
 */
public record ZohoOAuthSetupDTO(
    String clientType,
    String apiConsoleUrl,
    String homepageUrl,
    List<String> redirectUris,
    List<String> javascriptDomains,
    String scopes,
    String primaryRedirectUri,
    String authorizationUrlTemplate,
    String dataCenter
) {}
