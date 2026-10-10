package com.miniandmore.app.web.rest.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miniandmore.app.domain.OrderCancellationRequest;
import com.miniandmore.app.domain.enumeration.CancellationRequestStatus;
import com.miniandmore.app.domain.enumeration.ManualRefundMethod;
import com.miniandmore.app.service.CaseSlaSupport;
import com.miniandmore.app.service.OrderCancellationService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InternalAdminCancellationRequestResource.class)
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(authorities = "ROLE_ADMIN")
class InternalAdminCancellationRequestResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderCancellationService orderCancellationService;

    @MockitoBean
    private CaseSlaSupport caseSlaSupport;

    @Test
    void list_returnsEnrichedRows() throws Exception {
        OrderCancellationRequest request = sampleRequest();
        when(orderCancellationService.listForAdmin(null)).thenReturn(List.of(request));
        when(orderCancellationService.toDto(request)).thenReturn(Map.of("request_number", "CAN-1", "status", "requested"));

        mockMvc
            .perform(get("/internal/v1/admin/cancellation-requests"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].request_number").value("CAN-1"));

        verify(caseSlaSupport).enrichCancellationCase(any());
    }

    @Test
    void list_invalidStatus_returnsBadRequest() throws Exception {
        mockMvc
            .perform(get("/internal/v1/admin/cancellation-requests").param("status", "bogus"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void list_withStatusFilter_returns200() throws Exception {
        OrderCancellationRequest request = sampleRequest();
        when(orderCancellationService.listForAdmin(CancellationRequestStatus.REQUESTED)).thenReturn(List.of(request));
        when(orderCancellationService.toDto(request)).thenReturn(Map.of("request_number", "CAN-1"));

        mockMvc
            .perform(get("/internal/v1/admin/cancellation-requests").param("status", "requested"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].request_number").value("CAN-1"));
    }

    @Test
    void approve_returnsDto() throws Exception {
        OrderCancellationRequest approved = sampleRequest();
        approved.setStatus(CancellationRequestStatus.APPROVED);
        when(orderCancellationService.approve("CAN-1", "Approved by admin")).thenReturn(approved);
        when(orderCancellationService.toDto(approved)).thenReturn(Map.of("status", "approved"));

        mockMvc
            .perform(
                post("/internal/v1/admin/cancellation-requests/CAN-1/approve")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"admin_notes\":\"Approved by admin\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("approved"));
    }

    @Test
    void reject_returnsDto() throws Exception {
        OrderCancellationRequest rejected = sampleRequest();
        rejected.setStatus(CancellationRequestStatus.REJECTED);
        when(orderCancellationService.reject("CAN-1", "Already shipped", "Cannot cancel")).thenReturn(rejected);
        when(orderCancellationService.toDto(rejected)).thenReturn(Map.of("status", "rejected"));

        mockMvc
            .perform(
                post("/internal/v1/admin/cancellation-requests/CAN-1/reject")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"rejection_reason":"Already shipped","admin_notes":"Cannot cancel"}
                        """
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("rejected"));
    }

    @Test
    void recordCodRefund_returnsDto() throws Exception {
        OrderCancellationRequest recorded = sampleRequest();
        when(
            orderCancellationService.recordManualCodRefund(
                eq("CAN-1"),
                eq(ManualRefundMethod.UPI),
                eq("UPI-REF-1234"),
                eq("Recorded"),
                any()
            )
        ).thenReturn(recorded);
        when(orderCancellationService.toDto(recorded)).thenReturn(Map.of("manual_refund_method", "upi"));

        mockMvc
            .perform(
                post("/internal/v1/admin/cancellation-requests/CAN-1/record-cod-refund")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"refund_method":"upi","refund_reference":"UPI-REF-1234","admin_notes":"Recorded"}
                        """
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.manual_refund_method").value("upi"));
    }

    @Test
    void recordCodRefund_invalidMethod_returnsBadRequest() throws Exception {
        mockMvc
            .perform(
                post("/internal/v1/admin/cancellation-requests/CAN-1/record-cod-refund")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"refund_method\":\"wire\",\"refund_reference\":\"REF-1234\"}")
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    void retryRefund_returnsDto() throws Exception {
        OrderCancellationRequest retried = sampleRequest();
        when(orderCancellationService.retryOnlineRefund("CAN-1")).thenReturn(retried);
        when(orderCancellationService.toDto(retried)).thenReturn(Map.of("status", "requested"));

        mockMvc
            .perform(post("/internal/v1/admin/cancellation-requests/CAN-1/retry-refund"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("requested"));
    }

    private static OrderCancellationRequest sampleRequest() {
        OrderCancellationRequest request = new OrderCancellationRequest();
        request.setRequestNumber("CAN-1");
        request.setStatus(CancellationRequestStatus.REQUESTED);
        request.setReason("Ordered by mistake");
        return request;
    }
}
