package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.miniandmore.app.domain.LedgerlySetting;
import com.miniandmore.app.repository.LedgerlySettingRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LedgerlyCredentialsProviderTest {

    @Mock
    private LedgerlySettingRepository settingRepository;

    @InjectMocks
    private LedgerlyCredentialsProvider provider;

    @Test
    void baseUrl_prefersDatabaseSetting() {
        LedgerlySetting setting = new LedgerlySetting();
        setting.setApiBaseUrl("https://db.example/api");
        when(settingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(setting));
        ReflectionTestUtils.setField(provider, "envBaseUrl", "https://env.example/api");

        assertThat(provider.baseUrl()).isEqualTo("https://db.example/api");
    }

    @Test
    void baseUrl_fallsBackToEnvWhenSettingBlank() {
        LedgerlySetting setting = new LedgerlySetting();
        setting.setApiBaseUrl("  ");
        when(settingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(setting));
        ReflectionTestUtils.setField(provider, "envBaseUrl", "https://env.example/api");

        assertThat(provider.baseUrl()).isEqualTo("https://env.example/api");
    }

    @Test
    void baseUrl_fallsBackToEnvWhenNoSetting() {
        when(settingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        ReflectionTestUtils.setField(provider, "envBaseUrl", "https://env.example/api");

        assertThat(provider.baseUrl()).isEqualTo("https://env.example/api");
    }

    @Test
    void token_prefersDatabaseSetting() {
        LedgerlySetting setting = new LedgerlySetting();
        setting.setApiToken("db-token");
        when(settingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(setting));
        ReflectionTestUtils.setField(provider, "envToken", "env-token");

        assertThat(provider.token()).isEqualTo("db-token");
    }

    @Test
    void token_fallsBackToEnvWhenSettingBlank() {
        when(settingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        ReflectionTestUtils.setField(provider, "envToken", "env-token");

        assertThat(provider.token()).isEqualTo("env-token");
    }

    @Test
    void webhookSecret_prefersDatabaseSetting() {
        LedgerlySetting setting = new LedgerlySetting();
        setting.setWebhookSecret("db-whsec");
        when(settingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(setting));
        ReflectionTestUtils.setField(provider, "envWebhookSecret", "env-whsec");

        assertThat(provider.webhookSecret()).isEqualTo("db-whsec");
    }

    @Test
    void webhookSecret_fallsBackToEnvWhenSettingMissing() {
        when(settingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        ReflectionTestUtils.setField(provider, "envWebhookSecret", "env-whsec");

        assertThat(provider.webhookSecret()).isEqualTo("env-whsec");
    }
}
