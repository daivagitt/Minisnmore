package com.miniandmore.app.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestClient;

class ZohoBooksIntegrationClientTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private ZohoBooksIntegrationClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl("http://127.0.0.1:8083");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ZohoBooksIntegrationClient(builder, "http://127.0.0.1:8083", "test-key");
    }

    @AfterEach
    void verifyServer() {
        server.verify();
    }

    @Test
    void syncOrder_postsWithInternalApiKey() {
        server
            .expect(MockRestRequestMatchers.requestTo("http://127.0.0.1:8083/internal/v1/zoho/triggers/sync-orders/42"))
            .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
            .andExpect(MockRestRequestMatchers.header("X-Internal-Api-Key", "test-key"))
            .andRespond(MockRestResponseCreators.withSuccess("{}", MediaType.APPLICATION_JSON));

        client.syncOrder(42L);
    }

    @Test
    void syncProductAsync_postsAsyncEndpoint() {
        server
            .expect(MockRestRequestMatchers.requestTo("http://127.0.0.1:8083/internal/v1/zoho/triggers/sync-products/7/async"))
            .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
            .andRespond(MockRestResponseCreators.withSuccess("{}", MediaType.APPLICATION_JSON));

        client.syncProductAsync(7L);
    }

    @Test
    void pullOrderTracking_postsTrackingEndpoint() {
        server
            .expect(MockRestRequestMatchers.requestTo("http://127.0.0.1:8083/internal/v1/zoho/triggers/pull-order-tracking/9"))
            .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
            .andRespond(MockRestResponseCreators.withSuccess("{}", MediaType.APPLICATION_JSON));

        client.pullOrderTracking(9L);
    }

    @Test
    void createCreditNote_returnsCreditNoteId() {
        server
            .expect(MockRestRequestMatchers.requestTo("http://127.0.0.1:8083/internal/v1/zoho/triggers/credit-notes"))
            .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
            .andRespond(
                MockRestResponseCreators.withSuccess("{\"zohoCreditNoteId\":\"cn_123\"}", MediaType.APPLICATION_JSON)
            );

        assertThat(client.createCreditNote(1L, "RET-1")).contains("cn_123");
    }

    @Test
    void createCreditNote_blankId_returnsEmpty() {
        server
            .expect(MockRestRequestMatchers.requestTo("http://127.0.0.1:8083/internal/v1/zoho/triggers/credit-notes"))
            .andRespond(MockRestResponseCreators.withSuccess("{\"zohoCreditNoteId\":\"\"}", MediaType.APPLICATION_JSON));

        assertThat(client.createCreditNote(1L, "RET-1")).isEmpty();
    }

    @Test
    void post_withoutApiKey_stillWorks() {
        RestClient.Builder noKeyBuilder = RestClient.builder().baseUrl("http://127.0.0.1:8083");
        MockRestServiceServer noKeyServer = MockRestServiceServer.bindTo(noKeyBuilder).build();
        ZohoBooksIntegrationClient noKeyClient = new ZohoBooksIntegrationClient(noKeyBuilder, "http://127.0.0.1:8083", "");
        noKeyServer
            .expect(MockRestRequestMatchers.requestTo("http://127.0.0.1:8083/internal/v1/zoho/triggers/sync-orders/1"))
            .andRespond(MockRestResponseCreators.withSuccess("{}", MediaType.APPLICATION_JSON));

        noKeyClient.syncOrder(1L);
        noKeyServer.verify();
    }
}
