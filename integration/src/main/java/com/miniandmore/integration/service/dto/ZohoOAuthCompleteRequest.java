package com.miniandmore.integration.service.dto;

import jakarta.validation.constraints.NotBlank;

public record ZohoOAuthCompleteRequest(@NotBlank String code) {}
