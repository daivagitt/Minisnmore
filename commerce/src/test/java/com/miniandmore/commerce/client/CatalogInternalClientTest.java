package com.miniandmore.commerce.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;

import com.miniandmore.commerce.config.ApplicationProperties;
import com.miniandmore.commerce.security.SecurityUtils;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.mockito.MockedStatic;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class CatalogInternalClientTest {

    @Mock
    private ApplicationProperties applicationProperties;

    private MockRestServiceServer mockServer;
    private CatalogInternalClient client;

    @BeforeEach
    void setUp() {
        when(applicationProperties.getCatalogBaseUrl()).thenReturn("http://127.0.0.1:8084");
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new CatalogInternalClient(applicationProperties, builder);
    }

    @Test
    void encodedPath_encodesVariables() {
        assertThat(CatalogInternalClient.encodedPath("/internal/v1/orders/{id}", 42L)).isEqualTo("/internal/v1/orders/42");
    }

    @Test
    void get_withQueryString() {
        mockServer
            .expect(request -> assertThat(request.getURI().toString()).isEqualTo("http://127.0.0.1:8084/internal/v1/account/orders?limit=5"))
            .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess("[]", MediaType.APPLICATION_JSON));

        var response = client.get("/internal/v1/account/orders?limit=5");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("[]");
        mockServer.verify();
    }

    @Test
    void postPublic_sendsJsonBody() {
        mockServer
            .expect(request -> {
                assertThat(request.getMethod()).isEqualTo(HttpMethod.POST);
                assertThat(request.getURI().getPath()).isEqualTo("/internal/v1/coupons/validate");
            })
            .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess("{}", MediaType.APPLICATION_JSON));

        var response = client.postPublic("/internal/v1/coupons/validate", java.util.Map.of("code", "SAVE10"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        mockServer.verify();
    }

    @Test
    void postEmpty_sendsEmptyMap() {
        mockServer
            .expect(request -> assertThat(request.getMethod()).isEqualTo(HttpMethod.POST))
            .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess("ok", MediaType.TEXT_PLAIN));

        var response = client.postEmpty("/internal/v1/account/orders/1/verify-payment");
        assertThat(response.getBody()).isEqualTo("ok");
        mockServer.verify();
    }

    @Test
    void put_and_getPublic() {
        mockServer
            .expect(request -> assertThat(request.getMethod()).isEqualTo(HttpMethod.PUT))
            .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess("{}", MediaType.APPLICATION_JSON));
        mockServer
            .expect(request -> assertThat(request.getMethod()).isEqualTo(HttpMethod.GET))
            .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess("ok", MediaType.TEXT_PLAIN));

        assertThat(client.put("/internal/v1/profile", java.util.Map.of()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(client.getPublic("/internal/v1/track/1").getBody()).isEqualTo("ok");
        mockServer.verify();
    }

    @Test
    void exchange_returnsUtf8Body() {
        mockServer
            .expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("http://127.0.0.1:8084/internal/v1/ping"))
            .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess("ping", MediaType.TEXT_PLAIN));

        var response = client.exchange(HttpMethod.GET, "/internal/v1/ping", null, false);
        assertThat(response.getBody()).isEqualTo("ping");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        mockServer.verify();
    }

    @Test
    void exchange_emptyBody_returnsNullBody() {
        mockServer
            .expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("http://127.0.0.1:8084/internal/v1/empty"))
            .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent());

        var response = client.exchange(HttpMethod.GET, "/internal/v1/empty", null, false);
        assertThat(response.getBody()).isNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        mockServer.verify();
    }

    @Test
    void delete_forwardsAuthorizationHeader() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::currentBearerToken).thenReturn("Bearer commerce-token");
            mockServer
                .expect(requestTo("http://127.0.0.1:8084/internal/v1/account/orders/9"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer commerce-token"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess());

            assertThat(client.delete("/internal/v1/account/orders/9").getStatusCode()).isEqualTo(HttpStatus.OK);
            mockServer.verify();
        }
    }

    @Test
    void post_forwardsAuthorizationAndJsonBody() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::currentBearerToken).thenReturn("Bearer commerce-token");
            mockServer
                .expect(requestTo("http://127.0.0.1:8084/internal/v1/account/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer commerce-token"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess("{}", MediaType.APPLICATION_JSON));

            var response = client.post("/internal/v1/account/orders", java.util.Map.of("sku", "X"));
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            mockServer.verify();
        }
    }

    @Test
    void postMultipart_uploadsFileWithAuth() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::currentBearerToken).thenReturn("Bearer commerce-token");
            mockServer
                .expect(requestTo("http://127.0.0.1:8084/internal/v1/admin/uploads"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer commerce-token"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess("{\"url\":\"x\"}", MediaType.APPLICATION_JSON));

            MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "bytes".getBytes(StandardCharsets.UTF_8));
            var response = client.postMultipart("/internal/v1/admin/uploads", file);
            assertThat(response.getBody()).contains("url");
            mockServer.verify();
        }
    }
}
