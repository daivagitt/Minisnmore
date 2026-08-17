package com.miniandmore.app.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.miniandmore.app.service.LedgerlyCredentialsProvider;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
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
        client = new LedgerlyIntegrationClient(restClientBuilder, credentials, new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @Test
    void cancelOrder_returnsIdWhenPresent() {
        when(credentials.baseUrl()).thenReturn("http://ledgerly.test/api");
        when(credentials.token()).thenReturn("lt_test");

        server
            .expect(requestTo("http://ledgerly.test/api/v1/integrations/commerce/orders/ORD-1/cancel"))
            .andExpect(header("Authorization", "Bearer lt_test"))
            .andExpect(content().json("{\"returnNumber\":\"RET-ORD-1\",\"refundPaymentId\":\"rfnd_1\"}"))
            .andRespond(withSuccess("{\"ledgerlyCreditNoteId\":\"cn-42\",\"refunded\":true}", MediaType.APPLICATION_JSON));

        Optional<String> id = client.cancelOrder("ORD-1", "RET-ORD-1", "rfnd_1");

        assertThat(id).contains("cn-42");
        server.verify();
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

    @Test
    void syncCustomerPayload_postsCustomerBody() {
        when(credentials.baseUrl()).thenReturn("http://ledgerly.test/api");
        when(credentials.token()).thenReturn("lt_test");

        server
            .expect(requestTo("http://ledgerly.test/api/v1/integrations/commerce/customers/sync"))
            .andExpect(header("Authorization", "Bearer lt_test"))
            .andExpect(content().json("{\"email\":\"buyer@example.com\",\"name\":\"Buyer\"}"))
            .andRespond(withSuccess("{\"ledgerlyContactId\":\"contact-1\"}", MediaType.APPLICATION_JSON));

        Map<String, Object> response = client.syncCustomerPayload(Map.of("email", "buyer@example.com", "name", "Buyer"));

        assertThat(response).containsEntry("ledgerlyContactId", "contact-1");
        server.verify();
    }

    @Test
    void syncProduct_postsProductId() {
        when(credentials.baseUrl()).thenReturn("http://ledgerly.test/api");
        when(credentials.token()).thenReturn("lt_test");

        server
            .expect(requestTo("http://ledgerly.test/api/v1/integrations/commerce/products/sync"))
            .andExpect(content().json("{\"productId\":42}"))
            .andRespond(withSuccess("{\"ledgerlyItemId\":\"item-42\"}", MediaType.APPLICATION_JSON));

        client.syncProduct(42L);
        server.verify();
    }

    @Test
    void syncProductAsync_postsAsyncFlag() {
        when(credentials.baseUrl()).thenReturn("http://ledgerly.test/api");
        when(credentials.token()).thenReturn("lt_test");

        server
            .expect(requestTo("http://ledgerly.test/api/v1/integrations/commerce/products/sync"))
            .andExpect(content().json("{\"productId\":43,\"async\":true}"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.syncProductAsync(43L);
        server.verify();
    }

    @Test
    void syncOrder_postsOrderId() {
        when(credentials.baseUrl()).thenReturn("http://ledgerly.test/api");
        when(credentials.token()).thenReturn("lt_test");

        server
            .expect(requestTo("http://ledgerly.test/api/v1/integrations/commerce/orders/sync"))
            .andExpect(content().json("{\"orderId\":7}"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.syncOrder(7L);
        server.verify();
    }

    @Test
    void deactivateProduct_postsInactivePayload() {
        when(credentials.baseUrl()).thenReturn("http://ledgerly.test/api");
        when(credentials.token()).thenReturn("lt_test");

        server
            .expect(requestTo("http://ledgerly.test/api/v1/integrations/commerce/products/sync"))
            .andExpect(content().json("{\"productId\":8,\"active\":false}"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.deactivateProduct(8L);
        server.verify();
    }

    @Test
    void pullProducts_postsEmptyBody() {
        when(credentials.baseUrl()).thenReturn("http://ledgerly.test/api");
        when(credentials.token()).thenReturn("lt_test");

        server
            .expect(requestTo("http://ledgerly.test/api/v1/integrations/commerce/products/pull"))
            .andRespond(withSuccess("{\"products\":[]}", MediaType.APPLICATION_JSON));

        Map<String, Object> response = client.pullProducts();

        assertThat(response).containsKey("products");
        server.verify();
    }

    @Test
    void pullInventory_postsEmptyBody() {
        when(credentials.baseUrl()).thenReturn("http://ledgerly.test/api");
        when(credentials.token()).thenReturn("lt_test");

        server
            .expect(requestTo("http://ledgerly.test/api/v1/integrations/commerce/inventory/pull"))
            .andRespond(withSuccess("{\"inventory\":[]}", MediaType.APPLICATION_JSON));

        Map<String, Object> response = client.pullInventory();

        assertThat(response).containsKey("inventory");
        server.verify();
    }

    @Test
    void syncProductPayload_failureRethrowsAfterLogging() {
        when(credentials.baseUrl()).thenReturn("http://ledgerly.test/api");
        when(credentials.token()).thenReturn("lt_test");

        server
            .expect(requestTo("http://ledgerly.test/api/v1/integrations/commerce/products/sync"))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("{\"error\":\"bad\"}").contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.syncProductPayload(Map.of("sku", "BAD")))
            .isInstanceOf(org.springframework.web.client.RestClientResponseException.class);
        server.verify();
    }

    @Test
    void cancelOrder_withoutReturnNumber_postsEmptyBody() {
        when(credentials.baseUrl()).thenReturn("http://ledgerly.test/api");
        when(credentials.token()).thenReturn("lt_test");

        server
            .expect(requestTo("http://ledgerly.test/api/v1/integrations/commerce/orders/ORD-2/cancel"))
            .andExpect(content().json("{}"))
            .andRespond(withSuccess("{\"ledgerlyCreditNoteId\":\"cn-2\"}", MediaType.APPLICATION_JSON));

        Optional<String> id = client.cancelOrder("ORD-2", null, null);

        assertThat(id).contains("cn-2");
        server.verify();
    }

    @Test
    void createCreditNote_nullResponse_returnsEmpty() {
        when(credentials.baseUrl()).thenReturn("http://ledgerly.test/api");
        when(credentials.token()).thenReturn("lt_test");

        server
            .expect(requestTo("http://ledgerly.test/api/v1/integrations/commerce/orders/ORD-1/credit-notes"))
            .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));

        assertThat(client.createCreditNote("ORD-1", "RET-1")).isEmpty();
        server.verify();
    }

    @Test
    void syncOrderPayload_withoutIdempotencyKey_omitsHeader() {
        when(credentials.baseUrl()).thenReturn("http://ledgerly.test/api");
        when(credentials.token()).thenReturn("lt_test");

        server
            .expect(requestTo("http://ledgerly.test/api/v1/integrations/commerce/orders/sync"))
            .andExpect(headerDoesNotExist("Idempotency-Key"))
            .andRespond(withSuccess("{\"ledgerlyInvoiceId\":\"inv-2\"}", MediaType.APPLICATION_JSON));

        Map<String, Object> response = client.syncOrderPayload(Map.of("externalRef", "ORD-2"), " ");

        assertThat(response).containsEntry("ledgerlyInvoiceId", "inv-2");
        server.verify();
    }

    @Test
    void createCreditNote_withAmount_includesAmountInBody() {
        when(credentials.baseUrl()).thenReturn("http://ledgerly.test/api");
        when(credentials.token()).thenReturn("lt_test");

        server
            .expect(requestTo("http://ledgerly.test/api/v1/integrations/commerce/orders/ORD-1/credit-notes"))
            .andExpect(content().json("{\"returnNumber\":\"RET-ORD-1\",\"amount\":52.52}"))
            .andRespond(withSuccess("{\"ledgerlyCreditNoteId\":\"cn-99\"}", MediaType.APPLICATION_JSON));

        Optional<String> id = client.createCreditNote("ORD-1", "RET-ORD-1", new java.math.BigDecimal("52.52"));

        assertThat(id).contains("cn-99");
        server.verify();
    }
}
