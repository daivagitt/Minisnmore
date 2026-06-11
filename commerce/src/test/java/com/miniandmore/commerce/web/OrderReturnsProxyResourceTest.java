package com.miniandmore.commerce.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miniandmore.commerce.client.CatalogInternalClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class OrderReturnsProxyResourceTest {

    @Mock
    private CatalogInternalClient catalog;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OrderReturnsProxyResource(catalog)).build();
    }

    @Test
    void listMine_delegates() throws Exception {
        when(catalog.get("/internal/v1/order-returns")).thenReturn(ResponseEntity.ok("[]"));
        mockMvc.perform(get("/api/v1/order-returns")).andExpect(status().isOk());
        verify(catalog).get("/internal/v1/order-returns");
    }

    @Test
    void requestCancelAndEligibility() throws Exception {
        when(catalog.post(eq("/internal/v1/order-returns"), any())).thenReturn(ResponseEntity.ok("{}"));
        when(catalog.postEmpty("/internal/v1/order-returns/R1/cancel")).thenReturn(ResponseEntity.ok("{}"));
        when(catalog.get("/internal/v1/order-returns/eligibility/ORD-1")).thenReturn(ResponseEntity.ok("{}"));

        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/order-returns")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content("{}")
            )
            .andExpect(status().isOk());
        mockMvc
            .perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/order-returns/R1/cancel"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/order-returns/eligibility/ORD-1")).andExpect(status().isOk());
    }
}
