package com.miniandmore.app.web.rest.internal;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miniandmore.app.config.SecurityConfiguration;
import com.miniandmore.app.service.XpressBeesReturnWebhookHandlerService;
import com.miniandmore.app.service.XpressBeesShipmentWebhookHandlerService;
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
    controllers = { InternalXpressBeesShipmentWebhookResource.class, InternalXpressBeesReturnWebhookResource.class }
)
@Import({ SecurityConfiguration.class, InternalXpressBeesWebhookResourceTest.JwtConfig.class })
@AutoConfigureMockMvc
class InternalXpressBeesWebhookResourceTest {

    private static final String SHIPMENT_BODY = "{\"awb\":\"XB123\",\"status\":\"in_transit\"}";
    private static final String RETURN_BODY = "{\"return_awb\":\"XB-RET-9\",\"status\":\"picked_up\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private XpressBeesShipmentWebhookHandlerService shipmentWebhookHandlerService;

    @MockitoBean
    private XpressBeesReturnWebhookHandlerService returnWebhookHandlerService;

    @Test
    void shipmentWebhook_prefersHmacHeaderAndForwardsToHandler() throws Exception {
        when(
            shipmentWebhookHandlerService.handle(eq(SHIPMENT_BODY), eq("hmac-primary"), eq("shared-secret"))
        ).thenReturn(Map.of("ok", true, "awb", "XB123"));

        mockMvc
            .perform(
                post("/internal/v1/webhooks/xpressbees/shipments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Hmac-SHA256", "hmac-primary")
                    .header("X-XpressBees-Signature", "sig-fallback")
                    .header("X-XpressBees-Secret", "shared-secret")
                    .content(SHIPMENT_BODY)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ok").value(true))
            .andExpect(jsonPath("$.data.awb").value("XB123"));

        verify(shipmentWebhookHandlerService).handle(SHIPMENT_BODY, "hmac-primary", "shared-secret");
    }

    @Test
    void shipmentWebhook_apiAliasPath_isPublicWithoutJwt() throws Exception {
        when(shipmentWebhookHandlerService.handle(eq(SHIPMENT_BODY), isNull(), isNull())).thenReturn(Map.of("ok", true));

        mockMvc
            .perform(
                post("/api/v1/webhooks/xpressbees/shipments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(SHIPMENT_BODY)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ok").value(true));
    }

    @Test
    void returnWebhook_usesAlternateSignatureWhenPrimaryMissing() throws Exception {
        when(returnWebhookHandlerService.handle(eq(RETURN_BODY), eq("alt-signature"), eq("ret-secret"))).thenReturn(
            Map.of("ok", true, "returnNumber", "RET-9")
        );

        mockMvc
            .perform(
                post("/internal/v1/webhooks/xpressbees/returns")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-XpressBees-Webhook-Signature", "alt-signature")
                    .header("X-XpressBees-Secret", "ret-secret")
                    .content(RETURN_BODY)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.returnNumber").value("RET-9"));

        verify(returnWebhookHandlerService).handle(RETURN_BODY, "alt-signature", "ret-secret");
    }

    @TestConfiguration
    static class JwtConfig {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token).header("alg", "none").claim("sub", "user").build();
        }
    }
}
