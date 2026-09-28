package com.miniandmore.commerce.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
class AdminOrdersProxyResourceTest {

    @Mock
    private CatalogInternalClient catalog;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminOrdersProxyResource(catalog)).build();
    }

    @Test
    void codPending_delegatesToCatalog() throws Exception {
        when(catalog.get("/internal/v1/admin/orders/cod-pending")).thenReturn(ResponseEntity.ok("[]"));

        mockMvc.perform(get("/api/v1/admin/orders/cod-pending")).andExpect(status().isOk()).andExpect(content().string("[]"));

        verify(catalog).get("/internal/v1/admin/orders/cod-pending");
    }

    @Test
    void orderedLogins_delegatesToCatalog() throws Exception {
        when(catalog.get("/internal/v1/admin/orders/ordered-logins"))
            .thenReturn(ResponseEntity.ok("{\"logins\":[\"today.customer\"]}"));

        mockMvc
            .perform(get("/api/v1/admin/orders/ordered-logins"))
            .andExpect(status().isOk())
            .andExpect(content().string("{\"logins\":[\"today.customer\"]}"));

        verify(catalog).get("/internal/v1/admin/orders/ordered-logins");
    }

    @Test
    void listOrders_forwardsDateRangeToCatalog() throws Exception {
        String internalPath =
            "/internal/v1/admin/orders?from=2026-08-01T00:00:00Z&to=2026-08-08T00:00:00Z";
        when(catalog.get(internalPath)).thenReturn(ResponseEntity.ok("{\"orders\":[]}"));

        mockMvc
            .perform(
                get("/api/v1/admin/orders")
                    .param("from", "2026-08-01T00:00:00Z")
                    .param("to", "2026-08-08T00:00:00Z")
            )
            .andExpect(status().isOk())
            .andExpect(content().string("{\"orders\":[]}"));

        verify(catalog).get(internalPath);
    }

    @Test
    void listOrders_forwardsPaginationWithoutDateRange() throws Exception {
        String internalPath = "/internal/v1/admin/orders?page=2&size=10";
        when(catalog.get(internalPath)).thenReturn(ResponseEntity.ok("{\"orders\":[]}"));

        mockMvc
            .perform(get("/api/v1/admin/orders").param("page", "2").param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(content().string("{\"orders\":[]}"));

        verify(catalog).get(internalPath);
    }

    @Test
    void listOrders_forwardsFiltersToCatalog() throws Exception {
        String internalPath =
            "/internal/v1/admin/orders?q=MNM-1&status=confirmed&paymentStatus=paid&paymentMethod=cod&zohoSyncStatus=attention";
        when(catalog.get(internalPath)).thenReturn(ResponseEntity.ok("{\"orders\":[]}"));

        mockMvc
            .perform(
                get("/api/v1/admin/orders")
                    .param("q", "MNM-1")
                    .param("status", "confirmed")
                    .param("paymentStatus", "paid")
                    .param("paymentMethod", "cod")
                    .param("zohoSyncStatus", "attention")
            )
            .andExpect(status().isOk())
            .andExpect(content().string("{\"orders\":[]}"));

        verify(catalog).get(internalPath);
    }

    @Test
    void markCodCollected_delegatesToCatalog() throws Exception {
        when(catalog.post(eq("/internal/v1/admin/orders/MNM-42/mark-cod-collected"), eq(null)))
            .thenReturn(ResponseEntity.ok("{\"ok\":true}"));

        mockMvc
            .perform(post("/api/v1/admin/orders/MNM-42/mark-cod-collected"))
            .andExpect(status().isOk())
            .andExpect(content().string("{\"ok\":true}"));

        verify(catalog).post(eq("/internal/v1/admin/orders/MNM-42/mark-cod-collected"), eq(null));
    }

    @Test
    void getOrder_delegatesToCatalog() throws Exception {
        when(catalog.get("/internal/v1/admin/orders/MNM-42")).thenReturn(ResponseEntity.ok("{\"order\":{}}"));

        mockMvc
            .perform(get("/api/v1/admin/orders/MNM-42"))
            .andExpect(status().isOk())
            .andExpect(content().string("{\"order\":{}}"));

        verify(catalog).get("/internal/v1/admin/orders/MNM-42");
    }

    @Test
    void bookShipment_delegatesToCatalog() throws Exception {
        when(catalog.post(eq("/internal/v1/admin/orders/MNM-42/book-shipment"), any()))
            .thenReturn(ResponseEntity.ok("{\"order\":{}}"));

        mockMvc
            .perform(
                post("/api/v1/admin/orders/MNM-42/book-shipment")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content("{\"mode\":\"xpressbees\"}")
            )
            .andExpect(status().isOk())
            .andExpect(content().string("{\"order\":{}}"));

        verify(catalog).post(eq("/internal/v1/admin/orders/MNM-42/book-shipment"), any());
    }

    @Test
    void packageRates_delegatesToCatalog() throws Exception {
        when(catalog.post(eq("/internal/v1/admin/orders/MNM-42/package-rates"), any()))
            .thenReturn(ResponseEntity.ok("{\"chargeable_weight_grams\":500}"));

        mockMvc
            .perform(
                post("/api/v1/admin/orders/MNM-42/package-rates")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content("{\"length_cm\":10,\"breadth_cm\":10,\"height_cm\":10}")
            )
            .andExpect(status().isOk())
            .andExpect(content().string("{\"chargeable_weight_grams\":500}"));

        verify(catalog).post(eq("/internal/v1/admin/orders/MNM-42/package-rates"), any());
    }

    @Test
    void completePackage_delegatesToCatalog() throws Exception {
        when(catalog.post(eq("/internal/v1/admin/orders/MNM-42/complete-package"), any()))
            .thenReturn(ResponseEntity.ok("{\"order\":{}}"));

        mockMvc
            .perform(
                post("/api/v1/admin/orders/MNM-42/complete-package")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content("{\"length_cm\":10,\"breadth_cm\":10,\"height_cm\":10}")
            )
            .andExpect(status().isOk())
            .andExpect(content().string("{\"order\":{}}"));

        verify(catalog).post(eq("/internal/v1/admin/orders/MNM-42/complete-package"), any());
    }
}
