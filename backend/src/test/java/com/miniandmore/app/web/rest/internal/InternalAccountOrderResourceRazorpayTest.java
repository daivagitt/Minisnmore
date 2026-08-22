package com.miniandmore.app.web.rest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.client.LedgerlyIntegrationClient;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.repository.*;
import com.miniandmore.app.service.LedgerlySyncService;
import com.miniandmore.app.service.OrderEventService;
import com.miniandmore.app.service.ZohoSyncService;
import com.miniandmore.app.service.ProductVariantSupport;
import com.miniandmore.app.web.rest.v1.AccountOrderDataAccess;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class InternalAccountOrderResourceRazorpayTest {

    private static final String SECRET = "razorpay_test_secret";

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private CustomerProfileRepository customerProfileRepository;

    @Mock
    private CustomerOrderRepository customerOrderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SiteSettingRepository siteSettingRepository;

    @Mock
    private LedgerlyIntegrationClient ledgerlyIntegrationClient;

    @Mock
    private LedgerlySyncService ledgerlySyncService;

    @Mock
    private ZohoSyncService zohoSyncService;

    @Mock
    private OrderEventService orderEventService;

    @Mock
    private com.miniandmore.app.service.RazorpayRefundService razorpayRefundService;

    private InternalAccountOrderResource resource;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        AccountOrderDataAccess dataAccess = new AccountOrderDataAccess(
            couponRepository,
            customerProfileRepository,
            customerOrderRepository,
            orderItemRepository,
            productRepository,
            siteSettingRepository,
            ledgerlyIntegrationClient
        );
        resource = new InternalAccountOrderResource(
            dataAccess,
            objectMapper,
            ledgerlySyncService,
            zohoSyncService,
            new ProductVariantSupport(objectMapper),
            orderEventService,
            razorpayRefundService
        );
        ReflectionTestUtils.setField(resource, "razorpayKeyId", "rzp_test_key");
        ReflectionTestUtils.setField(resource, "razorpayKeySecret", SECRET);
        ReflectionTestUtils.setField(resource, "razorpayPaymentDemo", false);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("customer1", "n/a"));
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void findSuccessfulRazorpayPayment_capturedPayment_returnsItem() throws Exception {
        try (MockedStatic<HttpClient> httpStatic = mockStatic(HttpClient.class)) {
            stubRazorpayResponse(
                httpStatic,
                200,
                """
                {"items":[{"id":"pay_cap","status":"captured","amount":50000}]}
                """
            );

            @SuppressWarnings("unchecked")
            Optional<com.fasterxml.jackson.databind.JsonNode> result = (Optional<com.fasterxml.jackson.databind.JsonNode>) ReflectionTestUtils.invokeMethod(
                resource,
                "findSuccessfulRazorpayPayment",
                "order_cap"
            );

            assertThat(result).isPresent();
            assertThat(result.orElseThrow().path("id").asText()).isEqualTo("pay_cap");
        }
    }

    @Test
    void findSuccessfulRazorpayPayment_authorizedPayment_returnsItem() throws Exception {
        try (MockedStatic<HttpClient> httpStatic = mockStatic(HttpClient.class)) {
            stubRazorpayResponse(
                httpStatic,
                200,
                """
                {"items":[{"id":"pay_auth","status":"authorized","amount":10000}]}
                """
            );

            @SuppressWarnings("unchecked")
            Optional<com.fasterxml.jackson.databind.JsonNode> result = (Optional<com.fasterxml.jackson.databind.JsonNode>) ReflectionTestUtils.invokeMethod(
                resource,
                "findSuccessfulRazorpayPayment",
                "order_auth"
            );

            assertThat(result).isPresent();
            assertThat(result.orElseThrow().path("status").asText()).isEqualTo("authorized");
        }
    }

    @Test
    void findSuccessfulRazorpayPayment_noSuccessfulStatus_returnsEmpty() throws Exception {
        try (MockedStatic<HttpClient> httpStatic = mockStatic(HttpClient.class)) {
            stubRazorpayResponse(httpStatic, 200, """
                {"items":[{"id":"pay_fail","status":"failed","amount":50000}]}
                """);

            @SuppressWarnings("unchecked")
            Optional<com.fasterxml.jackson.databind.JsonNode> result = (Optional<com.fasterxml.jackson.databind.JsonNode>) ReflectionTestUtils.invokeMethod(
                resource,
                "findSuccessfulRazorpayPayment",
                "order_fail"
            );

            assertThat(result).isEmpty();
        }
    }

    @Test
    void findSuccessfulRazorpayPayment_httpError_returnsEmpty() throws Exception {
        try (MockedStatic<HttpClient> httpStatic = mockStatic(HttpClient.class)) {
            stubRazorpayResponse(httpStatic, 502, "bad gateway");

            @SuppressWarnings("unchecked")
            Optional<com.fasterxml.jackson.databind.JsonNode> result = (Optional<com.fasterxml.jackson.databind.JsonNode>) ReflectionTestUtils.invokeMethod(
                resource,
                "findSuccessfulRazorpayPayment",
                "order_err"
            );

            assertThat(result).isEmpty();
        }
    }

    @Test
    void razorpayPost_success_returnsOrderId() throws Exception {
        try (MockedStatic<HttpClient> httpStatic = mockStatic(HttpClient.class)) {
            stubRazorpayResponse(httpStatic, 200, """
                {"id":"order_new_123","amount":50000}
                """);

            com.fasterxml.jackson.databind.JsonNode result = (com.fasterxml.jackson.databind.JsonNode) ReflectionTestUtils.invokeMethod(
                resource,
                "razorpayPost",
                "/v1/orders",
                """
                {"amount":50000,"currency":"INR"}
                """
            );

            assertThat(result.path("id").asText()).isEqualTo("order_new_123");
        }
    }

    @Test
    void razorpayRequest_ioFailure_throwsBadGateway() {
        try (MockedStatic<HttpClient> httpStatic = mockStatic(HttpClient.class)) {
            HttpClient.Builder builder = mock(HttpClient.Builder.class);
            HttpClient client = mock(HttpClient.class);
            httpStatic.when(HttpClient::newBuilder).thenReturn(builder);
            when(builder.connectTimeout(any())).thenReturn(builder);
            when(builder.build()).thenReturn(client);
            try {
                when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenThrow(new java.io.IOException("connection reset"));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }

            assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(resource, "razorpayGet", "/v1/orders/x/payments"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Razorpay request failed");
        }
    }

    @Test
    void razorpayRequest_invalidJson_throwsBadGateway() throws Exception {
        try (MockedStatic<HttpClient> httpStatic = mockStatic(HttpClient.class)) {
            stubRazorpayResponse(httpStatic, 200, "not-json");

            assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(resource, "razorpayGet", "/v1/orders/x/payments"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("parse failed");
        }
    }

    @Test
    void createRazorpayOrder_success_returnsId() throws Exception {
        try (MockedStatic<HttpClient> httpStatic = mockStatic(HttpClient.class)) {
            stubRazorpayResponse(httpStatic, 200, """
                {"id":"order_created_99"}
                """);

            String id = (String) ReflectionTestUtils.invokeMethod(
                resource,
                "createRazorpayOrder",
                "MNM-99",
                BigDecimal.valueOf(500),
                "customer1"
            );

            assertThat(id).isEqualTo("order_created_99");
        }
    }

    @Test
    void createRazorpayOrder_missingId_throwsBadGateway() throws Exception {
        try (MockedStatic<HttpClient> httpStatic = mockStatic(HttpClient.class)) {
            stubRazorpayResponse(httpStatic, 200, """
                {"amount":50000}
                """);

            assertThatThrownBy(() ->
                ReflectionTestUtils.invokeMethod(
                    resource,
                    "createRazorpayOrder",
                    "MNM-BAD",
                    BigDecimal.valueOf(500),
                    "customer1"
                )
            )
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("missing id");
        }
    }

    @Test
    void syncPayment_capturedPayment_confirmsOrder() throws Exception {
        CustomerOrder order = new CustomerOrder()
            .id(100L)
            .userLogin("customer1")
            .orderNumber("MNM-SYNC-OK")
            .paymentStatus(PaymentStatus.PENDING)
            .status(OrderStatus.PENDING)
            .razorpayOrderId("order_sync_ok")
            .total(BigDecimal.valueOf(500))
            .inventoryReserved(true);
        when(customerOrderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        try (MockedStatic<HttpClient> httpStatic = mockStatic(HttpClient.class)) {
            stubRazorpayResponse(
                httpStatic,
                200,
                """
                {"items":[{"id":"pay_sync_ok","status":"captured","amount":50000}]}
                """
            );

            @SuppressWarnings("unchecked")
            var response = (com.miniandmore.app.web.rest.v1.ApiResponse<Map<String, Object>>) resource.syncPayment(
                new InternalAccountOrderResource.SyncPaymentRequest(100L)
            );

            assertThat(response.data().get("paid")).isEqualTo(true);
            assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(order.getRazorpayPaymentId()).isEqualTo("pay_sync_ok");
        }
    }

    @Test
    void syncPayment_amountMismatch_returnsNotPaid() throws Exception {
        CustomerOrder order = new CustomerOrder()
            .id(101L)
            .userLogin("customer1")
            .orderNumber("MNM-MISMATCH")
            .paymentStatus(PaymentStatus.PENDING)
            .razorpayOrderId("order_mismatch")
            .total(BigDecimal.valueOf(500))
            .inventoryReserved(true);
        when(customerOrderRepository.findById(101L)).thenReturn(Optional.of(order));

        try (MockedStatic<HttpClient> httpStatic = mockStatic(HttpClient.class)) {
            stubRazorpayResponse(
                httpStatic,
                200,
                """
                {"items":[{"id":"pay_wrong","status":"captured","amount":49999}]}
                """
            );

            @SuppressWarnings("unchecked")
            var response = (com.miniandmore.app.web.rest.v1.ApiResponse<Map<String, Object>>) resource.syncPayment(
                new InternalAccountOrderResource.SyncPaymentRequest(101L)
            );

            assertThat(response.data().get("paid")).isEqualTo(false);
            assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        }
    }

    @Test
    void syncPayment_blankPaymentId_returnsNotPaid() throws Exception {
        CustomerOrder order = new CustomerOrder()
            .id(102L)
            .userLogin("customer1")
            .paymentStatus(PaymentStatus.PENDING)
            .razorpayOrderId("order_blank")
            .total(BigDecimal.valueOf(500));
        when(customerOrderRepository.findById(102L)).thenReturn(Optional.of(order));

        try (MockedStatic<HttpClient> httpStatic = mockStatic(HttpClient.class)) {
            stubRazorpayResponse(httpStatic, 200, """
                {"items":[{"id":"","status":"captured","amount":50000}]}
                """);

            @SuppressWarnings("unchecked")
            var response = (com.miniandmore.app.web.rest.v1.ApiResponse<Map<String, Object>>) resource.syncPayment(
                new InternalAccountOrderResource.SyncPaymentRequest(102L)
            );

            assertThat(response.data().get("paid")).isEqualTo(false);
        }
    }

    @Test
    void toJson_invalidPayload_throwsBadRequest() {
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(resource, "toJson", new Object() {
            @SuppressWarnings("unused")
            public String getBroken() {
                throw new RuntimeException("boom");
            }
        }))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Invalid JSON payload");
    }

    @Test
    void requireOwnedOrder_wrongUser_throwsNotFound() {
        CustomerOrder order = new CustomerOrder().id(200L).userLogin("other");
        when(customerOrderRepository.findById(200L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(resource, "requireOwnedOrder", 200L, "customer1"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Order not found");
    }

    @Test
    void razorpayRequest_interrupted_throwsBadGateway() {
        try (MockedStatic<HttpClient> httpStatic = mockStatic(HttpClient.class)) {
            HttpClient.Builder builder = mock(HttpClient.Builder.class);
            HttpClient client = mock(HttpClient.class);
            httpStatic.when(HttpClient::newBuilder).thenReturn(builder);
            when(builder.connectTimeout(any())).thenReturn(builder);
            when(builder.build()).thenReturn(client);
            try {
                when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenThrow(new InterruptedException("interrupted"));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }

            assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(resource, "razorpayGet", "/v1/orders/x/payments"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("interrupted");
        }
    }

    @Test
    void validRazorpaySignature_malformedMac_returnsFalse() {
        ReflectionTestUtils.setField(resource, "razorpayKeySecret", "secret");
        InternalAccountOrderResource.VerifyPaymentRequest request = new InternalAccountOrderResource.VerifyPaymentRequest(
            1L,
            "order_1",
            "pay_1",
            "abcd"
        );
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(resource, "validRazorpaySignature", request)).isFalse();
    }

    @Test
    void requireOwnedOrder_missingOrder_throwsNotFound() {
        when(customerOrderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(resource, "requireOwnedOrder", 999L, "customer1"))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void requireLogin_unauthenticated_throwsUnauthorized() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(resource, "requireLogin"))
            .isInstanceOf(ResponseStatusException.class);
    }

    @SuppressWarnings("unchecked")
    private static void stubRazorpayResponse(MockedStatic<HttpClient> httpStatic, int statusCode, String body) throws Exception {
        HttpClient.Builder builder = mock(HttpClient.Builder.class);
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        httpStatic.when(HttpClient::newBuilder).thenReturn(builder);
        when(builder.connectTimeout(any())).thenReturn(builder);
        when(builder.build()).thenReturn(client);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
    }
}
