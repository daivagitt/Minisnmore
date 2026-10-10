package com.miniandmore.app.web.rest.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.app.client.LedgerlyIntegrationClient;
import com.miniandmore.app.domain.LedgerlySetting;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.LedgerlySettingRepository;
import com.miniandmore.app.repository.ProductRepository;
import com.miniandmore.app.service.LedgerlySyncService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LedgerlyAdminResourceConnectionMatrixTest {

    @Mock
    private LedgerlySettingRepository settingRepository;

    @Mock
    private LedgerlySyncService syncService;

    @Mock
    private LedgerlyIntegrationClient ledgerlyClient;

    @Mock
    private CustomerOrderRepository customerOrderRepository;

    @Mock
    private ProductRepository productRepository;

    private LedgerlyAdminResource resource;

    @BeforeEach
    void setUp() {
        resource = new LedgerlyAdminResource(
            settingRepository,
            syncService,
            ledgerlyClient,
            customerOrderRepository,
            productRepository
        );
        when(settingRepository.save(any(LedgerlySetting.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void testConnection_nullRemote_marksEmptyResultConnectedBecauseMapIsEmpty() {
        when(ledgerlyClient.testConnection()).thenReturn(null);
        when(settingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        Map<String, Object> result = resource.testConnection();

        assertThat(result.get("connected")).isEqualTo(false);
        assertThat(result.get("ok")).isEqualTo(false);
    }

    @Test
    void testConnection_emptyRemote_setsConnectedFalseBecauseEmptyMap() {
        when(ledgerlyClient.testConnection()).thenReturn(Map.of());
        when(settingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        Map<String, Object> result = resource.testConnection();

        assertThat(result.get("connected")).isEqualTo(false);
        assertThat(result.get("ok")).isEqualTo(false);
    }

    @Test
    void testConnection_explicitConnectedTrue_doesNotOverwriteFlag() {
        LedgerlySetting setting = setting(false);
        when(settingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(setting));
        when(ledgerlyClient.testConnection()).thenReturn(Map.of("connected", true, "ok", true));

        Map<String, Object> result = resource.testConnection();

        assertThat(result.get("connected")).isEqualTo(true);
        assertThat(result.get("ok")).isEqualTo(true);
        verify(settingRepository).save(setting);
        assertThat(setting.getConnected()).isTrue();
    }

    @Test
    void testConnection_okTrueWithoutConnected_derivesConnected() {
        when(ledgerlyClient.testConnection()).thenReturn(Map.of("ok", true, "message", "ready"));
        when(settingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        Map<String, Object> result = resource.testConnection();

        assertThat(result.get("connected")).isEqualTo(true);
        assertThat(result.get("ok")).isEqualTo(true);
    }

    @Test
    void testConnection_alreadyConnectedSetting_doesNotSave() {
        LedgerlySetting setting = setting(true);
        when(settingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(setting));
        when(ledgerlyClient.testConnection()).thenReturn(Map.of("connected", true));

        resource.testConnection();

        verify(settingRepository, never()).save(any());
    }

    @Test
    void testConnection_nonEmptyWithoutFlags_treatsAsConnected() {
        Map<String, Object> remote = new LinkedHashMap<>();
        remote.put("org", "1001");
        when(ledgerlyClient.testConnection()).thenReturn(remote);
        when(settingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        Map<String, Object> result = resource.testConnection();

        assertThat(result.get("connected")).isEqualTo(true);
        assertThat(result.get("ok")).isEqualTo(true);
    }

    @Test
    void testConnection_genericExceptionWithNullMessage_usesClassName() {
        when(ledgerlyClient.testConnection()).thenThrow(new RuntimeException((String) null));
        when(settingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resource.testConnection())
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                assertThat(rse.getReason()).contains("RuntimeException");
            });
    }

    @ParameterizedTest
    @MethodSource("httpStatusCases")
    void testConnection_httpErrors_coverUnauthorizedAndGeneric(int status, String expectedError) {
        when(settingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(ledgerlyClient.testConnection()).thenThrow(
            new RestClientResponseException("err", status, "err", null, null, null)
        );

        Map<String, Object> result = resource.testConnection();

        assertThat(result.get("ok")).isEqualTo(false);
        assertThat(result.get("error")).isEqualTo(expectedError);
        assertThat(result.get("status")).isEqualTo(status);
    }

    private static Object[][] httpStatusCases() {
        return new Object[][] { { 401, "unauthorized" }, { 403, "unauthorized" }, { 404, "http_404" }, { 502, "http_502" } };
    }

    @Test
    void getSettings_blankTokenAndWebhook_reportFalse() {
        LedgerlySetting setting = setting(false);
        setting.setApiToken("  ");
        setting.setWebhookSecret("");
        when(settingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(setting));

        Map<String, Object> dto = resource.getSettings().getBody();

        assertThat(dto).isNotNull();
        assertThat(dto.get("hasToken")).isEqualTo(false);
        assertThat(dto.get("hasWebhookSecret")).isEqualTo(false);
    }

    @Test
    void saveSettings_ignoresBlankTokenAndWebhook() {
        LedgerlySetting setting = setting(false);
        setting.setApiToken("keep");
        setting.setWebhookSecret("keep-wh");
        when(settingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(setting));

        resource.saveSettings(Map.of("apiToken", "  ", "webhookSecret", "", "webhook_secret", "   "));

        assertThat(setting.getApiToken()).isEqualTo("keep");
        assertThat(setting.getWebhookSecret()).isEqualTo("keep-wh");
        verify(settingRepository).save(setting);
    }

    private static LedgerlySetting setting(boolean connected) {
        LedgerlySetting setting = new LedgerlySetting();
        setting.setId(1L);
        setting.setConnected(connected);
        return setting;
    }
}
