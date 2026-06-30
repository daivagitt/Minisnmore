package com.miniandmore.app.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.miniandmore.app.service.LedgerlyCredentialsProvider;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class LedgerlyIntegrationClientTest {

    @Mock
    private LedgerlyCredentialsProvider credentials;

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer server;
    private LedgerlyIntegrationClient client;

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        client = new LedgerlyIntegrationClient(restClientBuilder, credentials);
    }

    @Test
    void createCreditNote_returnsIdWhenPresent() {
        when(credentials.baseUrl()).thenReturn("http://ledgerly.test/api");
        when(credentials.token()).thenReturn("lt_test");

        server
            .expect(requestTo("http://ledgerly.test/api/v1/integrations/commerce/orders/ORD-1/credit-notes"))
            .andExpect(header("Authorization", "Bearer lt_test"))
            .andExpect(content().json("{\"returnNumber\":\"RET-ORD-1\"}"))
            .andRespond(withSuccess("{\"ledgerlyCreditNoteId\":\"cn-42\"}", MediaType.APPLICATION_JSON));

        Optional<String> id = client.createCreditNote("ORD-1", "RET-ORD-1");

        assertThat(id).contains("cn-42");
        server.verify();
    }

    @Test
    void createCreditNote_blankId_returnsEmpty() {
        when(credentials.baseUrl()).thenReturn("http://ledgerly.test/api");
        when(credentials.token()).thenReturn("lt_test");

        server
            .expect(requestTo("http://ledgerly.test/api/v1/integrations/commerce/orders/ORD-1/credit-notes"))
            .andRespond(withSuccess("{\"ledgerlyCreditNoteId\":\"\"}", MediaType.APPLICATION_JSON));

        assertThat(client.createCreditNote("ORD-1", "RET-ORD-1")).isEmpty();
        server.verify();
    }

    @Test
    void syncOrderPayload_sendsIdempotencyKeyWhenPresent() {
        when(credentials.baseUrl()).thenReturn("http://ledgerly.test/api/");
        when(credentials.token()).thenReturn("lt_test");

        server
            .expect(requestTo("http://ledgerly.test/api/v1/integrations/commerce/orders/sync"))
            .andExpect(header("Idempotency-Key", "pay_abc"))
            .andExpect(header("Authorization", "Bearer lt_test"))
            .andRespond(withSuccess("{\"ledgerlyInvoiceId\":\"inv-1\"}", MediaType.APPLICATION_JSON));

        Map<String, Object> response = client.syncOrderPayload(Map.of("externalRef", "ORD-1"), "pay_abc");

        assertThat(response).containsEntry("ledgerlyInvoiceId", "inv-1");
        server.verify();
    }

    @Test
    void testConnection_usesDefaultBaseUrlWhenBlank() {
        when(credentials.baseUrl()).thenReturn("");
        when(credentials.token()).thenReturn("");

        server
            .expect(requestTo("http://127.0.0.1:8080/api/v1/platform/integration/status"))
            .andExpect(headerDoesNotExist("Authorization"))
            .andRespond(withSuccess("{\"connected\":true}", MediaType.APPLICATION_JSON));

        Map<String, Object> status = client.testConnection();

        assertThat(status).containsEntry("connected", true);
        server.verify();
    }

    @Test
    void pullOrderTracking_buildsPathFromExternalRef() {
        when(credentials.baseUrl()).thenReturn("http://ledgerly.test/api");
        when(credentials.token()).thenReturn("lt_test");

        server
            .expect(requestTo("http://ledgerly.test/api/v1/integrations/commerce/orders/MNM-100/tracking/pull"))
            .andRespond(withSuccess("{\"found\":true,\"status\":\"shipped\"}", MediaType.APPLICATION_JSON));

        Map<String, Object> response = client.pullOrderTracking("MNM-100");

        assertThat(response).containsEntry("found", true).containsEntry("status", "shipped");
        server.verify();
    }
}
