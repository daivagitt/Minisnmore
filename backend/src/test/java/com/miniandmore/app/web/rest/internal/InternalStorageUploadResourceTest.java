package com.miniandmore.app.web.rest.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miniandmore.app.service.S3StorageService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InternalStorageUploadResource.class)
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(authorities = "ROLE_ADMIN")
class InternalStorageUploadResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private S3StorageService s3StorageService;

    @Test
    void presign_returns200() throws Exception {
        when(s3StorageService.presignUpload("products", "photo.jpg", "image/jpeg")).thenReturn(
            new S3StorageService.PresignedUpload("https://upload", "https://cdn/photo.jpg", "products/1-photo.jpg", "PUT", Map.of(), 5000000L)
        );

        mockMvc
            .perform(
                post("/internal/v1/admin/uploads/presign")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"folder\":\"products\",\"filename\":\"photo.jpg\",\"contentType\":\"image/jpeg\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.publicUrl").value("https://cdn/photo.jpg"));
    }

    @Test
    void upload_returns200() throws Exception {
        when(s3StorageService.uploadObject(eq("products"), eq("photo.jpg"), any(byte[].class), eq("image/jpeg"))).thenReturn(
            "https://cdn/products/photo.jpg"
        );

        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "image-bytes".getBytes());

        mockMvc
            .perform(multipart("/internal/v1/admin/uploads").file(file).param("folder", "products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.publicUrl").value("https://cdn/products/photo.jpg"));
    }
}
