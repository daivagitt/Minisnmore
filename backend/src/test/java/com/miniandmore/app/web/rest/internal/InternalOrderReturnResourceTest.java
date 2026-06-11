package com.miniandmore.app.web.rest.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
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

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("customer1", "n/a"));
    }

    @Test
    void listMine_returns200() throws Exception {
        OrderReturn orderReturn = sampleReturn();
        when(orderReturnService.listForUser("customer1")).thenReturn(List.of(orderReturn));

        mockMvc
            .perform(get("/internal/v1/order-returns").with(user("customer1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].return_number").value("RET-1"));
    }

    @Test
    void requestReturn_returns200() throws Exception {
        when(orderReturnService.requestReturn(any(), any(), any())).thenReturn(sampleReturn());

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
