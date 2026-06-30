package com.miniandmore.app.service;

import com.miniandmore.app.repository.LedgerlySettingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LedgerlyCredentialsProvider {

    private final LedgerlySettingRepository settingRepository;
    private final String envBaseUrl;
    private final String envToken;
    private final String envWebhookSecret;

    public LedgerlyCredentialsProvider(
        LedgerlySettingRepository settingRepository,
        @Value("${application.ledgerly.api-base-url:}") String envBaseUrl,
        @Value("${application.ledgerly.api-token:}") String envToken,
        @Value("${application.ledgerly.webhook-secret:}") String envWebhookSecret
    ) {
        this.settingRepository = settingRepository;
        this.envBaseUrl = envBaseUrl;
        this.envToken = envToken;
        this.envWebhookSecret = envWebhookSecret;
    }

    public String baseUrl() {
        return settingRepository
            .findFirstByOrderByIdAsc()
            .map(s -> s.getApiBaseUrl())
            .filter(u -> u != null && !u.isBlank())
            .orElse(envBaseUrl);
    }

    public String token() {
        return settingRepository
            .findFirstByOrderByIdAsc()
            .map(s -> s.getApiToken())
            .filter(t -> t != null && !t.isBlank())
            .orElse(envToken);
    }

    public String webhookSecret() {
        return settingRepository
            .findFirstByOrderByIdAsc()
            .map(s -> s.getWebhookSecret())
            .filter(s -> s != null && !s.isBlank())
            .orElse(envWebhookSecret);
    }
}
