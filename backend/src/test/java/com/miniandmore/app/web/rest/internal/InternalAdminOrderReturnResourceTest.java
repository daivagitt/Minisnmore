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
import com.miniandmore.app.domain.enumeration.ReturnDisposition;
import com.miniandmore.app.domain.enumeration.ReturnLogisticsMode;
import com.miniandmore.app.service.CaseSlaSupport;
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

    @MockitoBean
    private CaseSlaSupport caseSlaSupport;

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
        when(orderReturnService.rejectAtCurrentStage(eq("RET-1"), eq("Not eligible for return"), any())).thenReturn(rejected);

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
    void pickupRequested_returns200() throws Exception {
        OrderReturn updated = sampleReturn();
        updated.setStatus(OrderReturnStatus.PICKUP_REQUESTED);
        when(orderReturnService.markPickupRequested("RET-1", null)).thenReturn(updated);

        mockMvc
            .perform(post("/internal/v1/admin/order-returns/RET-1/pickup-requested"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("pickup_requested"));
    }

    @Test
    void pickedUp_returns200() throws Exception {
        OrderReturn updated = sampleReturn();
        updated.setStatus(OrderReturnStatus.PICKED_UP);
        when(orderReturnService.markPickedUp("RET-1", null)).thenReturn(updated);

        mockMvc
            .perform(post("/internal/v1/admin/order-returns/RET-1/picked-up"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("picked_up"));
    }

    @Test
    void inspect_returns200() throws Exception {
        OrderReturn updated = sampleReturn();
        updated.setStatus(OrderReturnStatus.INSPECTED);
        when(orderReturnService.markInspected("RET-1", null)).thenReturn(updated);

        mockMvc
            .perform(post("/internal/v1/admin/order-returns/RET-1/inspect"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("inspected"));
    }

    @Test
    void accept_returns200() throws Exception {
        OrderReturn updated = sampleReturn();
        updated.setStatus(OrderReturnStatus.ACCEPTED);
        when(orderReturnService.acceptAfterInspection("RET-1", ReturnDisposition.RESTOCKABLE, null)).thenReturn(updated);

        mockMvc
            .perform(
                post("/internal/v1/admin/order-returns/RET-1/accept")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"disposition\":\"restockable\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("accepted"));
    }

    @Test
    void allocateReplacement_returns200() throws Exception {
        OrderReturn updated = sampleReturn();
        updated.setStatus(OrderReturnStatus.ITEM_ALLOCATED);
        when(orderReturnService.allocateReplacementItem("RET-1", null)).thenReturn(updated);

        mockMvc
            .perform(post("/internal/v1/admin/order-returns/RET-1/allocate-replacement"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("item_allocated"));
    }

    @Test
    void convertToRefund_returns200() throws Exception {
        OrderReturn updated = sampleReturn();
        updated.setStatus(OrderReturnStatus.REFUNDED);
        when(orderReturnService.convertExchangeToRefund("RET-1", null)).thenReturn(updated);

        mockMvc
            .perform(post("/internal/v1/admin/order-returns/RET-1/convert-to-refund"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("refunded"));
    }

    @Test
    void repacking_returns200() throws Exception {
        OrderReturn updated = sampleReturn();
        updated.setStatus(OrderReturnStatus.REPACKING);
        when(orderReturnService.markRepacking("RET-1", null)).thenReturn(updated);

        mockMvc
            .perform(post("/internal/v1/admin/order-returns/RET-1/repacking"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("repacking"));
    }

    @Test
    void readyToShip_returns200() throws Exception {
        OrderReturn updated = sampleReturn();
        updated.setStatus(OrderReturnStatus.READY_TO_SHIP);
        when(orderReturnService.markReadyToShip("RET-1", null)).thenReturn(updated);

        mockMvc
            .perform(post("/internal/v1/admin/order-returns/RET-1/ready-to-ship"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ready_to_ship"));
    }

    @Test
    void bookOutbound_returns200() throws Exception {
        OrderReturn updated = sampleReturn();
        updated.setStatus(OrderReturnStatus.SHIPPED);
        updated.setOutboundAwb("OUT123");
        when(
            orderReturnService.bookOutbound("RET-1", ReturnLogisticsMode.XPRESSBEES, null, null, null, null, null)
        ).thenReturn(updated);

        mockMvc
            .perform(
                post("/internal/v1/admin/order-returns/RET-1/book-outbound")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"mode\":\"xpressbees\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("shipped"))
            .andExpect(jsonPath("$.data.outbound_awb").value("OUT123"));
    }

    @Test
    void shipped_returns200() throws Exception {
        OrderReturn updated = sampleReturn();
        updated.setStatus(OrderReturnStatus.SHIPPED);
        when(orderReturnService.markShipped("RET-1", null)).thenReturn(updated);

        mockMvc
            .perform(post("/internal/v1/admin/order-returns/RET-1/shipped"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("shipped"));
    }

    @Test
    void delivered_returns200() throws Exception {
        OrderReturn updated = sampleReturn();
        updated.setStatus(OrderReturnStatus.DELIVERED);
        when(orderReturnService.markDelivered("RET-1", null)).thenReturn(updated);

        mockMvc
            .perform(post("/internal/v1/admin/order-returns/RET-1/delivered"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("delivered"));
    }

    @Test
    void complete_returns200() throws Exception {
        OrderReturn updated = sampleReturn();
        updated.setStatus(OrderReturnStatus.COMPLETED);
        when(orderReturnService.completeExchange("RET-1", null)).thenReturn(updated);

        mockMvc
            .perform(post("/internal/v1/admin/order-returns/RET-1/complete"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("completed"));
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
