package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.enumeration.OrderRequestType;
import com.miniandmore.app.domain.enumeration.OrderReturnStatus;
import com.miniandmore.app.repository.OrderReturnRepository;
import com.miniandmore.app.repository.XpressBeesReturnWebhookEventRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Dense reverse-leg carrier status matrix for {@link XpressBeesReturnWebhookHandlerService}. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class XpressBeesReturnWebhookHandlerServiceReverseMatrixTest {

    private final ApplicationProperties applicationProperties = org.mockito.Mockito.mock(ApplicationProperties.class);
    private final XpressBeesReturnWebhookEventRepository webhookEventRepository =
        org.mockito.Mockito.mock(XpressBeesReturnWebhookEventRepository.class);
    private final OrderReturnRepository orderReturnRepository = org.mockito.Mockito.mock(OrderReturnRepository.class);
    private final OrderReturnService orderReturnService = org.mockito.Mockito.mock(OrderReturnService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private XpressBeesReturnWebhookHandlerService handlerService;

    @BeforeEach
    void setUp() {
        ApplicationProperties.XpressBees xpressBees = new ApplicationProperties.XpressBees();
        xpressBees.setWebhookSecret("secret-123");
        when(applicationProperties.getXpressbees()).thenReturn(xpressBees);
        when(webhookEventRepository.findOneByEventId(any())).thenReturn(Optional.empty());
        when(webhookEventRepository.deletePendingByEventId(any())).thenReturn(0);
        when(webhookEventRepository.existsByEventId(any())).thenReturn(false);
        when(webhookEventRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(webhookEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderReturnRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        handlerService =
            new XpressBeesReturnWebhookHandlerService(
                applicationProperties,
                webhookEventRepository,
                orderReturnRepository,
                orderReturnService,
                objectMapper
            );
    }

    @Test
    void reverse_outForPickup_fromApproved_schedulesPickup() {
        OrderReturn orderReturn = baseReturn("RET-A", OrderReturnStatus.APPROVED);
        when(orderReturnRepository.findOneByReturnNumber("RET-A")).thenReturn(Optional.of(orderReturn));

        Map<String, Object> result = handlerService.handle(
            "{\"event_id\":\"evt-ofp\",\"return_number\":\"RET-A\",\"awb\":\"REV-1\",\"status\":\"out_for_pickup\"}",
            null,
            "secret-123"
        );

        assertThat(result.get("ok")).isEqualTo(true);
        assertThat(result.get("local_status")).isEqualTo("pickup_scheduled");
        verify(orderReturnService).ensurePickupScheduled("RET-A", "ExpressBees: out_for_pickup");
    }

    @Test
    void reverse_outForPickup_fromPickupFailed_reschedules() {
        OrderReturn orderReturn = baseReturn("RET-B", OrderReturnStatus.PICKUP_FAILED);
        when(orderReturnRepository.findOneByReturnNumber("RET-B")).thenReturn(Optional.of(orderReturn));

        handlerService.handle(
            "{\"event_id\":\"evt-ofp2\",\"return_number\":\"RET-B\",\"awb\":\"REV-2\",\"status\":\"data_received\"}",
            null,
            "secret-123"
        );

        verify(orderReturnService).ensurePickupScheduled("RET-B", "ExpressBees: data_received");
    }

    @Test
    void reverse_inTransit_fromPickupRequested_marksPickedUp() {
        OrderReturn orderReturn = baseReturn("RET-C", OrderReturnStatus.PICKUP_REQUESTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-C")).thenReturn(Optional.of(orderReturn));

        Map<String, Object> result = handlerService.handle(
            "{\"event_id\":\"evt-transit\",\"return_number\":\"RET-C\",\"awb\":\"REV-3\",\"status\":\"in_transit\"}",
            null,
            "secret-123"
        );

        assertThat(result.get("local_status")).isEqualTo("picked_up");
        verify(orderReturnService).markPickedUp("RET-C", "ExpressBees: in_transit");
    }

    @Test
    void reverse_inTransit_fromApproved_schedulesThenPicksUp() {
        OrderReturn orderReturn = baseReturn("RET-D", OrderReturnStatus.APPROVED);
        when(orderReturnRepository.findOneByReturnNumber("RET-D")).thenReturn(Optional.of(orderReturn));
        when(orderReturnService.ensurePickupScheduled(eq("RET-D"), any())).thenAnswer(inv -> {
            orderReturn.setStatus(OrderReturnStatus.PICKUP_SCHEDULED);
            return orderReturn;
        });
        when(orderReturnRepository.findOneByReturnNumber("RET-D")).thenReturn(Optional.of(orderReturn));

        handlerService.handle(
            "{\"event_id\":\"evt-transit2\",\"return_number\":\"RET-D\",\"awb\":\"REV-4\",\"status\":\"picked_up\"}",
            null,
            "secret-123"
        );

        verify(orderReturnService).ensurePickupScheduled("RET-D", "ExpressBees: picked_up");
        verify(orderReturnService).markPickedUp("RET-D", "ExpressBees: picked_up");
    }

    @Test
    void reverse_pickupFailed_fromPickupScheduled_marksFailed() {
        OrderReturn orderReturn = baseReturn("RET-E", OrderReturnStatus.PICKUP_SCHEDULED);
        when(orderReturnRepository.findOneByReturnNumber("RET-E")).thenReturn(Optional.of(orderReturn));

        Map<String, Object> result = handlerService.handle(
            "{\"event_id\":\"evt-pf\",\"return_number\":\"RET-E\",\"awb\":\"REV-5\",\"status\":\"pickup_failed\",\"failure_reason\":\"No show\"}",
            null,
            "secret-123"
        );

        assertThat(result.get("local_status")).isEqualTo("pickup_failed");
        verify(orderReturnService).markPickupFailed("RET-E", "No show", "ExpressBees webhook");
    }

    @Test
    void reverse_pickupFailed_fromApproved_usesProviderStatusWhenReasonBlank() {
        OrderReturn orderReturn = baseReturn("RET-F", OrderReturnStatus.APPROVED);
        when(orderReturnRepository.findOneByReturnNumber("RET-F")).thenReturn(Optional.of(orderReturn));

        handlerService.handle(
            "{\"event_id\":\"evt-pf2\",\"return_number\":\"RET-F\",\"awb\":\"REV-6\",\"status\":\"pickup_failed\"}",
            null,
            "secret-123"
        );

        verify(orderReturnService).markPickupFailed("RET-F", "pickup_failed", "ExpressBees webhook");
    }

    @Test
    void reverse_unmappedStatus_keepsCurrentStatus() {
        OrderReturn orderReturn = baseReturn("RET-G", OrderReturnStatus.PICKUP_SCHEDULED);
        when(orderReturnRepository.findOneByReturnNumber("RET-G")).thenReturn(Optional.of(orderReturn));

        Map<String, Object> result = handlerService.handle(
            "{\"event_id\":\"evt-unknown\",\"return_number\":\"RET-G\",\"awb\":\"REV-7\",\"status\":\"totally_unknown_code\"}",
            null,
            "secret-123"
        );

        assertThat(result.get("local_status")).isEqualTo("pickup_scheduled");
        verify(orderReturnService, never()).markPickedUp(any(), any());
        verify(orderReturnService, never()).markReceived(any(), any());
    }

    @Test
    void reverse_setsReverseAwbAndLogisticsWhenMissing() {
        OrderReturn orderReturn = baseReturn("RET-H", OrderReturnStatus.PICKUP_SCHEDULED);
        orderReturn.setReverseAwb(null);
        when(orderReturnRepository.findOneByReturnNumber("RET-H")).thenReturn(Optional.of(orderReturn));

        handlerService.handle(
            "{\"event_id\":\"evt-awb\",\"return_number\":\"RET-H\",\"awb\":\"NEW-REV-AWB\",\"status\":\"in_transit\"}",
            null,
            "secret-123"
        );

        assertThat(orderReturn.getReverseAwb()).isEqualTo("NEW-REV-AWB");
        assertThat(orderReturn.getCarrierName()).isEqualTo("XpressBees");
        verify(orderReturnRepository, org.mockito.Mockito.atLeastOnce()).save(orderReturn);
    }

    @Test
    void outbound_deliveryFailed_fromReadyToShip_marksShippedWithoutNotify() {
        OrderReturn orderReturn = baseReturn("RET-OUT", OrderReturnStatus.READY_TO_SHIP);
        orderReturn.setOutboundAwb("OUT-1");
        orderReturn.setRequestType(OrderRequestType.EXCHANGE);
        when(orderReturnRepository.findFirstByOutboundAwbOrderByRequestedAtDesc("OUT-1")).thenReturn(Optional.of(orderReturn));

        Map<String, Object> result = handlerService.handle(
            "{\"event_id\":\"evt-df\",\"awb\":\"OUT-1\",\"status\":\"delivery_failed\"}",
            null,
            "secret-123"
        );

        assertThat(result.get("local_status")).isEqualTo("shipped");
        verify(orderReturnService).markShipped("RET-OUT", "ExpressBees: delivery_failed");
        verify(orderReturnService, never()).notifyExchangeOutForDelivery(any());
    }

    @Test
    void outbound_delivered_nonExchange_stopsAtDelivered() {
        OrderReturn orderReturn = baseReturn("RET-REF", OrderReturnStatus.SHIPPED);
        orderReturn.setOutboundAwb("OUT-2");
        orderReturn.setRequestType(OrderRequestType.RETURN);
        when(orderReturnRepository.findFirstByOutboundAwbOrderByRequestedAtDesc("OUT-2")).thenReturn(Optional.of(orderReturn));

        Map<String, Object> result = handlerService.handle(
            "{\"event_id\":\"evt-del\",\"awb\":\"OUT-2\",\"status\":\"delivered\"}",
            null,
            "secret-123"
        );

        assertThat(result.get("local_status")).isEqualTo("delivered");
        verify(orderReturnService, never()).completeExchange(any(), any());
    }

    private static OrderReturn baseReturn(String returnNumber, OrderReturnStatus status) {
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber(returnNumber);
        orderReturn.setStatus(status);
        orderReturn.setRequestType(OrderRequestType.RETURN);
        return orderReturn;
    }
}
