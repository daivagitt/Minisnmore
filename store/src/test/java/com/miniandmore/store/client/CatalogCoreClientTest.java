package com.miniandmore.store.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.miniandmore.store.config.ApplicationProperties;
import com.miniandmore.store.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CatalogCoreClientTest {

    private CatalogCoreClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        ApplicationProperties props = new ApplicationProperties();
        props.setCatalogBaseUrl("http://catalog.test");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new CatalogCoreClient(props, builder);
    }

    @Test
    void forward_translatesApiV1ToInternal() throws Exception {
        server
            .expect(requestTo("http://catalog.test/internal/v1/categories"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/categories");
        ResponseEntity<byte[]> response = client.forward(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(new String(response.getBody())).isEqualTo("[]");
        server.verify();
    }

    @Test
    void forward_keepsLedgerlyAdminPath() throws Exception {
        server
            .expect(requestTo("http://catalog.test/api/v1/admin/ledgerly/test"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/ledgerly/test");
        ResponseEntity<byte[]> response = client.forward(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        server.verify();
    }

    @Test
    void forwardMultipartUpload_postsToInternalUploads() throws Exception {
        server
            .expect(requestTo("http://catalog.test/internal/v1/admin/uploads"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
            "file",
            "photo.png",
            "image/png",
            "bytes".getBytes()
        );
        ResponseEntity<byte[]> response = client.forwardMultipartUpload("banners", file);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        server.verify();
    }

    @Test
    void forwardSkuWiseImport_postsToInternalSkuWiseEndpoint() throws Exception {
        server
            .expect(requestTo("http://catalog.test/internal/v1/admin/import/sku-wise-print"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"imported\":1}", MediaType.APPLICATION_JSON));

        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
            "file",
            "import.csv",
            "text/csv",
            "sku,print\nA1,front".getBytes()
        );
        ResponseEntity<byte[]> response = client.forwardSkuWiseImport(file);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(new String(response.getBody())).contains("imported");
        server.verify();
    }

    @Test
    void forward_withQueryString_forwardsQueryToCatalog() throws Exception {
        server
            .expect(requestTo("http://catalog.test/internal/v1/products?page=2&limit=10"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products");
        request.setQueryString("page=2&limit=10");
        ResponseEntity<byte[]> response = client.forward(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        server.verify();
    }

    @Test
    void forward_withPostBody_forwardsBodyToCatalog() throws Exception {
        server
            .expect(requestTo("http://catalog.test/internal/v1/products"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().json("{\"name\":\"Widget\"}"))
            .andRespond(withSuccess("{\"id\":1}", MediaType.APPLICATION_JSON));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/products");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent("{\"name\":\"Widget\"}".getBytes());
        ResponseEntity<byte[]> response = client.forward(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(new String(response.getBody())).contains("id");
        server.verify();
    }

    @Test
    void forward_markHandWrapped_translatesAdminPostToCatalogCore() throws Exception {
        server
            .expect(requestTo("http://catalog.test/internal/v1/admin/orders/MNM-42/mark-hand-wrapped"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"status\":\"hand_wrapped\"}", MediaType.APPLICATION_JSON));

        MockHttpServletRequest request = new MockHttpServletRequest(
            "POST",
            "/api/v1/admin/orders/MNM-42/mark-hand-wrapped"
        );
        ResponseEntity<byte[]> response = client.forward(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(new String(response.getBody())).contains("hand_wrapped");
        server.verify();
    }

    @Test
    void forward_keepsNonApiPathUnchanged() throws Exception {
        server
            .expect(requestTo("http://catalog.test/health"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        ResponseEntity<byte[]> response = client.forward(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(new String(response.getBody())).isEqualTo("ok");
        server.verify();
    }

    @Test
    void forward_withBearerToken_forwardsAuthorization() throws Exception {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::currentBearerToken).thenReturn("Bearer store-token");
            server
                .expect(requestTo("http://catalog.test/internal/v1/account/profile"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer store-token"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/account/profile");
            ResponseEntity<byte[]> response = client.forward(request);

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            server.verify();
        }
    }
}
