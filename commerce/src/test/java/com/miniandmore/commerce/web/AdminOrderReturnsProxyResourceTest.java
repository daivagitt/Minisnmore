package com.miniandmore.commerce.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
class AdminOrderReturnsProxyResourceTest {

    @Mock
    private CatalogInternalClient catalog;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminOrderReturnsProxyResource(catalog)).build();
    }

    @Test
    void list_withStatusQuery() throws Exception {
        when(catalog.get(any())).thenReturn(ResponseEntity.ok("[]"));
        mockMvc.perform(get("/api/v1/admin/order-returns").param("status", "PENDING")).andExpect(status().isOk());
    }

    @Test
    void summary_delegatesToCatalog() throws Exception {
        when(catalog.get("/internal/v1/admin/order-returns/summary")).thenReturn(ResponseEntity.ok("{}"));
        mockMvc.perform(get("/api/v1/admin/order-returns/summary")).andExpect(status().isOk());
    }

    @Test
    void list_withoutStatus_andApprove() throws Exception {
        when(catalog.get("/internal/v1/admin/order-returns")).thenReturn(ResponseEntity.ok("[]"));
        when(catalog.post(eq("/internal/v1/admin/order-returns/R1/approve"), any())).thenReturn(ResponseEntity.ok("{}"));
        when(catalog.post(eq("/internal/v1/admin/order-returns/R1/reject"), any())).thenReturn(ResponseEntity.ok("{}"));
        when(catalog.post(eq("/internal/v1/admin/order-returns/R1/mark-received"), any())).thenReturn(ResponseEntity.ok("{}"));

        mockMvc.perform(get("/api/v1/admin/order-returns")).andExpect(status().isOk());
        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/order-returns/R1/approve")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content("{}")
            )
            .andExpect(status().isOk());
        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/order-returns/R1/reject")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content("{}")
            )
            .andExpect(status().isOk());
        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/order-returns/R1/mark-received")
            )
            .andExpect(status().isOk());
    }

    @Test
    void refund_delegatesToCatalog() throws Exception {
        when(catalog.post(eq("/internal/v1/admin/order-returns/R2/refund"), any())).thenReturn(ResponseEntity.ok("{}"));

        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/order-returns/R2/refund")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content("{}")
            )
            .andExpect(status().isOk());
    }

    @Test
    void newTransitionEndpoints_delegateToCatalog() throws Exception {
        String[] paths = {
            "pickup-requested",
            "picked-up",
            "inspect",
            "accept",
            "allocate-replacement",
            "convert-to-refund",
            "repacking",
            "ready-to-ship",
            "shipped",
            "delivered",
            "complete",
            "record-cod-refund",
            "book-pickup",
            "pickup-failed",
            "reschedule-pickup",
            "close-pickup-failed",
            "settle-price-difference",
            "book-outbound",
        };
        for (String path : paths) {
            when(catalog.post(eq("/internal/v1/admin/order-returns/R3/" + path), any())).thenReturn(ResponseEntity.ok("{}"));
            mockMvc
                .perform(
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/order-returns/R3/" + path)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}")
                )
                .andExpect(status().isOk());
        }
    }
}
