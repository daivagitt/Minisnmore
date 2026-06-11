package com.miniandmore.app.web.rest.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.enumeration.OrderReturnStatus;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.service.OrderReturnService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InternalAdminOrderReturnResource.class)
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(authorities = "ROLE_ADMIN")
class InternalAdminOrderReturnResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderReturnService orderReturnService;

    @Test
    void list_returns200() throws Exception {
        when(orderReturnService.listForAdmin(null)).thenReturn(List.of(sampleReturn()));

        mockMvc
            .perform(get("/internal/v1/admin/order-returns"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].return_number").value("RET-1"));
    }

    @Test
    void approve_returns200() throws Exception {
        OrderReturn approved = sampleReturn();
        approved.setStatus(OrderReturnStatus.APPROVED);
        when(orderReturnService.approve("RET-1", "Approved")).thenReturn(approved);

        mockMvc
            .perform(
                post("/internal/v1/admin/order-returns/RET-1/approve")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"admin_notes\":\"Approved\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("approved"));
    }

    @Test
    void reject_returns200() throws Exception {
        OrderReturn rejected = sampleReturn();
        rejected.setStatus(OrderReturnStatus.REJECTED);
        when(orderReturnService.reject(eq("RET-1"), eq("Not eligible for return"), any())).thenReturn(rejected);

        mockMvc
            .perform(
                post("/internal/v1/admin/order-returns/RET-1/reject")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"rejection_reason\":\"Not eligible for return\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("rejected"));
    }

    @Test
    void markReceived_returns200() throws Exception {
        OrderReturn received = sampleReturn();
        received.setStatus(OrderReturnStatus.RECEIVED);
        when(orderReturnService.markReceived("RET-1", null)).thenReturn(received);

        mockMvc
            .perform(post("/internal/v1/admin/order-returns/RET-1/mark-received"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("received"));
    }

    @Test
    void refund_returns200() throws Exception {
        OrderReturn refunded = sampleReturn();
        refunded.setStatus(OrderReturnStatus.REFUNDED);
        when(orderReturnService.processRefund("RET-1", null)).thenReturn(refunded);

        mockMvc
            .perform(post("/internal/v1/admin/order-returns/RET-1/refund"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("refunded"));
    }

    private static OrderReturn sampleReturn() {
        CustomerOrder order = new CustomerOrder()
            .id(10L)
            .orderNumber("ORD-1")
            .total(BigDecimal.valueOf(999))
            .status(OrderStatus.FULFILLED)
            .paymentStatus(PaymentStatus.PAID);
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setId(1L);
        orderReturn.setReturnNumber("RET-1");
        orderReturn.setStatus(OrderReturnStatus.REQUESTED);
        orderReturn.setReason("Product arrived damaged in transit");
        orderReturn.setRequestedAt(Instant.now());
        orderReturn.setOrder(order);
        orderReturn.setUserLogin("customer1");
        return orderReturn;
    }
}
