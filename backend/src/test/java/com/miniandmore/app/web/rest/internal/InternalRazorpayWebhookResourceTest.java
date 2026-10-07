package com.miniandmore.app.web.rest.internal;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miniandmore.app.config.SecurityConfiguration;
import com.miniandmore.app.service.RazorpayWebhookHandlerService;
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

@WebMvcTest(controllers = InternalRazorpayWebhookResource.class)
@Import({ SecurityConfiguration.class, InternalRazorpayWebhookResourceTest.JwtConfig.class })
@AutoConfigureMockMvc
class InternalRazorpayWebhookResourceTest {

    private static final String BODY = "{\"id\":\"evt_rest\",\"event\":\"refund.processed\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RazorpayWebhookHandlerService handler;

    @Test
    void razorpayWebhook_isPublicWithoutJwt() throws Exception {
        when(handler.handle(eq(BODY), eq("sig-hex"))).thenReturn(Map.of("ok", true, "updated", true, "order_number", "MNM-1"));

        mockMvc
            .perform(
                post("/api/v1/webhooks/razorpay")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Razorpay-Signature", "sig-hex")
                    .content(BODY)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ok").value(true))
            .andExpect(jsonPath("$.data.updated").value(true));

        verify(handler).handle(BODY, "sig-hex");
    }

    @TestConfiguration
    static class JwtConfig {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token).header("alg", "none").claim("sub", "user").build();
        }
    }
}
