package com.miniandmore.store.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miniandmore.store.client.CatalogCoreClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AdminUploadProxyResourceTest {

    @Mock
    private CatalogCoreClient catalogCoreClient;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminUploadProxyResource(catalogCoreClient)).build();
    }

    @Test
    void upload_delegatesMultipartToCatalog() throws Exception {
        when(catalogCoreClient.forwardMultipartUpload(eq("home"), any())).thenReturn(
            ResponseEntity.status(HttpStatus.OK).body("{\"success\":true}".getBytes())
        );

        var file = new MockMultipartFile("file", "card.png", "image/png", new byte[] { 1, 2, 3 });
        mockMvc
            .perform(multipart("/api/v1/admin/uploads").file(file).param("folder", "home"))
            .andExpect(status().isOk());

        verify(catalogCoreClient).forwardMultipartUpload(eq("home"), any());
    }
}
