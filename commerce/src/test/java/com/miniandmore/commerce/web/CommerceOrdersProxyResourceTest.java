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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class CommerceOrdersProxyResourceTest {

    @Mock
    private CatalogInternalClient catalog;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CommerceOrdersProxyResource(catalog)).build();
    }

    @Test
    void validateCoupon_delegatesToCatalog() throws Exception {
        when(catalog.postPublic(eq("/internal/v1/coupons/validate"), any())).thenReturn(ResponseEntity.ok("{}"));

        mockMvc
            .perform(post("/api/v1/coupons/validate").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"X\"}"))
            .andExpect(status().isOk())
            .andExpect(content().string("{}"));

        verify(catalog).postPublic(eq("/internal/v1/coupons/validate"), any());
    }

    @Test
    void profile_getsFromCatalog() throws Exception {
        when(catalog.get("/internal/v1/account/profile")).thenReturn(ResponseEntity.status(HttpStatus.OK).body("profile"));

        mockMvc.perform(get("/api/v1/account/profile")).andExpect(status().isOk()).andExpect(content().string("profile"));
    }

    @Test
    void ordersAndPayments_delegate() throws Exception {
        when(catalog.get("/internal/v1/orders")).thenReturn(ResponseEntity.ok("[]"));
        when(catalog.get("/internal/v1/payments")).thenReturn(ResponseEntity.ok("[]"));
        when(catalog.getPublic("/internal/v1/orders/track/ORD-1")).thenReturn(ResponseEntity.ok("{}"));
        when(catalog.get("/internal/v1/orders/ORD-1")).thenReturn(ResponseEntity.ok("{}"));
        when(catalog.post(eq("/internal/v1/orders"), any())).thenReturn(ResponseEntity.ok("{}"));
        when(catalog.post(eq("/internal/v1/orders/verify-payment"), any())).thenReturn(ResponseEntity.ok("{}"));
        when(catalog.put(eq("/internal/v1/account/profile"), any())).thenReturn(ResponseEntity.ok("{}"));

        mockMvc.perform(get("/api/v1/orders")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/payments")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/orders/track/ORD-1")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/orders/ORD-1")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        mockMvc
            .perform(post("/api/v1/orders/verify-payment").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk());
        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/account/profile")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            )
            .andExpect(status().isOk());
    }
}
