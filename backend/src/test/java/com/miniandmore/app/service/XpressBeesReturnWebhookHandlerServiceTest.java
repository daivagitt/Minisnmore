package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.XpressBeesReturnWebhookEvent;
import com.miniandmore.app.domain.enumeration.OrderRequestType;
import com.miniandmore.app.domain.enumeration.OrderReturnStatus;
import com.miniandmore.app.domain.enumeration.ReturnLogisticsMode;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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
        when(webhookEventRepository.findOneByEventId(any())).thenReturn(Optional.empty());
        when(webhookEventRepository.deletePendingByEventId(any())).thenReturn(0);
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
    void handle_reversePickedUpWithoutAwb_doesNotTreatAsOutboundWhenReplacementBooked() {
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber("EXC-ORD-1");
        orderReturn.setStatus(OrderReturnStatus.READY_TO_SHIP);
        orderReturn.setRequestType(OrderRequestType.EXCHANGE);
        orderReturn.setOutboundAwb("OUT-AWB-9");
        orderReturn.setReverseAwb("REV-9");
        orderReturn.setLogisticsMode(ReturnLogisticsMode.XPRESSBEES);
        when(orderReturnRepository.findOneByReturnNumber("EXC-ORD-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(webhookEventRepository.existsByEventId("evt-rev-no-awb")).thenReturn(false);
        when(webhookEventRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(webhookEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = handlerService.handle(
            "{\"event_id\":\"evt-rev-no-awb\",\"return_number\":\"EXC-ORD-1\",\"status\":\"picked_up\"}",
            null,
            "secret-123"
        );

        assertThat(result.get("ok")).isEqualTo(true);
        verify(orderReturnService, never()).markShipped(any(), any());
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
        XpressBeesReturnWebhookEvent completed = new XpressBeesReturnWebhookEvent();
        completed.setEventId("evt-dup");
        completed.setLocalStatus("picked_up");
        when(webhookEventRepository.findOneByEventId("evt-dup")).thenReturn(Optional.of(completed));

        Map<String, Object> result = handlerService.handle(
            "{\"event_id\":\"evt-dup\",\"return_number\":\"RET-ORD-1\",\"status\":\"picked_up\"}",
            null,
            "secret-123"
        );

        assertThat(result.get("duplicate")).isEqualTo(true);
        verify(orderReturnService, never()).markPickedUp(any(), any());
    }

    @Test
    void handle_applyFailure_clearsPendingEventSoCarrierCanRetry() {
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber("RET-ORD-1");
        orderReturn.setStatus(OrderReturnStatus.PICKUP_SCHEDULED);
        when(orderReturnRepository.findOneByReturnNumber("RET-ORD-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(webhookEventRepository.findOneByEventId("evt-retry")).thenReturn(Optional.empty());
        when(webhookEventRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(webhookEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "blocked"))
            .when(orderReturnService)
            .markPickedUp(any(), any());

        assertThatThrownBy(() ->
            handlerService.handle(
                "{\"event_id\":\"evt-retry\",\"return_number\":\"RET-ORD-1\",\"status\":\"picked_up\"}",
                null,
                "secret-123"
            )
        )
            .isInstanceOf(ResponseStatusException.class);

        verify(webhookEventRepository, atLeastOnce()).deletePendingByEventId("evt-retry");
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
        when(orderReturnRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(webhookEventRepository.existsByEventId("evt-approved-pickup")).thenReturn(false);
        when(webhookEventRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(webhookEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderReturnService.ensurePickupScheduled(eq("RET-ORD-1"), any())).thenAnswer(invocation -> {
            orderReturn.setStatus(OrderReturnStatus.PICKUP_SCHEDULED);
            return orderReturn;
        });

        Map<String, Object> result = handlerService.handle(
            "{\"event_id\":\"evt-approved-pickup\",\"return_number\":\"RET-ORD-1\",\"awb\":\"REV-1\",\"status\":\"picked_up\"}",
            null,
            "secret-123"
        );

        assertThat(result.get("ok")).isEqualTo(true);
        verify(orderReturnService).ensurePickupScheduled("RET-ORD-1", "ExpressBees: picked_up");
        verify(orderReturnService).markPickedUp("RET-ORD-1", "ExpressBees: picked_up");
    }

    @Test
    void handle_receivedWebhookFromApproved_marksReturnReceived() {
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber("RET-ORD-1");
        orderReturn.setStatus(OrderReturnStatus.APPROVED);
        when(orderReturnRepository.findOneByReturnNumber("RET-ORD-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(webhookEventRepository.existsByEventId("evt-approved-received")).thenReturn(false);
        when(webhookEventRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(webhookEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderReturnService.ensurePickupScheduled(eq("RET-ORD-1"), any())).thenAnswer(invocation -> {
            orderReturn.setStatus(OrderReturnStatus.PICKUP_SCHEDULED);
            return orderReturn;
        });

        Map<String, Object> result = handlerService.handle(
            "{\"event_id\":\"evt-approved-received\",\"return_number\":\"RET-ORD-1\",\"awb\":\"REV-2\",\"status\":\"warehouse_received\"}",
            null,
            "secret-123"
        );

        assertThat(result.get("ok")).isEqualTo(true);
        verify(orderReturnService).ensurePickupScheduled("RET-ORD-1", "ExpressBees: warehouse_received");
        verify(orderReturnService).markReceived("RET-ORD-1", "ExpressBees: warehouse_received");
    }

    @Test
    void handle_receivedWebhookOnExchangePickupScheduled_marksPickedUpThenReceived() {
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber("EXC-ORD-1");
        orderReturn.setStatus(OrderReturnStatus.PICKUP_SCHEDULED);
        orderReturn.setRequestType(OrderRequestType.EXCHANGE);
        when(orderReturnRepository.findOneByReturnNumber("EXC-ORD-1")).thenReturn(Optional.of(orderReturn));
        when(webhookEventRepository.existsByEventId("evt-exchange-received")).thenReturn(false);
        when(webhookEventRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(webhookEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = handlerService.handle(
            "{\"event_id\":\"evt-exchange-received\",\"return_number\":\"EXC-ORD-1\",\"awb\":\"REV-EX-1\",\"status\":\"warehouse_received\"}",
            null,
            "secret-123"
        );

        assertThat(result.get("ok")).isEqualTo(true);
        verify(orderReturnService).markPickedUp("EXC-ORD-1", "ExpressBees: warehouse_received");
        verify(orderReturnService).markReceived("EXC-ORD-1", "ExpressBees: warehouse_received");
    }

    @Test
    void handle_receivedWebhookOnExchangeAlreadyPickedUp_onlyMarksReceived() {
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber("EXC-ORD-1");
        orderReturn.setStatus(OrderReturnStatus.PICKED_UP);
        orderReturn.setRequestType(OrderRequestType.EXCHANGE);
        orderReturn.setReverseAwb("REV-EX-1");
        when(orderReturnRepository.findOneByReturnNumber("EXC-ORD-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(webhookEventRepository.existsByEventId("evt-exchange-picked-received")).thenReturn(false);
        when(webhookEventRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(webhookEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = handlerService.handle(
            "{\"event_id\":\"evt-exchange-picked-received\",\"return_number\":\"EXC-ORD-1\",\"awb\":\"REV-EX-1\",\"status\":\"warehouse_received\"}",
            null,
            "secret-123"
        );

        assertThat(result.get("ok")).isEqualTo(true);
        assertThat(result.get("local_status")).isEqualTo("received");
        verify(orderReturnService, never()).markPickedUp(any(), any());
        verify(orderReturnService).markReceived("EXC-ORD-1", "ExpressBees: warehouse_received");
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
    void handle_outboundOutForDelivery_notifiesCustomer() {
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber("RET-ORD-1");
        orderReturn.setStatus(OrderReturnStatus.SHIPPED);
        orderReturn.setOutboundAwb("OUT-AWB-9");
        orderReturn.setRequestType(OrderRequestType.EXCHANGE);
        when(orderReturnRepository.findFirstByOutboundAwbOrderByRequestedAtDesc("OUT-AWB-9")).thenReturn(Optional.of(orderReturn));
        when(webhookEventRepository.existsByEventId(any())).thenReturn(false);
        when(webhookEventRepository.existsCompletedOutForDeliveryEvent("RET-ORD-1")).thenReturn(false);
        when(webhookEventRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(webhookEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = handlerService.handle(
            "{\"event_id\":\"evt-out-ofd\",\"awb\":\"OUT-AWB-9\",\"status\":\"OUT FOR DELIVERY\"}",
            null,
            "secret-123"
        );

        assertThat(result.get("ok")).isEqualTo(true);
        verify(orderReturnService).notifyExchangeOutForDelivery("RET-ORD-1");
    }

    @Test
    void handle_outboundOutForDelivery_skipsDuplicateNotification() {
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber("RET-ORD-1");
        orderReturn.setStatus(OrderReturnStatus.SHIPPED);
        orderReturn.setOutboundAwb("OUT-AWB-9");
        orderReturn.setRequestType(OrderRequestType.EXCHANGE);
        when(orderReturnRepository.findFirstByOutboundAwbOrderByRequestedAtDesc("OUT-AWB-9")).thenReturn(Optional.of(orderReturn));
        when(webhookEventRepository.existsByEventId(any())).thenReturn(false);
        when(webhookEventRepository.existsCompletedOutForDeliveryEvent("RET-ORD-1")).thenReturn(true);
        when(webhookEventRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(webhookEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = handlerService.handle(
            "{\"event_id\":\"evt-out-ofd-2\",\"awb\":\"OUT-AWB-9\",\"status\":\"OUT FOR DELIVERY\"}",
            null,
            "secret-123"
        );

        assertThat(result.get("ok")).isEqualTo(true);
        verify(orderReturnService, never()).notifyExchangeOutForDelivery("RET-ORD-1");
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

    @Test
    void handle_unconfiguredSecret_throwsServiceUnavailable() {
        when(applicationProperties.getXpressbees()).thenReturn(new ApplicationProperties.XpressBees());

        assertThatThrownBy(() -> handlerService.handle("{}", null, "secret"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .satisfies(ex -> assertThat(((org.springframework.web.server.ResponseStatusException) ex).getStatusCode().value()).isEqualTo(503));
    }

    @Test
    void handle_missingStatus_throwsBadRequest() {
        assertThatThrownBy(() -> handlerService.handle("{\"return_number\":\"RET-1\"}", null, "secret-123"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .satisfies(ex -> assertThat(((org.springframework.web.server.ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void handle_missingReturnNumberAndAwb_throwsBadRequest() {
        assertThatThrownBy(() -> handlerService.handle("{\"status\":\"picked_up\"}", null, "secret-123"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .satisfies(ex -> assertThat(((org.springframework.web.server.ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void handle_returnNotFound_throwsNotFound() {
        when(orderReturnRepository.findOneByReturnNumber("NON_EXISTENT")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handlerService.handle("{\"return_number\":\"NON_EXISTENT\",\"status\":\"picked_up\"}", null, "secret-123"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .satisfies(ex -> assertThat(((org.springframework.web.server.ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void handle_awbNotFoundInReverseOrOutbound_throwsNotFound() {
        when(orderReturnRepository.findFirstByReverseAwbOrderByRequestedAtDesc("UNKNOWN-AWB")).thenReturn(Optional.empty());
        when(orderReturnRepository.findFirstByOutboundAwbOrderByRequestedAtDesc("UNKNOWN-AWB")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handlerService.handle("{\"awb\":\"UNKNOWN-AWB\",\"status\":\"picked_up\"}", null, "secret-123"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .satisfies(ex -> assertThat(((org.springframework.web.server.ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void handle_dbConcurrencyDuplicate_returnsDuplicateResult() {
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber("RET-ORD-1");
        when(orderReturnRepository.findOneByReturnNumber("RET-ORD-1")).thenReturn(Optional.of(orderReturn));
        when(webhookEventRepository.existsByEventId(any())).thenReturn(false);
        when(webhookEventRepository.saveAndFlush(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key"));

        Map<String, Object> result = handlerService.handle(
            "{\"event_id\":\"evt-race\",\"return_number\":\"RET-ORD-1\",\"status\":\"picked_up\"}",
            null,
            "secret-123"
        );

        assertThat(result.get("duplicate")).isEqualTo(true);
    }

    @Test
    void handle_reversePickupScheduledAndFailed_triggersServiceMethods() {
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber("RET-ORD-1");
        orderReturn.setStatus(OrderReturnStatus.APPROVED);
        when(orderReturnRepository.findOneByReturnNumber("RET-ORD-1")).thenReturn(Optional.of(orderReturn));
        when(webhookEventRepository.existsByEventId(any())).thenReturn(false);
        when(webhookEventRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(webhookEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // pickup_scheduled
        Map<String, Object> resSched = handlerService.handle(
            "{\"event_id\":\"evt-sched\",\"return_number\":\"RET-ORD-1\",\"status\":\"pickup_scheduled\"}",
            null,
            "secret-123"
        );
        assertThat(resSched.get("local_status")).isEqualTo("pickup_scheduled");
        verify(orderReturnService).ensurePickupScheduled("RET-ORD-1", "ExpressBees: pickup_scheduled");

        // pickup_failed
        orderReturn.setStatus(OrderReturnStatus.PICKUP_SCHEDULED);
        Map<String, Object> resFailed = handlerService.handle(
            "{\"event_id\":\"evt-fail\",\"return_number\":\"RET-ORD-1\",\"status\":\"pickup_failed\",\"reason\":\"customer not available\"}",
            null,
            "secret-123"
        );
        assertThat(resFailed.get("local_status")).isEqualTo("pickup_failed");
        verify(orderReturnService).markPickupFailed("RET-ORD-1", "customer not available", "ExpressBees webhook");
    }

    @Test
    void handle_outboundShippedAndDeliveredReturn_handlesStatusCorrectly() {
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber("RET-ORD-1");
        orderReturn.setStatus(OrderReturnStatus.READY_TO_SHIP);
        orderReturn.setOutboundAwb("OUT-123");
        orderReturn.setRequestType(OrderRequestType.RETURN); // Standard RETURN, not EXCHANGE
        when(orderReturnRepository.findOneByReturnNumber("RET-ORD-1")).thenReturn(Optional.of(orderReturn));
        when(webhookEventRepository.existsByEventId(any())).thenReturn(false);
        when(webhookEventRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(webhookEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // shipped
        Map<String, Object> resShipped = handlerService.handle(
            "{\"event_id\":\"evt-ship\",\"return_number\":\"RET-ORD-1\",\"awb\":\"OUT-123\",\"status\":\"shipped\"}",
            null,
            "secret-123"
        );
        assertThat(resShipped.get("local_status")).isEqualTo("shipped");
        verify(orderReturnService).markShipped("RET-ORD-1", "ExpressBees: shipped");

        // delivered for RETURN
        orderReturn.setStatus(OrderReturnStatus.SHIPPED);
        Map<String, Object> resDelivered = handlerService.handle(
            "{\"event_id\":\"evt-deliv\",\"return_number\":\"RET-ORD-1\",\"awb\":\"OUT-123\",\"status\":\"delivered\"}",
            null,
            "secret-123"
        );
        assertThat(resDelivered.get("local_status")).isEqualTo("delivered");
        verify(orderReturnService).markDelivered("RET-ORD-1", "ExpressBees: delivered");
        verify(orderReturnService, never()).completeExchange(any(), any());
    }

    @Test
    void handle_reverseInTransit_marksPickedUpAndStoresMilestone() {
        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber("RET-ORD-1");
        orderReturn.setStatus(OrderReturnStatus.PICKUP_SCHEDULED);
        orderReturn.setReverseAwb("REV-IT");
        when(orderReturnRepository.findOneByReturnNumber("RET-ORD-1")).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(webhookEventRepository.existsByEventId(any())).thenReturn(false);
        when(webhookEventRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(webhookEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = handlerService.handle(
            "{\"event_id\":\"evt-it\",\"return_number\":\"RET-ORD-1\",\"awb\":\"REV-IT\",\"status\":\"InTransit\"}",
            null,
            "secret-123"
        );

        assertThat(result.get("local_status")).isEqualTo("picked_up");
        assertThat(orderReturn.getReverseCarrierStatus()).isEqualTo("in_transit");
        verify(orderReturnService).markPickedUp("RET-ORD-1", "ExpressBees: InTransit");
    }
}
