package com.miniandmore.app.web.rest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.client.LedgerlyIntegrationClient;
import com.miniandmore.app.repository.*;
import com.miniandmore.app.service.LedgerlySyncService;
import com.miniandmore.app.service.OrderEventService;
import com.miniandmore.app.service.ZohoSyncService;
import com.miniandmore.app.service.StorePricingService;
import com.miniandmore.app.service.StorePricingTestSupport;
import com.miniandmore.app.web.rest.v1.AccountOrderDataAccess;
import com.miniandmore.app.web.rest.internal.InternalAccountOrderResource.VerifyPaymentRequest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InternalAccountOrderResourceReflectionTest {

    private static final String SECRET = "test_secret";

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

    @Mock
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private InternalAccountOrderResource resource;

    @BeforeEach
    void setUp() {
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
            new ObjectMapper(),
            new com.miniandmore.app.config.ApplicationProperties(),
            ledgerlySyncService,
            zohoSyncService,
            StorePricingTestSupport.variantSupport(new ObjectMapper()),
            StorePricingTestSupport.defaultPricing(),
            orderEventService,
            razorpayRefundService,
            transactionManager
        );
        ReflectionTestUtils.setField(resource, "razorpayKeyId", "rzp_live_abc");
        ReflectionTestUtils.setField(resource, "razorpayKeySecret", SECRET);
    }

    @Test
    void hexToBytes_invalidInputs_returnEmptyArray() {
        assertThat((byte[]) ReflectionTestUtils.invokeMethod(resource, "hexToBytes", (Object) null)).isEmpty();
        assertThat((byte[]) ReflectionTestUtils.invokeMethod(resource, "hexToBytes", "abc")).isEmpty();
        assertThat((byte[]) ReflectionTestUtils.invokeMethod(resource, "hexToBytes", "zz")).isEmpty();
        assertThat((byte[]) ReflectionTestUtils.invokeMethod(resource, "hexToBytes", "aabb")).hasSize(2);
    }

    @Test
    void validRazorpaySignature_matchingSignature_returnsTrue() throws Exception {
        String orderId = "order_1";
        String paymentId = "pay_1";
        String signature = hmac(orderId, paymentId);
        VerifyPaymentRequest request = new VerifyPaymentRequest(1L, orderId, paymentId, signature);

        boolean valid = Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(resource, "validRazorpaySignature", request));

        assertThat(valid).isTrue();
    }

    @Test
    void validRazorpaySignature_blankSecret_returnsFalse() {
        ReflectionTestUtils.setField(resource, "razorpayKeySecret", "  ");
        VerifyPaymentRequest request = new VerifyPaymentRequest(1L, "o", "p", "ab");

        boolean valid = Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(resource, "validRazorpaySignature", request));

        assertThat(valid).isFalse();
    }

    @Test
    void gatewayMode_liveAndUnknownKeys() {
        assertThat((String) ReflectionTestUtils.invokeMethod(resource, "gatewayMode")).isEqualTo("live");
        ReflectionTestUtils.setField(resource, "razorpayKeyId", "custom");
        assertThat((String) ReflectionTestUtils.invokeMethod(resource, "gatewayMode")).isEqualTo("unknown");
    }

    @Test
    void isDemoMode_demoKey_returnsTrue() {
        ReflectionTestUtils.setField(resource, "razorpayKeyId", "DEMO");
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(resource, "isDemoMode")).isTrue();
    }

    @Test
    void createRazorpayOrder_liveMode_callsApiAndFailsGracefully() {
        ReflectionTestUtils.setField(resource, "razorpayKeyId", "rzp_test_invalid_key");
        ReflectionTestUtils.setField(resource, "razorpayKeySecret", "invalid_secret");

        assertThatThrownBy(() ->
            ReflectionTestUtils.invokeMethod(resource, "createRazorpayOrder", "MNM-LIVE", java.math.BigDecimal.valueOf(100), "customer1")
        ).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void computeRazorpaySignature_matchesVerifyPath() throws Exception {
        String signature = (String) ReflectionTestUtils.invokeMethod(resource, "computeRazorpaySignature", "order_2", "pay_2");
        VerifyPaymentRequest request = new VerifyPaymentRequest(2L, "order_2", "pay_2", signature);
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(resource, "validRazorpaySignature", request)).isTrue();
    }

    private static String hmac(String orderId, String paymentId) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal((orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8)));
    }
}
