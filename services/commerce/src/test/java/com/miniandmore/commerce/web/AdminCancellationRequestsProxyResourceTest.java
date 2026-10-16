package com.miniandmore.commerce.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miniandmore.commerce.client.CatalogInternalClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AdminCancellationRequestsProxyResourceTest {

    @Mock
    private CatalogInternalClient catalog;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminCancellationRequestsProxyResource(catalog)).build();
    }

    @Test
    void list_withStatusQuery() throws Exception {
        when(catalog.get(any())).thenReturn(ResponseEntity.ok("[]"));
        mockMvc.perform(get("/api/v1/admin/cancellation-requests").param("status", "REQUESTED")).andExpect(status().isOk());
    }

    @Test
    void list_withoutStatusQuery() throws Exception {
        when(catalog.get("/internal/v1/admin/cancellation-requests")).thenReturn(ResponseEntity.ok("[]"));
        mockMvc.perform(get("/api/v1/admin/cancellation-requests")).andExpect(status().isOk());
    }

    @Test
    void approve_success() throws Exception {
        when(catalog.post(eq("/internal/v1/admin/cancellation-requests/CAN-101/approve"), any()))
            .thenReturn(ResponseEntity.ok("{}"));

        mockMvc
            .perform(
                post("/api/v1/admin/cancellation-requests/CAN-101/approve")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"adminNotes\":\"ok\"}")
            )
            .andExpect(status().isOk());
    }

    @Test
    void reject_success() throws Exception {
        when(catalog.post(eq("/internal/v1/admin/cancellation-requests/CAN-102/reject"), any()))
            .thenReturn(ResponseEntity.ok("{}"));

        mockMvc
            .perform(
                post("/api/v1/admin/cancellation-requests/CAN-102/reject")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"rejectionReason\":\"Dispatched\"}")
            )
            .andExpect(status().isOk());
    }

    @Test
    void recordCodRefund_success() throws Exception {
        when(catalog.post(eq("/internal/v1/admin/cancellation-requests/CAN-103/record-cod-refund"), any()))
            .thenReturn(ResponseEntity.ok("{}"));

        mockMvc
            .perform(
                post("/api/v1/admin/cancellation-requests/CAN-103/record-cod-refund")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"method\":\"upi\",\"reference\":\"TXN-1\"}")
            )
            .andExpect(status().isOk());
    }

    @Test
    void retryRefund_success() throws Exception {
        when(catalog.post(eq("/internal/v1/admin/cancellation-requests/CAN-104/retry-refund"), any()))
            .thenReturn(ResponseEntity.ok("{}"));

        mockMvc.perform(post("/api/v1/admin/cancellation-requests/CAN-104/retry-refund")).andExpect(status().isOk());
    }
}
