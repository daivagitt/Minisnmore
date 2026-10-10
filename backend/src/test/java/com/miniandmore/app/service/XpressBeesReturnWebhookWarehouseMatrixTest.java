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
import com.miniandmore.app.domain.enumeration.ReturnLogisticsMode;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class XpressBeesReturnWebhookWarehouseMatrixTest {

    private final ApplicationProperties applicationProperties = org.mockito.Mockito.mock(ApplicationProperties.class);
    private final XpressBeesReturnWebhookEventRepository webhookEventRepository =
        org.mockito.Mockito.mock(XpressBeesReturnWebhookEventRepository.class);
    private final OrderReturnRepository orderReturnRepository = org.mockito.Mockito.mock(OrderReturnRepository.class);
    private final OrderReturnService orderReturnService = org.mockito.Mockito.mock(OrderReturnService.class);
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
                new ObjectMapper()
            );
    }

    @Test
    void reverse_deliveredToWarehouse_fromApprovedExchange_schedulesPicksUpAndReceives() {
        OrderReturn orderReturn = base("RET-WH-A", OrderReturnStatus.APPROVED);
        orderReturn.setRequestType(OrderRequestType.EXCHANGE);
        when(orderReturnRepository.findOneByReturnNumber("RET-WH-A")).thenReturn(Optional.of(orderReturn));
        when(orderReturnService.ensurePickupScheduled(eq("RET-WH-A"), any())).thenAnswer(inv -> {
            orderReturn.setStatus(OrderReturnStatus.PICKUP_SCHEDULED);
            return orderReturn;
        });

        Map<String, Object> result = handlerService.handle(
            "{\"event_id\":\"evt-wh1\",\"return_number\":\"RET-WH-A\",\"awb\":\"REV-WH\",\"status\":\"delivered\"}",
            null,
            "secret-123"
        );

        assertThat(result.get("ok")).isEqualTo(true);
        verify(orderReturnService).ensurePickupScheduled("RET-WH-A", "ExpressBees: delivered");
        verify(orderReturnService).markPickedUp("RET-WH-A", "ExpressBees: delivered");
        verify(orderReturnService).markReceived("RET-WH-A", "ExpressBees: delivered");
    }

    @Test
    void reverse_deliveredToWarehouse_fromPickedUpReturn_marksReceivedOnly() {
        OrderReturn orderReturn = base("RET-WH-B", OrderReturnStatus.PICKED_UP);
        when(orderReturnRepository.findOneByReturnNumber("RET-WH-B")).thenReturn(Optional.of(orderReturn));

        handlerService.handle(
            "{\"event_id\":\"evt-wh2\",\"return_number\":\"RET-WH-B\",\"awb\":\"REV-WH2\",\"status\":\"warehouse_received\"}",
            null,
            "secret-123"
        );

        verify(orderReturnService, never()).ensurePickupScheduled(any(), any());
        verify(orderReturnService, never()).markPickedUp(any(), any());
        verify(orderReturnService).markReceived("RET-WH-B", "ExpressBees: warehouse_received");
    }

    @Test
    void reverse_deliveredToWarehouse_fromInspected_doesNotAdvance() {
        OrderReturn orderReturn = base("RET-WH-C", OrderReturnStatus.RECEIVED);
        when(orderReturnRepository.findOneByReturnNumber("RET-WH-C")).thenReturn(Optional.of(orderReturn));

        Map<String, Object> result = handlerService.handle(
            "{\"event_id\":\"evt-wh3\",\"return_number\":\"RET-WH-C\",\"awb\":\"REV-WH3\",\"status\":\"received\"}",
            null,
            "secret-123"
        );

        assertThat(result.get("local_status")).isEqualTo("received");
        verify(orderReturnService, never()).markReceived(any(), any());
    }

    @Test
    void reverse_pickupFailed_fromRequested_marksFailed() {
        OrderReturn orderReturn = base("RET-WH-D", OrderReturnStatus.PICKUP_REQUESTED);
        when(orderReturnRepository.findOneByReturnNumber("RET-WH-D")).thenReturn(Optional.of(orderReturn));

        handlerService.handle(
            "{\"event_id\":\"evt-wh4\",\"return_number\":\"RET-WH-D\",\"awb\":\"REV-WH4\",\"status\":\"pickup_not_done\"}",
            null,
            "secret-123"
        );

        verify(orderReturnService).markPickupFailed("RET-WH-D", "pickup_not_done", "ExpressBees webhook");
    }

    @Test
    void reverse_blankPreferredStatusStillSavesLogisticsDefaults() {
        OrderReturn orderReturn = base("RET-WH-E", OrderReturnStatus.PICKUP_SCHEDULED);
        orderReturn.setLogisticsMode(null);
        orderReturn.setCarrierName("  ");
        when(orderReturnRepository.findOneByReturnNumber("RET-WH-E")).thenReturn(Optional.of(orderReturn));

        handlerService.handle(
            "{\"event_id\":\"evt-wh5\",\"return_number\":\"RET-WH-E\",\"awb\":\"\",\"status\":\"totally_unknown\"}",
            null,
            "secret-123"
        );

        assertThat(orderReturn.getLogisticsMode()).isEqualTo(ReturnLogisticsMode.XPRESSBEES);
        assertThat(orderReturn.getCarrierName()).isEqualTo("XpressBees");
    }

    @Test
    void outbound_outForDelivery_notifiesOnceFromReadyToShip() {
        OrderReturn orderReturn = base("RET-OUT-OFD", OrderReturnStatus.READY_TO_SHIP);
        orderReturn.setOutboundAwb("OUT-OFD");
        orderReturn.setRequestType(OrderRequestType.EXCHANGE);
        when(orderReturnRepository.findFirstByOutboundAwbOrderByRequestedAtDesc("OUT-OFD")).thenReturn(Optional.of(orderReturn));
        when(webhookEventRepository.existsCompletedOutForDeliveryEvent("RET-OUT-OFD")).thenReturn(false);

        Map<String, Object> result = handlerService.handle(
            "{\"event_id\":\"evt-ofd\",\"awb\":\"OUT-OFD\",\"status\":\"out_for_delivery\"}",
            null,
            "secret-123"
        );

        assertThat(result.get("local_status")).isEqualTo("shipped");
        verify(orderReturnService).markShipped("RET-OUT-OFD", "ExpressBees: out_for_delivery");
        verify(orderReturnService).notifyExchangeOutForDelivery("RET-OUT-OFD");
    }

    @Test
    void outbound_deliveredExchangeFromReadyToShip_completes() {
        OrderReturn orderReturn = base("RET-OUT-DEL", OrderReturnStatus.READY_TO_SHIP);
        orderReturn.setOutboundAwb("OUT-DEL");
        orderReturn.setRequestType(OrderRequestType.EXCHANGE);
        when(orderReturnRepository.findFirstByOutboundAwbOrderByRequestedAtDesc("OUT-DEL")).thenReturn(Optional.of(orderReturn));
        when(orderReturnService.markShipped(eq("RET-OUT-DEL"), any())).thenAnswer(inv -> {
            orderReturn.setStatus(OrderReturnStatus.SHIPPED);
            return orderReturn;
        });
        when(orderReturnService.markDelivered(eq("RET-OUT-DEL"), any())).thenAnswer(inv -> {
            orderReturn.setStatus(OrderReturnStatus.DELIVERED);
            return orderReturn;
        });

        Map<String, Object> result = handlerService.handle(
            "{\"event_id\":\"evt-odel\",\"awb\":\"OUT-DEL\",\"status\":\"delivered\"}",
            null,
            "secret-123"
        );

        assertThat(result.get("local_status")).isEqualTo("completed");
        verify(orderReturnService).completeExchange("RET-OUT-DEL", "ExpressBees: delivered");
    }

    private static OrderReturn base(String returnNumber, OrderReturnStatus status) {
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber(returnNumber);
        orderReturn.setStatus(status);
        orderReturn.setRequestType(OrderRequestType.RETURN);
        return orderReturn;
    }
}
