package com.miniandmore.store.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miniandmore.store.client.CatalogCoreClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class StoreCatalogProxyResourceTest {

    @Mock
    private CatalogCoreClient catalogCoreClient;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StoreCatalogProxyResource(catalogCoreClient)).build();
    }

    @Test
    void proxy_delegatesToClient() throws Exception {
        when(catalogCoreClient.forward(any())).thenReturn(ResponseEntity.status(HttpStatus.OK).body("ok".getBytes()));

        mockMvc.perform(get("/api/v1/categories")).andExpect(status().isOk()).andExpect(content().string("ok"));

        verify(catalogCoreClient).forward(any());
    }
}
