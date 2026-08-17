package com.miniandmore.store.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miniandmore.store.client.CatalogCoreClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminImportProxyResource.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminImportProxyResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogCoreClient catalogCoreClient;

    @Test
    void importSkuWisePrint_forwardsToCatalog() throws Exception {
        when(catalogCoreClient.forwardSkuWiseImport(any())).thenReturn(ResponseEntity.ok().body("{\"success\":true}".getBytes()));

        MockMultipartFile file = new MockMultipartFile("file", "sheet.xlsx", MediaType.APPLICATION_OCTET_STREAM_VALUE, "data".getBytes());

        mockMvc.perform(multipart("/api/v1/admin/import/sku-wise-print").file(file)).andExpect(status().isOk());
    }
}
