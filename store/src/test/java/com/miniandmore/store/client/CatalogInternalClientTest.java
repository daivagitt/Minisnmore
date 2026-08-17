package com.miniandmore.store.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.miniandmore.store.config.ApplicationProperties;
import com.miniandmore.store.security.SecurityUtils;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CatalogInternalClientTest {

    private CatalogInternalClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        ApplicationProperties props = new ApplicationProperties();
        props.setCatalogBaseUrl("http://catalog.test");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new CatalogInternalClient(props, builder);
    }

    @Test
    void get_returnsBody() {
        server.expect(requestTo("http://catalog.test/internal/v1/products")).andExpect(method(HttpMethod.GET)).andRespond(
            withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON)
        );

        ResponseEntity<String> response = client.get("/internal/v1/products");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("success");
        server.verify();
    }

    @Test
    void postPublic_postsWithoutAuth() {
        server
            .expect(requestTo("http://catalog.test/internal/v1/pincode/560001"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        ResponseEntity<String> response = client.postPublic("/internal/v1/pincode/560001", Map.of());

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        server.verify();
    }

    @Test
    void postEmpty_sendsEmptyJsonObject() {
        server
            .expect(requestTo("http://catalog.test/internal/v1/admin/ledgerly/pull-inventory"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        ResponseEntity<String> response = client.postEmpty("/internal/v1/admin/ledgerly/pull-inventory");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        server.verify();
    }

    @Test
    void put_forwardsAuthorizationHeader() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::currentBearerToken).thenReturn("Bearer token-abc");
            server
                .expect(requestTo("http://catalog.test/internal/v1/profile"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-abc"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

            ResponseEntity<String> response = client.put("/internal/v1/profile", Map.of("name", "A"));

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            server.verify();
        }
    }
}
