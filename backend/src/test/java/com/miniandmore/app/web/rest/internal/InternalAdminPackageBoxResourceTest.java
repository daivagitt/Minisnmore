package com.miniandmore.app.web.rest.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miniandmore.app.service.PackageBoxService;
import com.miniandmore.app.service.dto.PackageBoxDTO;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InternalAdminPackageBoxResource.class)
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(authorities = "ROLE_ADMIN")
class InternalAdminPackageBoxResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PackageBoxService packageBoxService;

    @Test
    void list_returnsSavedBoxes() throws Exception {
        PackageBoxDTO dto = dto(7L, "Small carton", 20, 15, 10);
        when(packageBoxService.findAllByName()).thenReturn(List.of(dto));

        mockMvc
            .perform(get("/internal/v1/admin/package-boxes"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].name").value("Small carton"))
            .andExpect(jsonPath("$.data[0].lengthCm").value(20));
    }

    @Test
    void create_returnsSavedDto() throws Exception {
        when(packageBoxService.save(any(PackageBoxDTO.class))).thenReturn(dto(8L, "Mailer", 32, 24, 2));

        mockMvc
            .perform(
                post("/internal/v1/admin/package-boxes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Mailer\",\"lengthCm\":32,\"breadthCm\":24,\"heightCm\":2}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(8));
    }

    @Test
    void update_usesPathId() throws Exception {
        when(packageBoxService.update(any(PackageBoxDTO.class))).thenReturn(dto(8L, "Mailer", 33, 24, 2));

        mockMvc
            .perform(
                put("/internal/v1/admin/package-boxes/8")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Mailer\",\"lengthCm\":33,\"breadthCm\":24,\"heightCm\":2}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.lengthCm").value(33));
    }

    @Test
    void delete_removesRow() throws Exception {
        mockMvc.perform(delete("/internal/v1/admin/package-boxes/8")).andExpect(status().isOk());
        verify(packageBoxService).delete(8L);
    }

    private static PackageBoxDTO dto(Long id, String name, int length, int breadth, int height) {
        PackageBoxDTO dto = new PackageBoxDTO();
        dto.setId(id);
        dto.setName(name);
        dto.setLengthCm(length);
        dto.setBreadthCm(breadth);
        dto.setHeightCm(height);
        return dto;
    }
}
