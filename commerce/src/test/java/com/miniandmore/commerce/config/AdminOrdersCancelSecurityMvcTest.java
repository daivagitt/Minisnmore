package com.miniandmore.commerce.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miniandmore.commerce.CommerceApp;
import com.miniandmore.commerce.client.CatalogInternalClient;
import com.miniandmore.commerce.security.AuthoritiesConstants;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    classes = CommerceApp.class,
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
        "spring.cloud.consul.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.cloud.config.enabled=false",
    }
)
@AutoConfigureMockMvc
class AdminOrdersCancelSecurityMvcTest {

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add(
            "jhipster.security.authentication.jwt.base64-secret",
            () -> Base64.getEncoder().encodeToString(new byte[64])
        );
        registry.add("application.catalog-base-url", () -> "http://127.0.0.1:8084");
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogInternalClient catalog;

    @Test
    void cancelOrder_operationsRoleIsAllowed() throws Exception {
        when(catalog.post(eq("/internal/v1/admin/orders/MNM-1/cancel"), any()))
            .thenReturn(ResponseEntity.ok("{\"order\":{\"order_number\":\"MNM-1\"}}"));

        mockMvc
            .perform(
                post("/api/v1/admin/orders/MNM-1/cancel")
                    .with(jwt().authorities(new SimpleGrantedAuthority(AuthoritiesConstants.OPERATIONS)))
                    .contentType("application/json")
                    .content("{\"reason\":\"Item is out of stock now\"}")
            )
            .andExpect(status().isOk());
    }

    @Test
    void cancelOrder_adminRoleIsAllowed() throws Exception {
        when(catalog.post(eq("/internal/v1/admin/orders/MNM-1/cancel"), any()))
            .thenReturn(ResponseEntity.ok("{\"order\":{\"order_number\":\"MNM-1\"}}"));

        mockMvc
            .perform(
                post("/api/v1/admin/orders/MNM-1/cancel")
                    .with(jwt().authorities(new SimpleGrantedAuthority(AuthoritiesConstants.ADMIN)))
                    .contentType("application/json")
                    .content("{\"reason\":\"Item is out of stock now\"}")
            )
            .andExpect(status().isOk());
    }
}
