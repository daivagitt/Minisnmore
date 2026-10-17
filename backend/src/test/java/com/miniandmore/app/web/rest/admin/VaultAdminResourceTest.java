package com.miniandmore.app.web.rest.admin;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miniandmore.app.service.VaultSecretService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(VaultAdminResource.class)
@AutoConfigureMockMvc(addFilters = false)
class VaultAdminResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VaultSecretService vaultSecretService;

    @Test
    void list_returnsMetadataOnly() throws Exception {
        org.mockito.Mockito.when(vaultSecretService.listAdminView()).thenReturn(
            List.of(Map.of("name", "ZOHO_CLIENT_SECRET", "hasValue", true))
        );

        mockMvc
            .perform(get("/api/v1/admin/vault"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("ZOHO_CLIENT_SECRET"))
            .andExpect(jsonPath("$[0].hasValue").value(true))
            .andExpect(jsonPath("$[0].value").doesNotExist());
    }

    @Test
    void put_delegatesToService() throws Exception {
        mockMvc
            .perform(
                put("/api/v1/admin/vault/ZOHO_CLIENT_SECRET")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"value\":\"secret-1\"}")
            )
            .andExpect(status().isOk());

        verify(vaultSecretService).putSecret(eq("ZOHO_CLIENT_SECRET"), eq("secret-1"), eq("admin"));
    }

    @Test
    void delete_delegatesToService() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/vault/ZOHO_CLIENT_SECRET")).andExpect(status().isOk());
        verify(vaultSecretService).deleteSecret("ZOHO_CLIENT_SECRET");
    }
}
