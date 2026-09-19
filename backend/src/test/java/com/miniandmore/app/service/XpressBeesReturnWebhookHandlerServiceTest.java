package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.enumeration.OrderRequestType;
import com.miniandmore.app.domain.enumeration.OrderReturnStatus;
import com.miniandmore.app.repository.OrderReturnRepository;
import com.miniandmore.app.repository.XpressBeesReturnWebhookEventRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class XpressBeesReturnWebhookHandlerServiceTest {

    @Mock
    private ApplicationProperties applicationProperties;

    @Mock
    private XpressBeesReturnWebhookEventRepository webhookEventRepository;

    @Mock
    private OrderReturnRepository orderReturnRepository;

    @Mock
    private OrderReturnService orderReturnService;

    @InjectMocks
    private XpressBeesReturnWebhookHandlerService handlerService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ApplicationProperties.XpressBees xpressBees = new ApplicationProperties.XpressBees();
        xpressBees.setWebhookSecret("secret-123");
        when(applicationProperties.getXpressbees()).thenReturn(xpressBees);
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
    void handle_pickedUpWebhook_marksReturnPickedUp() {
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber("RET-ORD-1");
        orderReturn.setStatus(OrderReturnStatus.PICKUP_SCHEDULED);
        when(orderReturnRepository.findOneByReturnNumber("RET-ORD-1")).thenReturn(Optional.of(orderReturn));
        when(webhookEventRepository.existsByEventId("evt-1")).thenReturn(false);
        when(webhookEventRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(webhookEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = handlerService.handle(
            "{\"event_id\":\"evt-1\",\"return_number\":\"RET-ORD-1\",\"status\":\"picked_up\"}",
            null,
            "secret-123"
        );

        assertThat(result.get("ok")).isEqualTo(true);
        verify(orderReturnService).markPickedUp("RET-ORD-1", "ExpressBees: picked_up");
    }

    @Test
    void handle_duplicateEvent_isIdempotent() {
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber("RET-ORD-1");
        when(orderReturnRepository.findOneByReturnNumber("RET-ORD-1")).thenReturn(Optional.of(orderReturn));
        when(webhookEventRepository.existsByEventId("evt-dup")).thenReturn(true);

        Map<String, Object> result = handlerService.handle(
            "{\"event_id\":\"evt-dup\",\"return_number\":\"RET-ORD-1\",\"status\":\"picked_up\"}",
            null,
            "secret-123"
        );

        assertThat(result.get("duplicate")).isEqualTo(true);
    }

    @Test
    void handle_outboundDeliveredWebhook_marksExchangeDelivered() {
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber("RET-ORD-1");
        orderReturn.setStatus(OrderReturnStatus.SHIPPED);
        orderReturn.setOutboundAwb("OUT-AWB-9");
        orderReturn.setRequestType(OrderRequestType.EXCHANGE);
        when(orderReturnRepository.findFirstByOutboundAwbOrderByRequestedAtDesc("OUT-AWB-9")).thenReturn(Optional.of(orderReturn));
        when(webhookEventRepository.existsByEventId(any())).thenReturn(false);
        when(webhookEventRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(webhookEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = handlerService.handle(
            "{\"event_id\":\"evt-out-1\",\"awb\":\"OUT-AWB-9\",\"status\":\"delivered\"}",
            null,
            "secret-123"
        );

        assertThat(result.get("ok")).isEqualTo(true);
        verify(orderReturnService).markDelivered("RET-ORD-1", "ExpressBees: delivered");
        verify(orderReturnService).completeExchange("RET-ORD-1", "ExpressBees: delivered");
    }

    @Test
    void handle_pickedUpWebhookFromApproved_marksReturnPickedUp() {
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber("RET-ORD-1");
        orderReturn.setStatus(OrderReturnStatus.APPROVED);
        when(orderReturnRepository.findOneByReturnNumber("RET-ORD-1")).thenReturn(Optional.of(orderReturn));
        when(webhookEventRepository.existsByEventId("evt-approved-pickup")).thenReturn(false);
        when(webhookEventRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(webhookEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = handlerService.handle(
            "{\"event_id\":\"evt-approved-pickup\",\"return_number\":\"RET-ORD-1\",\"status\":\"picked_up\"}",
            null,
            "secret-123"
        );

        assertThat(result.get("ok")).isEqualTo(true);
        verify(orderReturnService).markPickedUp("RET-ORD-1", "ExpressBees: picked_up");
    }

    @Test
    void handle_receivedWebhookFromApproved_marksReturnReceived() {
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber("RET-ORD-1");
        orderReturn.setStatus(OrderReturnStatus.APPROVED);
        when(orderReturnRepository.findOneByReturnNumber("RET-ORD-1")).thenReturn(Optional.of(orderReturn));
        when(webhookEventRepository.existsByEventId("evt-approved-received")).thenReturn(false);
        when(webhookEventRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(webhookEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = handlerService.handle(
            "{\"event_id\":\"evt-approved-received\",\"return_number\":\"RET-ORD-1\",\"status\":\"warehouse_received\"}",
            null,
            "secret-123"
        );

        assertThat(result.get("ok")).isEqualTo(true);
        verify(orderReturnService).markReceived("RET-ORD-1", "ExpressBees: warehouse_received");
    }

    @Test
    void handle_outboundDeliveredFromReadyToShip_marksShippedThenDelivered() {
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber("RET-ORD-1");
        orderReturn.setStatus(OrderReturnStatus.READY_TO_SHIP);
        orderReturn.setOutboundAwb("OUT-AWB-9");
        orderReturn.setRequestType(OrderRequestType.EXCHANGE);
        when(orderReturnRepository.findFirstByOutboundAwbOrderByRequestedAtDesc("OUT-AWB-9")).thenReturn(Optional.of(orderReturn));
        when(webhookEventRepository.existsByEventId(any())).thenReturn(false);
        when(webhookEventRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(webhookEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = handlerService.handle(
            "{\"event_id\":\"evt-out-2\",\"awb\":\"OUT-AWB-9\",\"status\":\"delivered\"}",
            null,
            "secret-123"
        );

        assertThat(result.get("ok")).isEqualTo(true);
        verify(orderReturnService).markShipped("RET-ORD-1", "ExpressBees: delivered");
        verify(orderReturnService).markDelivered("RET-ORD-1", "ExpressBees: delivered");
        verify(orderReturnService).completeExchange("RET-ORD-1", "ExpressBees: delivered");
    }

    @Test
    void handle_samplePayloadAwbNumber_looksUpReturnByAwb() {
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber("RET-ORD-1");
        orderReturn.setStatus(OrderReturnStatus.PICKUP_SCHEDULED);
        orderReturn.setReverseAwb("4152912381315");
        when(orderReturnRepository.findFirstByReverseAwbOrderByRequestedAtDesc("4152912381315")).thenReturn(Optional.of(orderReturn));
        when(webhookEventRepository.existsByEventId(any())).thenReturn(false);
        when(webhookEventRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(webhookEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = handlerService.handle(
            "{\"awb_number\":\"4152912381315\",\"status\":\"picked_up\",\"event_time\":\"2021-02-26 16:19:59\"}",
            null,
            "secret-123"
        );

        assertThat(result.get("ok")).isEqualTo(true);
        verify(orderReturnService).markPickedUp("RET-ORD-1", "ExpressBees: picked_up");
    }
}
