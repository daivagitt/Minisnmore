package com.miniandmore.integration.service.dto;

/**
 * Admin-facing Zoho connection status (no secrets).
 */
public record ZohoIntegrationStatusDTO(
    boolean serverConfigured,
    boolean connected,
    String dataCenter,
    String organizationId,
    boolean pullProductsAutoCreateCategory
) {}
