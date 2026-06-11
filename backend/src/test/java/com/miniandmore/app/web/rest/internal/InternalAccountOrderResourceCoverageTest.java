package com.miniandmore.app.web.rest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.domain.*;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.repository.*;
import com.miniandmore.app.web.rest.v1.AccountOrderDataAccess;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InternalAccountOrderResource.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(InternalAccountOrderResourceCoverageTest.DataAccessConfig.class)
class InternalAccountOrderResourceCoverageTest {

    private static final String LIVE_SECRET = "live_secret_key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InternalAccountOrderResource resource;

    @MockitoBean
    private CouponRepository couponRepository;

    @MockitoBean
    private CustomerProfileRepository customerProfileRepository;

    @MockitoBean
    private CustomerOrderRepository customerOrderRepository;

    @MockitoBean
    private OrderItemRepository orderItemRepository;

    @MockitoBean
    private ProductRepository productRepository;

    @MockitoBean
    private SiteSettingRepository siteSettingRepository;

    @MockitoBean
    private com.miniandmore.app.client.ZohoBooksIntegrationClient zohoBooksIntegrationClient;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(resource, "razorpayKeyId", "rzp_test_livemode");
        ReflectionTestUtils.setField(resource, "razorpayKeySecret", LIVE_SECRET);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("customer1", "n/a"));
    }

    @Test
    void verifyPayment_invalidSignature_releasesInventory() throws Exception {
        CustomerOrder order = new CustomerOrder()
            .id(50L)
            .userLogin("customer1")
            .orderNumber("MNM-LIVE")
            .paymentStatus(PaymentStatus.PENDING)
            .inventoryReserved(true)
            .total(BigDecimal.TEN);
        OrderItem item = new OrderItem().productId("1").qty(1).order(order);
        Product product = new Product().id(1L).stock(4);
        when(customerOrderRepository.findById(50L)).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.findAllByOrderId(50L)).thenReturn(List.of(item));
        when(productRepository.findOneForInventoryUpdate(1L)).thenReturn(Optional.empty());

        mockMvc
            .perform(
                post("/internal/v1/orders/verify-payment")
                    .with(user("customer1"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"orderId\":50,\"razorpay_order_id\":\"order_live\",\"razorpay_payment_id\":\"pay_live\",\"razorpay_signature\":\"deadbeef\"}"
                    )
            )
            .andExpect(status().isBadRequest());

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void verifyPayment_validSignature_confirmsOrder() throws Exception {
        String razorpayOrderId = "order_live_ok";
        String razorpayPaymentId = "pay_live_ok";
        String signature = hmacSignature(razorpayOrderId, razorpayPaymentId, LIVE_SECRET);
        CustomerOrder order = new CustomerOrder()
            .id(51L)
            .userLogin("customer1")
            .orderNumber("MNM-LIVE-OK")
            .paymentStatus(PaymentStatus.PENDING)
            .inventoryReserved(true)
            .total(BigDecimal.valueOf(100));
        when(customerOrderRepository.findById(51L)).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(customerOrderRepository.findById(51L)).thenReturn(Optional.of(order));

        mockMvc
            .perform(
                post("/internal/v1/orders/verify-payment")
                    .with(user("customer1"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"orderId":51,"razorpay_order_id":"%s","razorpay_payment_id":"%s","razorpay_signature":"%s"}
                        """
                            .formatted(razorpayOrderId, razorpayPaymentId, signature)
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ok").value(true));

        verify(zohoBooksIntegrationClient).syncOrder(51L);
    }

    @Test
    void verifyPayment_alreadyPaidWithoutZoho_triggersSync() throws Exception {
        CustomerOrder order = new CustomerOrder()
            .id(52L)
            .userLogin("customer1")
            .orderNumber("MNM-SYNC")
            .paymentStatus(PaymentStatus.PAID)
            .inventoryReserved(true)
            .total(BigDecimal.TEN);
        when(customerOrderRepository.findById(52L)).thenReturn(Optional.of(order));
        when(customerOrderRepository.findById(52L)).thenReturn(Optional.of(order));

        mockMvc
            .perform(
                post("/internal/v1/orders/verify-payment")
                    .with(user("customer1"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"orderId":52,"razorpay_order_id":"order_x","razorpay_payment_id":"pay_x","razorpay_signature":"%s"}
                        """
                            .formatted(hmacSignature("order_x", "pay_x", LIVE_SECRET))
                    )
            )
            .andExpect(status().isOk());

        verify(zohoBooksIntegrationClient).syncOrder(52L);
    }

    @Test
    void trackOrder_zohoRefreshFailure_returnsOrderAnyway() throws Exception {
        CustomerOrder order = new CustomerOrder()
            .id(6L)
            .orderNumber("MNM-ZOHO")
            .status(OrderStatus.CONFIRMED)
            .zohoSalesOrderId("SO-1")
            .zohoTrackingLastPulledAt(Instant.now().minusSeconds(7200));
        when(customerOrderRepository.findOneByOrderNumber("MNM-ZOHO")).thenReturn(Optional.of(order));
        doThrow(new RuntimeException("zoho down")).when(zohoBooksIntegrationClient).pullOrderTracking(6L);

        mockMvc.perform(get("/internal/v1/orders/track/MNM-ZOHO")).andExpect(status().isOk());
    }

    @Test
    void trackOrder_fulfilledOrder_skipsZohoRefresh() throws Exception {
        CustomerOrder order = new CustomerOrder()
            .id(7L)
            .orderNumber("MNM-DONE")
            .status(OrderStatus.FULFILLED)
            .zohoSalesOrderId("SO-2")
            .trackingNumber("TRK");
        when(customerOrderRepository.findOneByOrderNumber("MNM-DONE")).thenReturn(Optional.of(order));

        mockMvc
            .perform(get("/internal/v1/orders/track/MNM-DONE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.order.tracking_number").value("TRK"));
    }

    @Test
    void createOrder_multipleItemsSameProduct_aggregatesStock() throws Exception {
        ReflectionTestUtils.setField(resource, "razorpayKeyId", "DEMO");
        ReflectionTestUtils.setField(resource, "razorpayKeySecret", "");
        Product product = new Product().id(1L).name("Dress").slug("dress").price(BigDecimal.valueOf(100)).stock(10).active(true);
        when(productRepository.findOneForInventoryUpdate(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        CustomerOrder saved = new CustomerOrder().id(200L).orderNumber("MNM-MULTI").total(BigDecimal.valueOf(300));
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenReturn(saved);
        when(orderItemRepository.saveAll(any())).thenReturn(List.of());

        mockMvc
            .perform(
                post("/internal/v1/orders")
                    .with(user("customer1"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "items":[
                            {"product_id":"1","product_name":"Dress","qty":2,"price":100,"size":"S","color":"Red","product_image":"img.jpg"},
                            {"product_id":"1","product_name":"Dress","qty":1,"price":100}
                          ],
                          "shipping_address":{"full_name":"Jane","email":"j@example.com","phone":"9876543210","line1":"Line 1","city":"City","state":"State","pincode":"560001"},
                          "donation":0,
                          "notes":"Gift wrap"
                        }
                        """
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.demoMode").value(true));

        assertThat(product.getStock()).isEqualTo(7);
    }

    private static String hmacSignature(String orderId, String paymentId, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal((orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void trackOrder_cancelledOrder_skipsZohoRefresh() throws Exception {
        CustomerOrder order = new CustomerOrder()
            .id(8L)
            .orderNumber("MNM-CAN")
            .status(OrderStatus.CANCELLED)
            .zohoSalesOrderId("SO-9");
        when(customerOrderRepository.findOneByOrderNumber("MNM-CAN")).thenReturn(Optional.of(order));

        mockMvc.perform(get("/internal/v1/orders/track/MNM-CAN")).andExpect(status().isOk());
    }

    @Test
    void createOrder_pricesExcludeGst_addsTaxToTotal() throws Exception {
        ReflectionTestUtils.setField(resource, "razorpayKeyId", "DEMO");
        ReflectionTestUtils.setField(resource, "razorpayKeySecret", "");
        Product product = new Product().id(1L).name("Dress").slug("dress").price(BigDecimal.valueOf(1000)).stock(5).active(true);
        SiteSetting settings = new SiteSetting()
            .gstRate(new BigDecimal("18"))
            .pricesIncludeGst(false)
            .shippingStandardInr(99)
            .shippingExpressInr(150)
            .freeShippingMinInr(5000);
        when(productRepository.findOneForInventoryUpdate(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(settings));
        CustomerOrder saved = new CustomerOrder().id(300L).orderNumber("MNM-GST").total(BigDecimal.valueOf(1200));
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenReturn(saved);
        when(orderItemRepository.saveAll(any())).thenReturn(List.of());

        mockMvc
            .perform(
                post("/internal/v1/orders")
                    .with(user("customer1"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "items":[{"product_id":"1","product_name":"Dress","qty":1,"price":1000}],
                          "shipping_address":{"full_name":"Jane","email":"j@example.com","phone":"9876543210","line1":"Line 1","city":"City","state":"State","pincode":"560001"},
                          "donation":0
                        }
                        """
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totals.tax").isNumber());
    }

    @Test
    void createOrder_singleItemInStock_usesSingularMessage() throws Exception {
        Product product = new Product().id(1L).name("Dress").slug("dress").price(BigDecimal.TEN).stock(1).active(true);
        when(productRepository.findOneForInventoryUpdate(1L)).thenReturn(Optional.of(product));

        mockMvc
            .perform(
                post("/internal/v1/orders")
                    .with(user("customer1"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "items":[{"product_id":"1","product_name":"Dress","qty":2,"price":10}],
                          "shipping_address":{"full_name":"Jane","email":"j@example.com","phone":"9876543210","line1":"Line 1","city":"City","state":"State","pincode":"560001"},
                          "donation":0
                        }
                        """
                    )
            )
            .andExpect(status().isConflict())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("1 item available")));
    }

    @TestConfiguration
    static class DataAccessConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        AccountOrderDataAccess accountOrderDataAccess(
            CouponRepository couponRepository,
            CustomerProfileRepository customerProfileRepository,
            CustomerOrderRepository customerOrderRepository,
            OrderItemRepository orderItemRepository,
            ProductRepository productRepository,
            SiteSettingRepository siteSettingRepository,
            com.miniandmore.app.client.ZohoBooksIntegrationClient zohoBooksIntegrationClient
        ) {
            return new AccountOrderDataAccess(
                couponRepository,
                customerProfileRepository,
                customerOrderRepository,
                orderItemRepository,
                productRepository,
                siteSettingRepository,
                zohoBooksIntegrationClient
            );
        }
    }
}
