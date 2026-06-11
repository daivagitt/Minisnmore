package com.miniandmore.integration.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miniandmore.integration.config.ApplicationProperties;
import com.miniandmore.integration.domain.ZohoSetting;
import com.miniandmore.integration.repository.ZohoSettingRepository;
import com.miniandmore.integration.service.ZohoOAuthService;
import com.miniandmore.integration.service.ZohoSettingService;
import com.miniandmore.integration.service.dto.ZohoSettingDTO;
import com.miniandmore.integration.service.mapper.ZohoSettingMapper;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ZohoSettingsResourceTest {

    @Mock
    private ZohoSettingRepository zohoSettingRepository;

    @Mock
    private ZohoSettingService zohoSettingService;

    @Mock
    private ZohoSettingMapper zohoSettingMapper;

    @Mock
    private ZohoOAuthService zohoOAuthService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ApplicationProperties props = new ApplicationProperties();
        mockMvc = MockMvcBuilders
            .standaloneSetup(
                new ZohoSettingsResource(zohoSettingRepository, zohoSettingService, zohoSettingMapper, zohoOAuthService, props)
            )
            .build();
    }

    @Test
    void get_returnsSanitizedDto() throws Exception {
        ZohoSetting entity = new ZohoSetting();
        entity.setClientSecret("secret");
        ZohoSettingDTO dto = new ZohoSettingDTO();
        dto.setClientSecret("secret");
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.of(entity));
        when(zohoSettingMapper.toDto(entity)).thenReturn(dto);

        mockMvc
            .perform(get("/api/v1/admin/zoho-settings"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.clientSecret").value("[REDACTED]"));
    }

    @Test
    void put_savesWhenNoExisting() throws Exception {
        when(zohoSettingRepository.findActiveOrLatest()).thenReturn(Optional.empty());
        when(zohoOAuthService.isServerConfigured()).thenReturn(false);
        ZohoSettingDTO saved = new ZohoSettingDTO();
        saved.setOrgId("org");
        when(zohoSettingService.save(any())).thenReturn(saved);

        mockMvc
            .perform(
                put("/api/v1/admin/zoho-settings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"orgId\":\"org\",\"connected\":true}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orgId").value("org"));
    }
}
