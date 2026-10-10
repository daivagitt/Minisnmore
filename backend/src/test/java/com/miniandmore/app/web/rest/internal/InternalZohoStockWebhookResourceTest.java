package com.miniandmore.app.web.rest.internal;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miniandmore.app.config.SecurityConfiguration;
import com.miniandmore.app.service.ZohoStockWebhookHandlerService;
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

@WebMvcTest(controllers = InternalZohoStockWebhookResource.class)
@Import({ SecurityConfiguration.class, InternalZohoStockWebhookResourceTest.JwtConfig.class })
@AutoConfigureMockMvc
class InternalZohoStockWebhookResourceTest {

    private static final String BODY = "{\"eventId\":\"evt-stock\",\"item_id\":\"zoho-1\",\"stock_on_hand\":12}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ZohoStockWebhookHandlerService stockWebhookHandlerService;

    @Test
    void stockWebhook_isPublicAndForwardsSignature() throws Exception {
        when(stockWebhookHandlerService.handle(eq(BODY), eq("stock-signature"), eq("stock-secret"))).thenReturn(
            Map.of("ok", true, "itemId", "zoho-1", "stock", 12)
        );

        mockMvc
            .perform(
                post("/api/v1/zoho/stock-webhook")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Zoho-Stock-Signature", "stock-signature")
                    .header("X-Zoho-Stock-Secret", "stock-secret")
                    .content(BODY)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ok").value(true))
            .andExpect(jsonPath("$.data.itemId").value("zoho-1"));

        verify(stockWebhookHandlerService).handle(BODY, "stock-signature", "stock-secret");
    }

    @Test
    void stockWebhook_fallsBackToGenericZohoSignatureHeader() throws Exception {
        when(stockWebhookHandlerService.handle(eq(BODY), eq("generic-signature"), eq("stock-secret"))).thenReturn(
            Map.of("ok", true)
        );

        mockMvc
            .perform(
                post("/internal/v1/zoho/stock-webhook")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Zoho-Webhook-Signature", "generic-signature")
                    .header("X-Zoho-Stock-Secret", "stock-secret")
                    .content(BODY)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ok").value(true));

        verify(stockWebhookHandlerService).handle(BODY, "generic-signature", "stock-secret");
    }

    @TestConfiguration
    static class JwtConfig {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token).header("alg", "none").claim("sub", "user").build();
        }
    }
}
