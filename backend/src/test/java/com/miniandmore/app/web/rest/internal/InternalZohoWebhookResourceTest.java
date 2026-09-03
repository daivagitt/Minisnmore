package com.miniandmore.app.web.rest.internal;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miniandmore.app.config.SecurityConfiguration;
import com.miniandmore.app.service.ZohoInvoiceWebhookHandlerService;
import com.miniandmore.app.service.ZohoOrderStatusWebhookHandlerService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = { InternalZohoInvoiceWebhookResource.class, InternalZohoOrderStatusWebhookResource.class }
)
@Import({ SecurityConfiguration.class, InternalZohoWebhookResourceTest.JwtConfig.class })
@AutoConfigureMockMvc
class InternalZohoWebhookResourceTest {

    private static final String INVOICE_BODY = "{\"eventId\":\"evt-rest\",\"invoice_id\":\"inv-rest\",\"status\":\"sent\"}";
    private static final String ORDER_BODY = "{\"eventId\":\"evt-rest-so\",\"salesorder_id\":\"so-rest\",\"status\":\"void\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ZohoInvoiceWebhookHandlerService invoiceWebhookHandlerService;

    @MockitoBean
    private ZohoOrderStatusWebhookHandlerService orderStatusWebhookHandlerService;

    @Test
    void invoiceWebhook_isPublicWithoutJwt() throws Exception {
        when(invoiceWebhookHandlerService.handle(eq(INVOICE_BODY), eq(null), eq("invoice-secret"))).thenReturn(
            Map.of("ok", true, "eventId", "evt-rest", "orderNumber", "MNM-REST", "status", "sent")
        );

        mockMvc
            .perform(
                post("/api/v1/zoho/invoice-webhook")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Zoho-Invoice-Secret", "invoice-secret")
                    .content(INVOICE_BODY)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ok").value(true))
            .andExpect(jsonPath("$.data.eventId").value("evt-rest"));

        verify(invoiceWebhookHandlerService).handle(INVOICE_BODY, null, "invoice-secret");
    }

    @Test
    void orderStatusWebhook_isPublicWithoutJwt() throws Exception {
        when(orderStatusWebhookHandlerService.handle(eq(ORDER_BODY), eq(null), eq("order-secret"))).thenReturn(
            Map.of("ok", true, "eventId", "evt-rest-so", "orderNumber", "MNM-SO", "status", "CANCELLED")
        );

        mockMvc
            .perform(
                post("/internal/v1/zoho/order-status-webhook")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Zoho-Order-Status-Secret", "order-secret")
                    .content(ORDER_BODY)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        verify(orderStatusWebhookHandlerService).handle(ORDER_BODY, null, "order-secret");
    }

    @TestConfiguration
    static class JwtConfig {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token).header("alg", "none").claim("sub", "user").build();
        }
    }
}
