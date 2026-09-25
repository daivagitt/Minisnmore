package com.miniandmore.app.web.rest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.ArgumentCaptor;
import com.miniandmore.app.client.LedgerlyIntegrationClient;
import com.miniandmore.app.domain.*;
import com.miniandmore.app.domain.enumeration.DiscountType;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.domain.enumeration.CancellationRequestStatus;
import com.miniandmore.app.repository.*;
import com.miniandmore.app.service.LedgerlySyncService;
import com.miniandmore.app.service.OrderEventService;
import com.miniandmore.app.service.ZohoSyncService;
import com.miniandmore.app.web.rest.v1.AccountOrderDataAccess;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InternalAccountOrderResource.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ InternalAccountOrderResourceCoverageTest.DataAccessConfig.class, com.miniandmore.app.web.rest.errors.ResponseStatusExceptionHandler.class })
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
    private LedgerlyIntegrationClient ledgerlyIntegrationClient;

    @MockitoBean
    private LedgerlySyncService ledgerlySyncService;

    @MockitoBean
    private com.miniandmore.app.service.ZohoSyncService zohoSyncService;

    @MockitoBean
    private OrderEventService orderEventService;

    @MockitoBean
    private com.miniandmore.app.service.RazorpayRefundService razorpayRefundService;

    @MockitoBean
    private com.miniandmore.app.service.OrderCancellationService orderCancellationService;

    @MockitoBean
    private com.miniandmore.app.service.OrderShipmentService orderShipmentService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(resource, "razorpayKeyId", "rzp_test_livemode");
        ReflectionTestUtils.setField(resource, "razorpayKeySecret", LIVE_SECRET);
        ReflectionTestUtils.setField(resource, "razorpayPaymentDemo", false);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("customer1", "n/a"));
        when(customerOrderRepository.existsByUserLoginAndStatusNot("customer1", OrderStatus.CANCELLED)).thenReturn(false);
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

        verify(ledgerlySyncService).syncOrder(51L);
    }

    @Test
    void verifyPayment_alreadyPaidWithoutLedgerly_triggersSync() throws Exception {
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

        verify(ledgerlySyncService).syncOrder(52L);
    }

    @Test
    void syncPayment_alreadyPaid_returnsPaidWithoutCallingRazorpay() throws Exception {
        CustomerOrder order = new CustomerOrder()
            .id(53L)
            .userLogin("customer1")
            .orderNumber("MNM-PAID")
            .paymentStatus(PaymentStatus.PAID)
            .total(BigDecimal.TEN);
        when(customerOrderRepository.findById(53L)).thenReturn(Optional.of(order));

        mockMvc
            .perform(
                post("/internal/v1/orders/sync-payment")
                    .with(user("customer1"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"orderId\":53}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.paid").value(true))
            .andExpect(jsonPath("$.data.orderNumber").value("MNM-PAID"));
    }

    @Test
    void syncPayment_pendingWithoutRazorpayOrder_returnsNotPaid() throws Exception {
        CustomerOrder order = new CustomerOrder()
            .id(54L)
            .userLogin("customer1")
            .orderNumber("MNM-PENDING")
            .paymentStatus(PaymentStatus.PENDING)
            .total(BigDecimal.TEN);
        when(customerOrderRepository.findById(54L)).thenReturn(Optional.of(order));

        mockMvc
            .perform(
                post("/internal/v1/orders/sync-payment")
                    .with(user("customer1"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"orderId\":54}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.paid").value(false))
            .andExpect(jsonPath("$.data.payment_status").value("pending"));
    }

    @Test
    void trackOrder_doesNotRefreshLedgerlyTracking() throws Exception {
        CustomerOrder order = new CustomerOrder()
            .id(6L)
            .orderNumber("MNM-LDR")
            .status(OrderStatus.CONFIRMED)
            .ledgerlySalesOrderId("SO-1")
            .ledgerlyTrackingLastPulledAt(Instant.now().minusSeconds(7200));
        when(customerOrderRepository.findOneByOrderNumber("MNM-LDR")).thenReturn(Optional.of(order));

        mockMvc.perform(get("/internal/v1/orders/track/MNM-LDR")).andExpect(status().isOk());
        verify(ledgerlySyncService, never()).pullOrderTracking(anyLong());
    }

    @Test
    void trackOrder_fulfilledOrder_skipsLedgerlyRefresh() throws Exception {
        CustomerOrder order = new CustomerOrder()
            .id(7L)
            .orderNumber("MNM-DONE")
            .status(OrderStatus.FULFILLED)
            .ledgerlySalesOrderId("SO-2")
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

    @Test
    void createOrder_zeroCartAmount_isRejectedBeforeOrderIsSaved() throws Exception {
        Product product = new Product().id(1L).name("Free item").slug("free-item").price(BigDecimal.ZERO).stock(1).active(true);
        when(productRepository.findOneForInventoryUpdate(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        mockMvc
            .perform(
                post("/internal/v1/orders")
                    .with(user("customer1"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "items":[{"product_id":"1","product_name":"Free item","qty":1,"price":0}],
                          "shipping_address":{"full_name":"Jane","email":"j@example.com","phone":"9876543210","line1":"Line 1","city":"City","state":"State","pincode":"560001"},
                          "payment_method":"online",
                          "donation":0
                        }
                        """
                    )
            )
            .andExpect(status().isBadRequest());

        verify(customerOrderRepository, never()).save(any(CustomerOrder.class));
    }

    @Test
    void createOrder_mismatchedShippingEmail_overridesWithAccountLogin() throws Exception {
        ReflectionTestUtils.setField(resource, "razorpayKeyId", "DEMO");
        ReflectionTestUtils.setField(resource, "razorpayKeySecret", "");
        Product product = new Product().id(1L).name("Dress").slug("dress").price(BigDecimal.valueOf(100)).stock(10).active(true);
        when(productRepository.findOneForInventoryUpdate(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        ArgumentCaptor<CustomerOrder> orderCaptor = ArgumentCaptor.forClass(CustomerOrder.class);
        when(customerOrderRepository.save(orderCaptor.capture())).thenAnswer(inv -> {
            CustomerOrder order = inv.getArgument(0);
            order.setId(201L);
            order.setOrderNumber("MNM-EMAIL");
            return order;
        });
        when(orderItemRepository.saveAll(any())).thenReturn(List.of());

        mockMvc
            .perform(
                post("/internal/v1/orders")
                    .with(user("customer1"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "items":[{"product_id":"1","product_name":"Dress","qty":1,"price":100}],
                          "shipping_address":{"full_name":"Jane","email":"other@example.com","phone":"9876543210","line1":"Line 1","city":"City","state":"State","pincode":"560001"},
                          "donation":0
                        }
                        """
                    )
            )
            .andExpect(status().isOk());

        String shippingJson = orderCaptor.getValue().getShippingAddressJson();
        assertThat(shippingJson).contains("customer1");
        assertThat(shippingJson).doesNotContain("other@example.com");
    }

    @Test
    void createOrder_firstOrderWithoutCoupon_noAutomaticDiscount() throws Exception {
        ReflectionTestUtils.setField(resource, "razorpayKeyId", "DEMO");
        ReflectionTestUtils.setField(resource, "razorpayKeySecret", "");
        Product product = new Product().id(1L).name("Dress").slug("dress").price(BigDecimal.valueOf(1000)).stock(5).active(true);
        when(productRepository.findOneForInventoryUpdate(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        CustomerOrder saved = new CustomerOrder().id(400L).orderNumber("MNM-FIRST").total(BigDecimal.valueOf(1099));
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
            .andExpect(jsonPath("$.data.totals.discount").value(0.0));
    }

    @Test
    void createOrder_welcome10Coupon_appliesTenPercentDiscount() throws Exception {
        ReflectionTestUtils.setField(resource, "razorpayKeyId", "DEMO");
        ReflectionTestUtils.setField(resource, "razorpayKeySecret", "");
        Product product = new Product().id(1L).name("Dress").slug("dress").price(BigDecimal.valueOf(1000)).stock(5).active(true);
        when(productRepository.findOneForInventoryUpdate(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        Coupon coupon = new Coupon()
            .code("WELCOME10")
            .active(true)
            .discountType(com.miniandmore.app.domain.enumeration.DiscountType.PERCENT)
            .discountValue(BigDecimal.TEN)
            .minOrder(BigDecimal.ZERO);
        when(couponRepository.findOneByCodeIgnoreCase("WELCOME10")).thenReturn(Optional.of(coupon));
        CustomerOrder saved = new CustomerOrder().id(401L).orderNumber("MNM-COUPON").total(BigDecimal.valueOf(999));
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
                          "donation":0,
                          "coupon_code":"WELCOME10"
                        }
                        """
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totals.discount").value(100.0));
    }

    @Test
    void createOrder_welcome10Coupon_rejectsReturningCustomer() throws Exception {
        when(customerOrderRepository.existsByUserLoginAndStatusNot("customer1", OrderStatus.CANCELLED)).thenReturn(true);
        Product product = new Product().id(1L).name("Dress").slug("dress").price(BigDecimal.valueOf(1000)).stock(5).active(true);
        when(productRepository.findOneForInventoryUpdate(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        Coupon coupon = new Coupon()
            .code("WELCOME10")
            .active(true)
            .discountType(com.miniandmore.app.domain.enumeration.DiscountType.PERCENT)
            .discountValue(BigDecimal.TEN)
            .minOrder(BigDecimal.ZERO);
        when(couponRepository.findOneByCodeIgnoreCase("WELCOME10")).thenReturn(Optional.of(coupon));

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
                          "donation":0,
                          "coupon_code":"WELCOME10"
                        }
                        """
                    )
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    void validateCoupon_welcome10_rejectsReturningCustomer() throws Exception {
        when(customerOrderRepository.existsByUserLoginAndStatusNot("customer1", OrderStatus.CANCELLED)).thenReturn(true);
        Coupon coupon = new Coupon()
            .code("WELCOME10")
            .active(true)
            .discountType(com.miniandmore.app.domain.enumeration.DiscountType.PERCENT)
            .discountValue(BigDecimal.TEN)
            .minOrder(BigDecimal.ZERO);
        when(couponRepository.findOneByCodeIgnoreCase("WELCOME10")).thenReturn(Optional.of(coupon));

        mockMvc
            .perform(
                post("/internal/v1/coupons/validate")
                    .with(user("customer1"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "code":"WELCOME10",
                          "subtotal":1000
                        }
                        """
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ok").value(false))
            .andExpect(jsonPath("$.data.error").value("WELCOME10 is only valid on your first order"));
    }

    @Test
    void validateCoupon_welcome10_rejectsAfterPriorCodOrder() throws Exception {
        // COD stays payment PENDING until collection; prior confirmed COD must still block WELCOME10.
        when(customerOrderRepository.existsByUserLoginAndStatusNot("customer1", OrderStatus.CANCELLED)).thenReturn(true);
        Coupon coupon = new Coupon()
            .code("WELCOME10")
            .active(true)
            .discountType(com.miniandmore.app.domain.enumeration.DiscountType.PERCENT)
            .discountValue(BigDecimal.TEN)
            .minOrder(BigDecimal.ZERO);
        when(couponRepository.findOneByCodeIgnoreCase("WELCOME10")).thenReturn(Optional.of(coupon));

        mockMvc
            .perform(
                post("/internal/v1/coupons/validate")
                    .with(user("customer1"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "code":"WELCOME10",
                          "subtotal":1000
                        }
                        """
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ok").value(false))
            .andExpect(jsonPath("$.data.error").value("WELCOME10 is only valid on your first order"));
    }

    @Test
    void createOrder_inactiveProduct_rejectsCheckout() throws Exception {
        Product product = new Product().id(1L).name("Dress").slug("dress").price(BigDecimal.valueOf(1000)).stock(5).active(false);
        when(productRepository.findOneForInventoryUpdate(1L)).thenReturn(Optional.of(product));

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
            .andExpect(status().isConflict());
    }

    /**
     * Percentage discounts retain paise so checkout matches the workbook and payment amount.
     */
    @Test
    void createOrder_fractionalDiscount_preservesPaiseWhenCharging() throws Exception {
        ReflectionTestUtils.setField(resource, "razorpayKeyId", "DEMO");
        ReflectionTestUtils.setField(resource, "razorpayKeySecret", "");
        Product product = new Product().id(1L).name("Dress").slug("dress").price(BigDecimal.valueOf(999)).stock(5).active(true);
        when(productRepository.findOneForInventoryUpdate(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        Coupon coupon = new Coupon()
            .code("WELCOME10")
            .active(true)
            .discountType(com.miniandmore.app.domain.enumeration.DiscountType.PERCENT)
            .discountValue(BigDecimal.TEN)
            .minOrder(BigDecimal.ZERO);
        when(couponRepository.findOneByCodeIgnoreCase("WELCOME10")).thenReturn(Optional.of(coupon));
        CustomerOrder saved = new CustomerOrder().id(402L).orderNumber("MNM-ROUND").total(BigDecimal.valueOf(998));
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
                          "items":[{"product_id":"1","product_name":"Dress","qty":1,"price":999}],
                          "shipping_address":{"full_name":"Jane","email":"j@example.com","phone":"9876543210","line1":"Line 1","city":"City","state":"State","pincode":"560001"},
                          "donation":0,
                          "coupon_code":"WELCOME10"
                        }
                        """
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totals.discount").value(99.9))
            .andExpect(jsonPath("$.data.totals.total").value(998.1))
            .andExpect(jsonPath("$.data.amount").value(99810));
    }

    @Test
    void createOrder_prepaidShipping_matchesWorkbookAggregateGst() throws Exception {
        ReflectionTestUtils.setField(resource, "razorpayKeyId", "DEMO");
        ReflectionTestUtils.setField(resource, "razorpayKeySecret", "");
        Product product = new Product()
            .id(1L)
            .name("Body suit")
            .slug("body-suit")
            .price(new BigDecimal("279.20"))
            .stock(5)
            .active(true);
        when(productRepository.findOneForInventoryUpdate(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        Coupon coupon = new Coupon()
            .code("WELCOME10")
            .active(true)
            .discountType(com.miniandmore.app.domain.enumeration.DiscountType.PERCENT)
            .discountValue(BigDecimal.TEN)
            .minOrder(BigDecimal.ZERO);
        when(couponRepository.findOneByCodeIgnoreCase("WELCOME10")).thenReturn(Optional.of(coupon));
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> {
            CustomerOrder order = inv.getArgument(0);
            order.setId(412L);
            return order;
        });
        when(orderItemRepository.saveAll(any())).thenReturn(List.of());

        mockMvc
            .perform(
                post("/internal/v1/orders")
                    .with(user("customer1"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "items":[{"product_id":"1","product_name":"Body suit","qty":1,"price":279.20}],
                          "shipping_address":{"full_name":"Jane","email":"j@example.com","phone":"9876543210","line1":"Line 1","city":"City","state":"State","pincode":"560001"},
                          "donation":0,
                          "coupon_code":"WELCOME10"
                        }
                        """
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totals.subtotal").value(279.2))
            .andExpect(jsonPath("$.data.totals.discount").value(27.92))
            .andExpect(jsonPath("$.data.totals.shipping").value(99.0))
            .andExpect(jsonPath("$.data.totals.tax").value(11.97))
            .andExpect(jsonPath("$.data.totals.total").value(350.28))
            .andExpect(jsonPath("$.data.amount").value(35028));
    }

    /**
     * Cash on Delivery must place a real, fulfillable order without touching Razorpay: the order
     * is confirmed immediately (no online payment step to wait on), the configured COD handling fee is
     * added to the total, and the inventory hold never expires (no 30-minute TTL like online
     * orders, since there's no "abandoned checkout" to reap).
     */
    @Test
    void createOrder_cod_skipsGatewayAndChargesConfiguredHandlingFee() throws Exception {
        ReflectionTestUtils.setField(resource, "razorpayKeyId", "DEMO");
        ReflectionTestUtils.setField(resource, "razorpayKeySecret", "");
        Product product = new Product().id(1L).name("Dress").slug("dress").price(BigDecimal.valueOf(1000)).stock(5).active(true);
        when(productRepository.findOneForInventoryUpdate(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        SiteSetting settings = new SiteSetting()
            .gstRate(BigDecimal.valueOf(5))
            .pricesIncludeGst(true)
            .shippingStandardInr(99)
            .shippingExpressInr(150)
            .freeShippingMinInr(2500)
            .codFeeInr(149);
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(settings));
        CustomerOrder saved = new CustomerOrder().id(403L).orderNumber("MNM-COD").total(BigDecimal.valueOf(1148));
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> {
            CustomerOrder order = inv.getArgument(0);
            order.setId(403L);
            return order;
        });
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
                          "donation":0,
                          "payment_method":"cod"
                        }
                        """
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.cod").value(true))
            .andExpect(jsonPath("$.data.paymentMethod").value("cod"))
            // Subtotal ₹1000 + shipping ₹99 + configured GST-inclusive ₹149 COD fee = ₹1248.
            .andExpect(jsonPath("$.data.totals.cod_fee").value(149.0))
            .andExpect(jsonPath("$.data.totals.tax").value(54.72))
            .andExpect(jsonPath("$.data.totals.total").value(1248.0))
            .andExpect(jsonPath("$.data.amount").value(124800))
            .andExpect(jsonPath("$.data.razorpayOrderId").doesNotExist())
            .andExpect(jsonPath("$.data.keyId").doesNotExist());

        ArgumentCaptor<CustomerOrder> captor = ArgumentCaptor.forClass(CustomerOrder.class);
        verify(customerOrderRepository, times(2)).save(captor.capture());
        CustomerOrder persisted = captor.getAllValues().get(0);
        assertThat(persisted.getPaymentMethod()).isEqualTo("cod");
        assertThat(persisted.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(persisted.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(persisted.getInventoryReservedUntil()).isNull();

        ArgumentCaptor<CustomerProfile> profileCaptor = ArgumentCaptor.forClass(CustomerProfile.class);
        verify(customerProfileRepository).save(profileCaptor.capture());
        CustomerProfile syncedProfile = profileCaptor.getValue();
        assertThat(syncedProfile.getUserLogin()).isEqualTo("customer1");
        assertThat(syncedProfile.getFullName()).isEqualTo("Jane");
        assertThat(syncedProfile.getPhone()).isEqualTo("9876543210");
        verify(ledgerlySyncService).syncCustomerProfileAsync("customer1");
        verify(zohoSyncService).syncOrder(403L);
    }

    @Test
    void createOrder_syncsProfileFromShippingAddress() throws Exception {
        ReflectionTestUtils.setField(resource, "razorpayKeyId", "DEMO");
        ReflectionTestUtils.setField(resource, "razorpayKeySecret", "");
        Product product = new Product().id(1L).name("Dress").slug("dress").price(BigDecimal.valueOf(500)).stock(5).active(true);
        when(productRepository.findOneForInventoryUpdate(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(customerProfileRepository.findOneByUserLogin("customer1")).thenReturn(Optional.empty());
        when(customerProfileRepository.save(any(CustomerProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        CustomerOrder saved = new CustomerOrder().id(404L).orderNumber("MNM-PROFILE").total(BigDecimal.valueOf(599));
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
                          "items":[{"product_id":"1","product_name":"Dress","qty":1,"price":500}],
                          "shipping_address":{"full_name":"stagefirst","email":"minisuatregression@yopmail.com","phone":"9876543210","line1":"Line 1","city":"City","state":"State","pincode":"560001"},
                          "donation":0,
                          "payment_method":"cod"
                        }
                        """
                    )
            )
            .andExpect(status().isOk());

        ArgumentCaptor<CustomerProfile> profileCaptor = ArgumentCaptor.forClass(CustomerProfile.class);
        verify(customerProfileRepository).save(profileCaptor.capture());
        CustomerProfile syncedProfile = profileCaptor.getValue();
        assertThat(syncedProfile.getFullName()).isEqualTo("stagefirst");
        assertThat(syncedProfile.getPhone()).isEqualTo("9876543210");
        verify(ledgerlySyncService).syncCustomerProfileAsync("customer1");
    }

    private static String hmacSignature(String orderId, String paymentId, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal((orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void trackOrder_cancelledOrder_skipsLedgerlyRefresh() throws Exception {
        CustomerOrder order = new CustomerOrder()
            .id(8L)
            .orderNumber("MNM-CAN")
            .status(OrderStatus.CANCELLED)
            .ledgerlySalesOrderId("SO-9");
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

    @Test
    void activeCoupons_listsEligibleOffers() throws Exception {
        Coupon coupon = new Coupon()
            .code("SAVE5")
            .discountType(DiscountType.PERCENT)
            .discountValue(BigDecimal.valueOf(5))
            .minOrder(BigDecimal.ZERO)
            .active(true);
        when(couponRepository.findAllByOrderByIdDesc()).thenReturn(List.of(coupon));

        mockMvc
            .perform(get("/internal/v1/coupons/active"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.coupons[0].code").value("SAVE5"));
    }

    @Test
    void profile_getAndUpdate() throws Exception {
        CustomerProfile profile = new CustomerProfile().userLogin("customer1").fullName("Jane").phone("9876543210");
        when(customerProfileRepository.findOneByUserLogin("customer1")).thenReturn(Optional.of(profile));
        when(customerProfileRepository.save(any(CustomerProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc
            .perform(get("/internal/v1/account/profile").with(user("customer1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.full_name").value("Jane"))
            .andExpect(jsonPath("$.data.phone").value("9876543210"))
            .andExpect(jsonPath("$.data.subscribed").value(false));

        mockMvc
            .perform(
                put("/internal/v1/account/profile")
                    .with(user("customer1"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"full_name\":\"Janet\",\"phone\":\"9000000000\",\"subscribed\":true}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ok").value(true));

        ArgumentCaptor<CustomerProfile> updated = ArgumentCaptor.forClass(CustomerProfile.class);
        verify(customerProfileRepository).save(updated.capture());
        assertThat(updated.getValue().getSubscribed()).isTrue();
        verify(ledgerlySyncService).syncCustomerProfileAsync("customer1");
    }

    @Test
    void profile_bootstrapFromSignup() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("parent@example.com", "n/a"));
        when(customerProfileRepository.findOneByUserLogin("parent@example.com")).thenReturn(Optional.empty());
        when(customerProfileRepository.save(any(CustomerProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc
            .perform(
                post("/internal/v1/account/profile/bootstrap")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"full_name\":\"New Parent\",\"email\":\"parent@example.com\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ok").value(true));

        ArgumentCaptor<CustomerProfile> profileCaptor = ArgumentCaptor.forClass(CustomerProfile.class);
        verify(customerProfileRepository).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getFullName()).isEqualTo("New Parent");
        assertThat(profileCaptor.getValue().getSubscribed()).isFalse();
        verify(ledgerlySyncService).syncCustomerProfileAsync("parent@example.com");
    }

    @Test
    void profile_bootstrapAppliesSubscribeWhenNameAlreadySet() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("parent@example.com", "n/a"));
        CustomerProfile profile = new CustomerProfile().userLogin("parent@example.com").fullName("Jane").subscribed(false);
        when(customerProfileRepository.findOneByUserLogin("parent@example.com")).thenReturn(Optional.of(profile));
        when(customerProfileRepository.save(any(CustomerProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc
            .perform(
                post("/internal/v1/account/profile/bootstrap")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"full_name\":\"Other Name\",\"email\":\"parent@example.com\",\"subscribed\":true}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ok").value(true));

        ArgumentCaptor<CustomerProfile> profileCaptor = ArgumentCaptor.forClass(CustomerProfile.class);
        verify(customerProfileRepository).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getFullName()).isEqualTo("Jane");
        assertThat(profileCaptor.getValue().getSubscribed()).isTrue();
        verify(ledgerlySyncService).syncCustomerProfileAsync("parent@example.com");
    }

    @Test
    void profile_bootstrapSkipsWhenNameAlreadySet() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("parent@example.com", "n/a"));
        CustomerProfile profile = new CustomerProfile().userLogin("parent@example.com").fullName("Jane");
        when(customerProfileRepository.findOneByUserLogin("parent@example.com")).thenReturn(Optional.of(profile));

        mockMvc
            .perform(
                post("/internal/v1/account/profile/bootstrap")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"full_name\":\"Other Name\",\"email\":\"parent@example.com\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ok").value(true));

        verify(customerProfileRepository, never()).save(any());
        verify(ledgerlySyncService, never()).syncCustomerProfileAsync(any());
    }

    @Test
    void profile_bootstrapSkipsAdministratorAccounts() throws Exception {
        SecurityContextHolder
            .getContext()
            .setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    "admin@example.com",
                    "n/a",
                    List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))
                )
            );

        mockMvc
            .perform(
                post("/internal/v1/account/profile/bootstrap")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"full_name\":\"Store Admin\",\"email\":\"admin@example.com\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ok").value(true));

        verify(customerProfileRepository, never()).save(any());
        verify(ledgerlySyncService, never()).syncCustomerProfileAsync(any());
    }

    @Test
    void profile_bootstrapRejectsEmailMismatch() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("parent@example.com", "n/a"));

        mockMvc
            .perform(
                post("/internal/v1/account/profile/bootstrap")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"full_name\":\"New Parent\",\"email\":\"other@example.com\"}")
            )
            .andExpect(status().isBadRequest());

        verify(customerProfileRepository, never()).save(any());
        verify(ledgerlySyncService, never()).syncCustomerProfileAsync(any());
    }

    @Test
    void notifications_syncsOrdersAndReturnsEvents() throws Exception {
        CustomerOrder order = new CustomerOrder()
            .id(501L)
            .userLogin("customer1")
            .orderNumber("MNM-NOTIF1")
            .status(OrderStatus.CONFIRMED)
            .paymentStatus(PaymentStatus.PAID)
            .total(BigDecimal.valueOf(299))
            .placedAt(Instant.parse("2026-08-14T10:00:00Z"));
        when(customerOrderRepository.findAllByUserLoginOrderByIdDesc("customer1")).thenReturn(List.of(order));
        when(orderEventService.listForUser("customer1"))
            .thenReturn(
                List.of(
                    Map.of(
                        "id",
                        1L,
                        "event_type",
                        "order_placed",
                        "title",
                        "Order placed",
                        "body",
                        "MNM-NOTIF1 · ₹299",
                        "order_number",
                        "MNM-NOTIF1",
                        "created_at",
                        "2026-08-14T10:00:00Z"
                    )
                )
            );

        mockMvc
            .perform(get("/internal/v1/account/notifications").with(user("customer1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.events[0].event_type").value("order_placed"))
            .andExpect(jsonPath("$.data.events[0].title").value("Order placed"));

        verify(orderEventService).syncLifecycleEvents(order);
    }

    @Test
    void ordersAndPayments_listSummaries() throws Exception {
        CustomerOrder order = new CustomerOrder()
            .id(42L)
            .userLogin("customer1")
            .orderNumber("MNM-42")
            .status(OrderStatus.CONFIRMED)
            .paymentStatus(PaymentStatus.PAID)
            .paymentMethod("online")
            .total(BigDecimal.valueOf(500))
            .placedAt(Instant.now());
        when(customerOrderRepository.findAllByUserLoginOrderByIdDesc("customer1")).thenReturn(List.of(order));
        when(orderItemRepository.findAllByOrderId(42L)).thenReturn(List.of());

        mockMvc
            .perform(get("/internal/v1/orders").with(user("customer1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orders[0].order_number").value("MNM-42"));

        mockMvc
            .perform(get("/internal/v1/payments").with(user("customer1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.payments[0].order_number").value("MNM-42"));
    }

    @Test
    void orderDetail_returnsOwnedOrder() throws Exception {
        CustomerOrder order = new CustomerOrder()
            .id(55L)
            .userLogin("customer1")
            .orderNumber("MNM-55")
            .status(OrderStatus.CONFIRMED)
            .paymentStatus(PaymentStatus.PAID)
            .paymentMethod("online")
            .subtotal(BigDecimal.valueOf(400))
            .discount(BigDecimal.ZERO)
            .shipping(BigDecimal.ZERO)
            .codFee(BigDecimal.ZERO)
            .tax(BigDecimal.ZERO)
            .donation(BigDecimal.ZERO)
            .total(BigDecimal.valueOf(400))
            .placedAt(Instant.now());
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin("MNM-55", "customer1")).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrderId(55L)).thenReturn(List.of());

        mockMvc
            .perform(get("/internal/v1/orders/MNM-55").with(user("customer1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.order.order_number").value("MNM-55"))
            .andExpect(jsonPath("$.data.order.subtotal").value(400));
    }

    @Test
    void retryPayment_reopensPendingSession() throws Exception {
        CustomerOrder order = new CustomerOrder()
            .id(77L)
            .userLogin("customer1")
            .orderNumber("MNM-77")
            .paymentStatus(PaymentStatus.PENDING)
            .razorpayOrderId("order_rzp_77")
            .total(BigDecimal.valueOf(299));
        when(customerOrderRepository.findById(77L)).thenReturn(Optional.of(order));

        mockMvc
            .perform(
                post("/internal/v1/orders/retry-payment")
                    .with(user("customer1"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"orderId\":77}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.razorpayOrderId").value("order_rzp_77"))
            .andExpect(jsonPath("$.data.amount").value(29900));
    }

    @Test
    void retryPayment_alreadyPaid_returnsConflict() throws Exception {
        CustomerOrder order = new CustomerOrder()
            .id(78L)
            .userLogin("customer1")
            .paymentStatus(PaymentStatus.PAID)
            .razorpayOrderId("order_rzp_78");
        when(customerOrderRepository.findById(78L)).thenReturn(Optional.of(order));

        mockMvc
            .perform(
                post("/internal/v1/orders/retry-payment")
                    .with(user("customer1"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"orderId\":78}")
            )
            .andExpect(status().isConflict());
    }

    @Test
    void releaseExpiredInventoryReservations_releasesStock() {
        CustomerOrder expired = new CustomerOrder()
            .id(90L)
            .userLogin("customer1")
            .paymentStatus(PaymentStatus.PENDING)
            .inventoryReserved(true);
        when(customerOrderRepository.findExpiredInventoryReservations(eq(PaymentStatus.PENDING), any(Instant.class), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(expired)));
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        resource.releaseExpiredInventoryReservations();

        assertThat(expired.getInventoryReserved()).isFalse();
        verify(customerOrderRepository).save(expired);
    }

    @Test
    void syncPayment_liveOrderNoCapturedPayment_returnsNotPaid() throws Exception {
        CustomerOrder order = new CustomerOrder()
            .id(56L)
            .userLogin("customer1")
            .orderNumber("MNM-SYNC-LIVE")
            .paymentStatus(PaymentStatus.PENDING)
            .razorpayOrderId("order_no_payments_yet")
            .total(BigDecimal.valueOf(500));
        when(customerOrderRepository.findById(56L)).thenReturn(Optional.of(order));

        mockMvc
            .perform(
                post("/internal/v1/orders/sync-payment")
                    .with(user("customer1"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"orderId\":56}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.paid").value(false));
    }

    @Test
    void syncPayment_demoMode_returnsNotPaidWithoutRazorpayLookup() throws Exception {
        ReflectionTestUtils.setField(resource, "razorpayPaymentDemo", true);
        CustomerOrder order = new CustomerOrder()
            .id(57L)
            .userLogin("customer1")
            .orderNumber("MNM-SYNC-DEMO")
            .paymentStatus(PaymentStatus.PENDING)
            .razorpayOrderId("order_demo_xyz")
            .total(BigDecimal.TEN);
        when(customerOrderRepository.findById(57L)).thenReturn(Optional.of(order));

        mockMvc
            .perform(
                post("/internal/v1/orders/sync-payment")
                    .with(user("customer1"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"orderId\":57}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.paid").value(false));
    }

    @Test
    void validateCoupon_validPercentCoupon_returnsDiscount() throws Exception {
        Coupon coupon = new Coupon()
            .code("SAVE10")
            .active(true)
            .discountType(DiscountType.PERCENT)
            .discountValue(BigDecimal.TEN)
            .minOrder(BigDecimal.ZERO);
        when(couponRepository.findOneByCodeIgnoreCase("SAVE10")).thenReturn(Optional.of(coupon));

        mockMvc
            .perform(
                post("/internal/v1/coupons/validate")
                    .with(user("customer1"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":\"SAVE10\",\"subtotal\":1000}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ok").value(true))
            .andExpect(jsonPath("$.data.discount").value(100.0));
    }

    @Test
    void validateCoupon_bogoCoupon_returnsCheapestItemDiscount() throws Exception {
        Coupon coupon = new Coupon()
            .code("BOGO1")
            .active(true)
            .discountType(DiscountType.BOGO)
            .discountValue(BigDecimal.valueOf(100))
            .minOrder(BigDecimal.ZERO)
            .bogoBuyQty(1)
            .bogoGetQty(1);
        when(couponRepository.findOneByCodeIgnoreCase("BOGO1")).thenReturn(Optional.of(coupon));

        mockMvc
            .perform(
                post("/internal/v1/coupons/validate")
                    .with(user("customer1"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"code\":\"BOGO1\",\"subtotal\":1500,\"items\":[{\"qty\":1,\"price\":1000},{\"qty\":1,\"price\":500}]}"
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ok").value(true))
            .andExpect(jsonPath("$.data.discount").value(500.0));
    }

    @Test
    void activeCoupons_hidesWelcome10ForReturningCustomer() throws Exception {
        when(customerOrderRepository.existsByUserLoginAndStatusNot("customer1", OrderStatus.CANCELLED)).thenReturn(true);
        Coupon welcome = new Coupon()
            .code("WELCOME10")
            .active(true)
            .discountType(DiscountType.PERCENT)
            .discountValue(BigDecimal.TEN)
            .minOrder(BigDecimal.ZERO);
        Coupon save5 = new Coupon()
            .code("SAVE5")
            .active(true)
            .discountType(DiscountType.PERCENT)
            .discountValue(BigDecimal.valueOf(5))
            .minOrder(BigDecimal.ZERO);
        when(couponRepository.findAllByOrderByIdDesc()).thenReturn(List.of(welcome, save5));

        mockMvc
            .perform(get("/internal/v1/coupons/active").with(user("customer1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.coupons.length()").value(1))
            .andExpect(jsonPath("$.data.coupons[0].code").value("SAVE5"));
    }

    @Test
    void orderDetail_refreshesStaleLedgerlyTracking() throws Exception {
        CustomerOrder order = new CustomerOrder()
            .id(60L)
            .userLogin("customer1")
            .orderNumber("MNM-STALE")
            .status(OrderStatus.CONFIRMED)
            .paymentStatus(PaymentStatus.PAID)
            .paymentMethod("online")
            .ledgerlySalesOrderId("SO-STALE")
            .ledgerlyTrackingLastPulledAt(Instant.now().minusSeconds(7200))
            .total(BigDecimal.TEN)
            .placedAt(Instant.now());
        CustomerOrder refreshed = new CustomerOrder()
            .id(60L)
            .userLogin("customer1")
            .orderNumber("MNM-STALE")
            .status(OrderStatus.CONFIRMED)
            .paymentStatus(PaymentStatus.PAID)
            .paymentMethod("online")
            .ledgerlySalesOrderId("SO-STALE")
            .trackingNumber("AWB-NEW")
            .total(BigDecimal.TEN)
            .placedAt(Instant.now());
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin("MNM-STALE", "customer1")).thenReturn(Optional.of(order));
        when(customerOrderRepository.findById(60L)).thenReturn(Optional.of(refreshed));
        when(orderItemRepository.findAllByOrderId(60L)).thenReturn(List.of());

        mockMvc
            .perform(get("/internal/v1/orders/MNM-STALE").with(user("customer1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.order.tracking_number").value("AWB-NEW"));

        verify(ledgerlySyncService).pullOrderTracking(60L);
    }

    @Test
    void createOrder_expressShipping_chargesExpressRate() throws Exception {
        ReflectionTestUtils.setField(resource, "razorpayKeyId", "DEMO");
        ReflectionTestUtils.setField(resource, "razorpayKeySecret", "");
        Product product = new Product().id(1L).name("Dress").slug("dress").price(BigDecimal.valueOf(1000)).stock(5).active(true);
        SiteSetting settings = new SiteSetting()
            .shippingStandardInr(99)
            .shippingExpressInr(199)
            .freeShippingMinInr(5000)
            .pricesIncludeGst(true);
        when(productRepository.findOneForInventoryUpdate(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(settings));
        CustomerOrder saved = new CustomerOrder().id(410L).orderNumber("MNM-EXP").total(BigDecimal.valueOf(1199));
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
                          "donation":0,
                          "shipping_mode":"exp"
                        }
                        """
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totals.shipping").value(199.0));
    }

    @Test
    void createOrder_freeShippingAboveThreshold() throws Exception {
        ReflectionTestUtils.setField(resource, "razorpayKeyId", "DEMO");
        ReflectionTestUtils.setField(resource, "razorpayKeySecret", "");
        Product product = new Product().id(1L).name("Dress").slug("dress").price(BigDecimal.valueOf(3000)).stock(5).active(true);
        SiteSetting settings = new SiteSetting()
            .shippingStandardInr(99)
            .shippingExpressInr(199)
            .freeShippingMinInr(2500)
            .pricesIncludeGst(true);
        when(productRepository.findOneForInventoryUpdate(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(settings));
        CustomerOrder saved = new CustomerOrder().id(411L).orderNumber("MNM-FREE").total(BigDecimal.valueOf(3000));
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
                          "items":[{"product_id":"1","product_name":"Dress","qty":1,"price":3000}],
                          "shipping_address":{"full_name":"Jane","email":"j@example.com","phone":"9876543210","line1":"Line 1","city":"City","state":"State","pincode":"560001"},
                          "donation":0
                        }
                        """
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totals.shipping").value(0.0));
    }

    @Test
    void createOrder_variantProduct_reservesVariantStock() throws Exception {
        ReflectionTestUtils.setField(resource, "razorpayKeyId", "DEMO");
        ReflectionTestUtils.setField(resource, "razorpayKeySecret", "");
        String variantsJson =
            "[{\"sku\":\"TEE-S-RED\",\"size\":\"S\",\"color\":\"Red\",\"rate\":500,\"stock\":3,\"active\":true}]";
        Product product = new Product()
            .id(2L)
            .name("Tee")
            .slug("tee")
            .price(BigDecimal.valueOf(500))
            .stock(3)
            .active(true)
            .itemStructure("VARIANTS")
            .variantsJson(variantsJson);
        when(productRepository.findOneForInventoryUpdate(2L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(siteSettingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        CustomerOrder saved = new CustomerOrder().id(412L).orderNumber("MNM-VAR").total(BigDecimal.valueOf(599));
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
                          "items":[{"product_id":"2","product_name":"Tee","qty":1,"price":500,"size":"S","color":"Red","sku":"TEE-S-RED"}],
                          "shipping_address":{"full_name":"Jane","email":"j@example.com","phone":"9876543210","line1":"Line 1","city":"City","state":"State","pincode":"560001"},
                          "donation":0
                        }
                        """
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orderId").value(412));
    }

    @Test
    void retryPayment_nonPendingStatus_returnsConflict() throws Exception {
        CustomerOrder order = new CustomerOrder()
            .id(79L)
            .userLogin("customer1")
            .paymentStatus(PaymentStatus.FAILED)
            .razorpayOrderId("order_rzp_79");
        when(customerOrderRepository.findById(79L)).thenReturn(Optional.of(order));

        mockMvc
            .perform(
                post("/internal/v1/orders/retry-payment")
                    .with(user("customer1"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"orderId\":79}")
            )
            .andExpect(status().isConflict());
    }

    @Test
    void cancelOrder_codBeforeDispatch_createsCancellationRequest() throws Exception {
        CustomerOrder order = new CustomerOrder()
            .id(80L)
            .userLogin("customer1")
            .orderNumber("MNM-COD-CANCEL")
            .status(OrderStatus.CONFIRMED)
            .paymentStatus(PaymentStatus.PENDING)
            .paymentMethod("cod")
            .inventoryReserved(true)
            .total(BigDecimal.valueOf(549));
        OrderCancellationRequest request = new OrderCancellationRequest();
        request.setRequestNumber("CAN-COD-001");
        request.setStatus(CancellationRequestStatus.REQUESTED);
        request.setReason("Ordered by mistake");
        request.setOrder(order);
        request.setUserLogin("customer1");
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin("MNM-COD-CANCEL", "customer1")).thenReturn(Optional.of(order));
        when(orderCancellationService.submitRequest("customer1", "MNM-COD-CANCEL", "Ordered by mistake")).thenReturn(request);
        when(orderCancellationService.getOpenForUser("customer1", "MNM-COD-CANCEL")).thenReturn(request);
        when(orderCancellationService.hasPendingRequest(80L)).thenReturn(true);
        when(orderCancellationService.toDto(request)).thenReturn(
            Map.of("request_number", "CAN-COD-001", "status", "requested", "reason", "Ordered by mistake")
        );

        mockMvc
            .perform(
                post("/internal/v1/orders/MNM-COD-CANCEL/cancel")
                    .with(user("customer1"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"Ordered by mistake\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.order.status").value("confirmed"))
            .andExpect(jsonPath("$.data.cancellation_request.status").value("requested"));

        verify(razorpayRefundService, never()).refundPayment(any(), any());
        verify(zohoSyncService, never()).cancelOrder(anyLong());
    }

    @Test
    void cancelOrder_paidBeforeDispatch_createsCancellationRequest() throws Exception {
        CustomerOrder order = new CustomerOrder()
            .id(81L)
            .userLogin("customer1")
            .orderNumber("MNM-PAID-CANCEL")
            .status(OrderStatus.CONFIRMED)
            .paymentStatus(PaymentStatus.PAID)
            .paymentMethod("online")
            .razorpayPaymentId("pay_81")
            .total(BigDecimal.valueOf(999));
        OrderCancellationRequest request = new OrderCancellationRequest();
        request.setRequestNumber("CAN-PAID-001");
        request.setStatus(CancellationRequestStatus.REQUESTED);
        request.setReason("Found a better price elsewhere");
        request.setOrder(order);
        request.setUserLogin("customer1");
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin("MNM-PAID-CANCEL", "customer1")).thenReturn(Optional.of(order));
        when(orderCancellationService.submitRequest("customer1", "MNM-PAID-CANCEL", "Found a better price elsewhere")).thenReturn(request);
        when(orderCancellationService.getOpenForUser("customer1", "MNM-PAID-CANCEL")).thenReturn(request);
        when(orderCancellationService.hasPendingRequest(81L)).thenReturn(true);
        when(orderCancellationService.toDto(request)).thenReturn(
            Map.of("request_number", "CAN-PAID-001", "status", "requested", "reason", "Found a better price elsewhere")
        );

        mockMvc
            .perform(
                post("/internal/v1/orders/MNM-PAID-CANCEL/cancel")
                    .with(user("customer1"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"Found a better price elsewhere\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.order.status").value("confirmed"))
            .andExpect(jsonPath("$.data.cancellation_request.status").value("requested"));

        verify(razorpayRefundService, never()).refundPayment(any(), any());
    }

    @Test
    void cancelOrder_whenDuplicateRequest_propagatesConflict() throws Exception {
        CustomerOrder order = new CustomerOrder()
            .id(83L)
            .userLogin("customer1")
            .orderNumber("MNM-REFUND-FAIL")
            .status(OrderStatus.CONFIRMED)
            .paymentStatus(PaymentStatus.PAID)
            .paymentMethod("online")
            .razorpayPaymentId("pay_83")
            .total(BigDecimal.valueOf(750));
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin("MNM-REFUND-FAIL", "customer1")).thenReturn(Optional.of(order));
        when(orderCancellationService.submitRequest(eq("customer1"), eq("MNM-REFUND-FAIL"), any())).thenThrow(
            new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "A cancellation request is already pending for this order")
        );

        mockMvc
            .perform(
                post("/internal/v1/orders/MNM-REFUND-FAIL/cancel")
                    .with(user("customer1"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"Changed my mind\"}")
            )
            .andExpect(status().isConflict());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        verify(orderEventService, never()).syncLifecycleEvents(order);
    }

    @Test
    void cancelOrder_afterDispatch_returnsConflictWithoutRefund() throws Exception {
        CustomerOrder order = new CustomerOrder()
            .id(82L)
            .userLogin("customer1")
            .orderNumber("MNM-SHIPPED")
            .status(OrderStatus.CONFIRMED)
            .paymentStatus(PaymentStatus.PAID)
            .ledgerlyOrderStatus("shipped")
            .total(BigDecimal.valueOf(500));
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin("MNM-SHIPPED", "customer1")).thenReturn(Optional.of(order));

        mockMvc
            .perform(
                post("/internal/v1/orders/MNM-SHIPPED/cancel")
                    .with(user("customer1"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"No longer needed\"}")
            )
            .andExpect(status().isConflict());

        verify(razorpayRefundService, never()).refundPayment(any(), any());
    }

    @Test
    void orderDetail_notFound_returns404() throws Exception {
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin("MNM-MISSING", "customer1")).thenReturn(Optional.empty());

        mockMvc
            .perform(get("/internal/v1/orders/MNM-MISSING").with(user("customer1")))
            .andExpect(status().isNotFound());
    }

    @Test
    void orderDetail_wrongOwner_returns404() throws Exception {
        CustomerOrder order = new CustomerOrder().id(99L).userLogin("other").orderNumber("MNM-OTHER");
        when(customerOrderRepository.findOneByOrderNumberAndUserLogin("MNM-OTHER", "customer1")).thenReturn(Optional.empty());

        mockMvc
            .perform(get("/internal/v1/orders/MNM-OTHER").with(user("customer1")))
            .andExpect(status().isNotFound());
    }

    @Test
    void validateCoupon_invalidCoupon_returnsError() throws Exception {
        when(couponRepository.findOneByCodeIgnoreCase("NOPE")).thenReturn(Optional.empty());

        mockMvc
            .perform(
                post("/internal/v1/coupons/validate")
                    .with(user("customer1"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":\"NOPE\",\"subtotal\":100}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ok").value(false));
    }

    @Test
    void createOrder_demoMode_usesDemoRazorpayOrderId() throws Exception {
        ReflectionTestUtils.setField(resource, "razorpayPaymentDemo", true);
        Product product = new Product().id(1L).name("Dress").slug("dress").price(BigDecimal.TEN).stock(5).active(true);
        when(productRepository.findOneForInventoryUpdate(1L)).thenReturn(Optional.of(product));
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> {
            CustomerOrder order = inv.getArgument(0);
            if (order.getId() == null) {
                order.setId(101L);
            }
            return order;
        });

        mockMvc
            .perform(
                post("/internal/v1/orders")
                    .with(user("customer1"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "items":[{"product_id":"1","product_name":"Dress","qty":1,"price":10}],
                          "shipping_address":{"full_name":"Jane","email":"j@example.com","phone":"9876543210","line1":"Line 1","city":"City","state":"State","pincode":"560001"},
                          "donation":0
                        }
                        """
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.demoMode").value(true))
            .andExpect(jsonPath("$.data.razorpayOrderId").value(org.hamcrest.Matchers.startsWith("order_demo_")));
    }

    @TestConfiguration
    static class DataAccessConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        com.miniandmore.app.service.StorePricingService storePricingService() {
            return com.miniandmore.app.service.StorePricingTestSupport.defaultPricing();
        }

        @Bean
        com.miniandmore.app.service.ProductVariantSupport productVariantSupport(ObjectMapper objectMapper) {
            return com.miniandmore.app.service.StorePricingTestSupport.variantSupport(objectMapper);
        }

        @Bean
        org.springframework.transaction.PlatformTransactionManager transactionManager() {
            return org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class);
        }

        @Bean
        AccountOrderDataAccess accountOrderDataAccess(
            CouponRepository couponRepository,
            CustomerProfileRepository customerProfileRepository,
            CustomerOrderRepository customerOrderRepository,
            OrderItemRepository orderItemRepository,
            ProductRepository productRepository,
            SiteSettingRepository siteSettingRepository,
            LedgerlyIntegrationClient ledgerlyIntegrationClient
        ) {
            return new AccountOrderDataAccess(
                couponRepository,
                customerProfileRepository,
                customerOrderRepository,
                orderItemRepository,
                productRepository,
                siteSettingRepository,
                ledgerlyIntegrationClient
            );
        }
    }
}
