package com.miniandmore.app.web.rest.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.enumeration.OrderReturnStatus;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.service.OrderReturnService;
import com.miniandmore.app.service.S3StorageService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InternalOrderReturnResource.class)
@AutoConfigureMockMvc(addFilters = false)
class InternalOrderReturnResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderReturnService orderReturnService;

    @MockitoBean
    private S3StorageService s3StorageService;

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("customer1", "n/a"));
        when(orderReturnService.returnLineBreakdown(any())).thenReturn(List.of());
    }

    @Test
    void listMine_returns200() throws Exception {
        OrderReturn orderReturn = sampleReturn();
        orderReturn.setCarrierName("XpressBees");
        orderReturn.setReverseAwb("REV-123");
        orderReturn.setTrackingUrl("https://track.example/REV-123");
        orderReturn.setPickupWindow("Tomorrow 10am–2pm");
        orderReturn.setOutboundCarrierName("XpressBees");
        orderReturn.setOutboundAwb("OUT-456");
        orderReturn.setOutboundTrackingUrl("https://track.example/OUT-456");
        when(orderReturnService.listForUser("customer1")).thenReturn(List.of(orderReturn));

        mockMvc
            .perform(get("/internal/v1/order-returns").with(user("customer1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].return_number").value("RET-1"))
            .andExpect(jsonPath("$.data[0].reverse_awb").value("REV-123"))
            .andExpect(jsonPath("$.data[0].tracking_url").value("https://track.example/REV-123"))
            .andExpect(jsonPath("$.data[0].carrier_name").value("XpressBees"))
            .andExpect(jsonPath("$.data[0].pickup_window").value("Tomorrow 10am–2pm"))
            .andExpect(jsonPath("$.data[0].outbound_awb").value("OUT-456"))
            .andExpect(jsonPath("$.data[0].outbound_tracking_url").value("https://track.example/OUT-456"))
            .andExpect(jsonPath("$.data[0].outbound_carrier_name").value("XpressBees"))
            .andExpect(jsonPath("$.data[0].outbound_label_url").doesNotExist());
    }

    @Test
    void requestReturn_returns200() throws Exception {
        when(orderReturnService.requestReturn(any(), any(), any(), any(), any())).thenReturn(sampleReturn());

        mockMvc
            .perform(
                post("/internal/v1/order-returns")
                    .with(user("customer1"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"order_number\":\"ORD-1\",\"reason\":\"Product arrived damaged in transit\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("requested"));
    }

    @Test
    void checkEligibility_returns200() throws Exception {
        when(orderReturnService.eligibilityForUser("customer1", "ORD-1")).thenReturn(Map.of("eligible", true, "order_number", "ORD-1"));

        mockMvc
            .perform(get("/internal/v1/order-returns/eligibility/ORD-1").with(user("customer1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.eligible").value(true));
    }

    @Test
    void cancel_returns200() throws Exception {
        OrderReturn cancelled = sampleReturn();
        cancelled.setStatus(OrderReturnStatus.CANCELLED);
        when(orderReturnService.cancelByCustomer("customer1", "RET-1")).thenReturn(cancelled);

        mockMvc
            .perform(post("/internal/v1/order-returns/RET-1/cancel").with(user("customer1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("cancelled"));
    }

    @Test
    void requestExchange_returns200() throws Exception {
        OrderReturn exchange = sampleReturn();
        exchange.setRequestType(com.miniandmore.app.domain.enumeration.OrderRequestType.EXCHANGE);
        when(orderReturnService.requestExchange(any(), any(), any(), any(), any(), any(), any())).thenReturn(exchange);

        mockMvc
            .perform(
                post("/internal/v1/order-returns/exchange")
                    .with(user("customer1"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"order_number\":\"ORD-1\",\"order_item_id\":5,\"reason\":\"Wrong size ordered by mistake\","
                            + "\"replacement_size\":\"M\",\"replacement_color\":\"Blue\"}"
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.request_type").value("exchange"));
    }

    @Test
    void checkExchangeAvailability_returns200() throws Exception {
        when(orderReturnService.isReplacementSellable("customer1", 5L, "M", "Blue")).thenReturn(true);

        mockMvc
            .perform(
                get("/internal/v1/order-returns/exchange-availability/5")
                    .with(user("customer1"))
                    .param("size", "M")
                    .param("color", "Blue")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sellable").value(true));
    }

    @Test
    void config_returns200() throws Exception {
        when(orderReturnService.publicConfig()).thenReturn(Map.of("enabled", true, "window_days", 3));

        mockMvc
            .perform(get("/internal/v1/order-returns/config").with(user("customer1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.window_days").value(3));
    }

    @Test
    void requestReturn_withImageUrls_returnsThem() throws Exception {
        OrderReturn orderReturn = sampleReturn();
        orderReturn.setImageUrlsJson("[\"https://cdn.example/returns/damage.jpg\"]");
        when(orderReturnService.requestReturn(any(), any(), any(), any(), any())).thenReturn(orderReturn);
        when(orderReturnService.imageUrls(orderReturn)).thenReturn(List.of("https://cdn.example/returns/damage.jpg"));

        mockMvc
            .perform(
                post("/internal/v1/order-returns")
                    .with(user("customer1"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"order_number\":\"ORD-1\",\"reason\":\"Product arrived damaged in transit\","
                            + "\"image_urls\":[\"https://cdn.example/returns/damage.jpg\"]}"
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.image_urls[0]").value("https://cdn.example/returns/damage.jpg"));
    }

    @Test
    void uploadImage_returnsPublicUrl() throws Exception {
        when(s3StorageService.uploadObject(eq("returns"), eq("damage.jpg"), any(byte[].class), eq("image/jpeg")))
            .thenReturn("https://cdn.example/returns/damage.jpg");
        MockMultipartFile file = new MockMultipartFile("file", "damage.jpg", "image/jpeg", "image-bytes".getBytes());

        mockMvc
            .perform(multipart("/internal/v1/order-returns/uploads").file(file).with(user("customer1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.publicUrl").value("https://cdn.example/returns/damage.jpg"));
    }

    private static OrderReturn sampleReturn() {
        CustomerOrder order = new CustomerOrder()
            .orderNumber("ORD-1")
            .total(BigDecimal.TEN)
            .status(OrderStatus.FULFILLED)
            .paymentStatus(PaymentStatus.PAID);
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber("RET-1");
        orderReturn.setStatus(OrderReturnStatus.REQUESTED);
        orderReturn.setReason("Product arrived damaged in transit");
        orderReturn.setRequestedAt(Instant.now());
        orderReturn.setOrder(order);
        return orderReturn;
    }
}
