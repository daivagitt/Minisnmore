package com.miniandmore.app.web.rest.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miniandmore.app.domain.ZohoSetting;
import com.miniandmore.app.repository.ZohoSettingRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(ZohoSettingsResource.class)
@AutoConfigureMockMvc(addFilters = false)
class ZohoSettingsResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ZohoSettingRepository settingRepository;

    @Test
    void getSettings_whenMissing_returnsNullBody() throws Exception {
        when(settingRepository.findActiveOrLatest()).thenReturn(Optional.empty());

        MvcResult result = mockMvc.perform(get("/api/v1/admin/zoho-settings")).andExpect(status().isOk()).andReturn();

        assertThat(result.getResponse().getContentAsString()).isBlank();
    }

    @Test
    void getSettings_whenPresent_masksSecrets() throws Exception {
        ZohoSetting setting = new ZohoSetting()
            .orgId("org-1")
            .clientId("client-1")
            .clientSecret("secret-1")
            .refreshToken("refresh-1")
            .dataCenter("in")
            .connected(true);
        when(settingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));

        mockMvc
            .perform(get("/api/v1/admin/zoho-settings"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orgId").value("org-1"))
            .andExpect(jsonPath("$.clientId").value("client-1"))
            .andExpect(jsonPath("$.hasClientSecret").value(true))
            .andExpect(jsonPath("$.hasRefreshToken").value(true))
            .andExpect(jsonPath("$.dataCenter").value("in"))
            .andExpect(jsonPath("$.connected").value(true))
            .andExpect(jsonPath("$.clientSecret").doesNotExist())
            .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @Test
    void saveSettings_persistsProvidedFields() throws Exception {
        ZohoSetting setting = new ZohoSetting();
        when(settingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));
        when(settingRepository.save(any(ZohoSetting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc
            .perform(
                put("/api/v1/admin/zoho-settings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"orgId\":\"org-99\",\"clientId\":\"client-99\",\"clientSecret\":\"secret-99\",\"refreshToken\":\"refresh-99\",\"dataCenter\":\"eu\",\"connected\":true}"
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ok").value(true));

        ArgumentCaptor<ZohoSetting> captor = ArgumentCaptor.forClass(ZohoSetting.class);
        verify(settingRepository).save(captor.capture());
        ZohoSetting saved = captor.getValue();
        assertThat(saved.getOrgId()).isEqualTo("org-99");
        assertThat(saved.getClientId()).isEqualTo("client-99");
        assertThat(saved.getClientSecret()).isEqualTo("secret-99");
        assertThat(saved.getRefreshToken()).isEqualTo("refresh-99");
        assertThat(saved.getDataCenter()).isEqualTo("eu");
        assertThat(saved.getConnected()).isTrue();
    }

    @Test
    void saveSettings_blankClientSecretDoesNotOverwriteExistingValue() throws Exception {
        ZohoSetting setting = new ZohoSetting().clientSecret("keep-me");
        when(settingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));
        when(settingRepository.save(any(ZohoSetting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc
            .perform(
                put("/api/v1/admin/zoho-settings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"clientSecret\":\"   \"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ok").value(true));

        ArgumentCaptor<ZohoSetting> captor = ArgumentCaptor.forClass(ZohoSetting.class);
        verify(settingRepository).save(captor.capture());
        assertThat(captor.getValue().getClientSecret()).isEqualTo("keep-me");
    }

    @Test
    void saveSettings_refreshTokenMarksConnectedWhenFlagOmitted() throws Exception {
        ZohoSetting setting = new ZohoSetting().connected(false);
        when(settingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));
        when(settingRepository.save(any(ZohoSetting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc
            .perform(
                put("/api/v1/admin/zoho-settings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"refreshToken\":\"refresh-only\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ok").value(true));

        ArgumentCaptor<ZohoSetting> captor = ArgumentCaptor.forClass(ZohoSetting.class);
        verify(settingRepository).save(captor.capture());
        assertThat(captor.getValue().getRefreshToken()).isEqualTo("refresh-only");
        assertThat(captor.getValue().getConnected()).isTrue();
    }

    @Test
    void saveSettings_explicitConnectedFalseIsHonored() throws Exception {
        ZohoSetting setting = new ZohoSetting().refreshToken("refresh-1").connected(true);
        when(settingRepository.findActiveOrLatest()).thenReturn(Optional.of(setting));
        when(settingRepository.save(any(ZohoSetting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc
            .perform(
                put("/api/v1/admin/zoho-settings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"connected\":false}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ok").value(true));

        ArgumentCaptor<ZohoSetting> captor = ArgumentCaptor.forClass(ZohoSetting.class);
        verify(settingRepository).save(captor.capture());
        assertThat(captor.getValue().getConnected()).isFalse();
        assertThat(captor.getValue().getRefreshToken()).isEqualTo("refresh-1");
    }
}
