package com.miniandmore.app.web.rest.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.ProductRepository;
import com.miniandmore.app.repository.ZohoSettingRepository;
import com.miniandmore.app.service.ZohoOAuthService;
import com.miniandmore.app.service.ZohoSettingsService;
import com.miniandmore.app.service.ZohoSyncService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ZohoAdminResourceTestConnectionMatrixTest {

    @Mock
    private ZohoSettingRepository settingRepository;

    @Mock
    private ZohoOAuthService oauthService;

    @Mock
    private ZohoSyncService syncService;

    @Mock
    private ZohoSettingsService zohoSettingsService;

    @Mock
    private CustomerOrderRepository customerOrderRepository;

    @Mock
    private ProductRepository productRepository;

    private ZohoAdminResource resource;

    @BeforeEach
    void setUp() {
        resource = new ZohoAdminResource(
            settingRepository,
            oauthService,
            syncService,
            zohoSettingsService,
            customerOrderRepository,
            productRepository
        );
        when(settingRepository.findActiveOrLatest()).thenReturn(java.util.Optional.empty());
    }

    @Test
    void testConnection_nullRemoteAndEmptyOrgs() {
        when(syncService.testConnection()).thenReturn(null);
        Map<String, Object> result = resource.testConnection();
        assertThat(result.get("connected")).isEqualTo(true);
        assertThat(result.get("organizationName")).isNull();

        when(syncService.testConnection()).thenReturn(Map.of("organizations", List.of()));
        assertThat(resource.testConnection().get("organizationName")).isNull();
    }

    @Test
    void testConnection_readsFirstOrgNameWhenPresent() {
        when(syncService.testConnection()).thenReturn(Map.of("organizations", List.of(Map.of("name", "Mini Org"), Map.of("name", "Other"))));
        assertThat(resource.testConnection().get("organizationName")).isEqualTo("Mini Org");

        when(syncService.testConnection()).thenReturn(Map.of("organizations", List.of(Map.of("id", "1"))));
        assertThat(resource.testConnection().get("organizationName")).isNull();

        when(syncService.testConnection()).thenReturn(Map.of("organizations", List.of("not-a-map")));
        assertThat(resource.testConnection().get("organizationName")).isNull();
    }

    @Test
    void testConnection_httpUnauthorizedVsOtherAndUnreachable() {
        doThrow(new RestClientResponseException("401", HttpStatus.UNAUTHORIZED.value(), "Unauthorized", null, null, null))
            .when(syncService)
            .testConnection();
        assertThat(resource.testConnection().get("message").toString()).contains("401");

        doThrow(new RestClientResponseException("403", HttpStatus.FORBIDDEN.value(), "Forbidden", null, null, null))
            .when(syncService)
            .testConnection();
        assertThat(resource.testConnection().get("message").toString()).contains("403");

        doThrow(new RestClientResponseException("500", HttpStatus.INTERNAL_SERVER_ERROR.value(), "Error", null, null, null))
            .when(syncService)
            .testConnection();
        assertThat(resource.testConnection().get("message").toString()).contains("HTTP 500");

        doThrow(new ResourceAccessException("down")).when(syncService).testConnection();
        assertThat(resource.testConnection().get("message").toString()).contains("Could not reach Zoho");
    }
}
