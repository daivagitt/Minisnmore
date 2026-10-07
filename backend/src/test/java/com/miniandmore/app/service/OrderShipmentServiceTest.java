package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.client.XpressBeesClient;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PackageType;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.domain.enumeration.ReturnLogisticsMode;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.OrderItemRepository;
import com.miniandmore.app.repository.StoreAddressRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class OrderShipmentServiceTest {

    @Mock
    private CustomerOrderRepository customerOrderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private XpressBeesClient xpressBeesClient;

    @Mock
    private ApplicationProperties applicationProperties;

    @Mock
    private OrderEventService orderEventService;

    @Mock
    private OrderCancellationService orderCancellationService;

    @Mock
    private OrderMailService orderMailService;

    @Mock
    private StoreAddressRepository storeAddressRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private OrderShipmentService orderShipmentService;

    @BeforeEach
    void setUp() {
        ApplicationProperties.XpressBees xpressBees = new ApplicationProperties.XpressBees();
        xpressBees.setWarehouseName("Mini's n More");
        xpressBees.setWarehouseLine1("Warehouse 1");
        xpressBees.setWarehouseCity("Bengaluru");
        xpressBees.setWarehouseState("Karnataka");
        xpressBees.setOriginPincode("560074");
        xpressBees.setDefaultWeightGrams(500);
        org.mockito.Mockito.lenient().when(applicationProperties.getXpressbees()).thenReturn(xpressBees);
        org.mockito.Mockito.lenient().when(orderItemRepository.findAllByOrderId(any())).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.lenient().when(orderCancellationService.hasPendingRequest(any())).thenReturn(false);
    }

    @Test
    void isShipmentEligible_confirmedPaidOnline_isTrue() {
        assertThat(OrderShipmentService.isShipmentEligible(confirmedPaid())).isTrue();
    }

    @Test
    void isShipmentEligible_confirmedCodUnpaid_isTrue() {
        CustomerOrder order = confirmedPaid().paymentMethod("cod").paymentStatus(PaymentStatus.PENDING);
        assertThat(OrderShipmentService.isShipmentEligible(order)).isTrue();
    }

    @Test
    void isShipmentEligible_unpaidOnline_isFalse() {
        CustomerOrder order = confirmedPaid().paymentStatus(PaymentStatus.PENDING);
        assertThat(OrderShipmentService.isShipmentEligible(order)).isFalse();
    }

    @Test
    void isShipmentEligible_alreadyTracked_isFalse() {
        CustomerOrder order = confirmedPaid().trackingNumber("AWB-1");
        assertThat(OrderShipmentService.isShipmentEligible(order)).isFalse();
    }

    @Test
    void hasLocalForwardShipment_requiresActiveBookedAwb() {
        assertThat(OrderShipmentService.hasLocalForwardShipment(confirmedPaid())).isFalse();
        CustomerOrder booked = confirmedPaid()
            .trackingNumber("XB-1")
            .shipmentLogisticsMode(ReturnLogisticsMode.XPRESSBEES);
        assertThat(OrderShipmentService.hasLocalForwardShipment(booked)).isTrue();
        booked.setShipmentCancelledAt(Instant.now());
        assertThat(OrderShipmentService.hasLocalForwardShipment(booked)).isFalse();
    }

    @Test
    void canBookExpressBees_requiresCompletedPackage() {
        assertThat(orderShipmentService.canBookExpressBees(confirmedPaid())).isFalse();
        assertThat(orderShipmentService.canBookExpressBees(packedConfirmedPaid())).isTrue();
    }

    @Test
    void canBook_blocksWhenCancellationPending() {
        CustomerOrder order = packedConfirmedPaid();
        when(orderCancellationService.hasPendingRequest(10L)).thenReturn(true);
        assertThat(orderShipmentService.canBook(order)).isFalse();
        assertThat(orderShipmentService.canBookWithOverride(order)).isTrue();
    }

    @Test
    void bookShipment_blocksWhenCancellationPendingWithoutOverride() {
        CustomerOrder order = confirmedPaid();
        when(customerOrderRepository.findOneByOrderNumber("MNM-1")).thenReturn(Optional.of(order));
        when(orderCancellationService.hasPendingRequest(10L)).thenReturn(true);

        assertThatThrownBy(() ->
            orderShipmentService.bookShipment("MNM-1", ReturnLogisticsMode.MANUAL, "Delhivery", "AWB-99", null, null, false, null)
        )
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void bookShipment_requiresOverrideReasonWhenHoldActive() {
        CustomerOrder order = confirmedPaid();
        when(customerOrderRepository.findOneByOrderNumber("MNM-1")).thenReturn(Optional.of(order));
        when(orderCancellationService.hasPendingRequest(10L)).thenReturn(true);

        assertThatThrownBy(() ->
            orderShipmentService.bookShipment("MNM-1", ReturnLogisticsMode.MANUAL, "Delhivery", "AWB-99", null, null, true, "no")
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Override reason");
    }

    @Test
    void bookShipment_allowsOverrideWhenCancellationPending() {
        CustomerOrder order = packedConfirmedPaid();
        when(customerOrderRepository.findOneByOrderNumber("MNM-1")).thenReturn(Optional.of(order));
        when(orderCancellationService.hasPendingRequest(10L)).thenReturn(true);

        CustomerOrder result = orderShipmentService.bookShipment(
            "MNM-1",
            ReturnLogisticsMode.MANUAL,
            "Delhivery",
            "AWB-99",
            null,
            null,
            true,
            "Customer withdrew verbally; ship while case is reviewed"
        );

        assertThat(result.getTrackingNumber()).isEqualTo("AWB-99");
    }

    @Test
    void canBook_requiresCompletedPackage() {
        assertThat(orderShipmentService.canBook(confirmedPaid())).isFalse();
        assertThat(orderShipmentService.canBook(packedConfirmedPaid())).isTrue();
    }

    @Test
    void canBook_confirmedCodUnpaid_isTrueWhenPacked() {
        CustomerOrder order = packedConfirmedPaid().paymentMethod("cod").paymentStatus(PaymentStatus.PENDING);
        assertThat(orderShipmentService.canBook(order)).isTrue();
    }

    @Test
    void canBook_unpaidOnline_isFalse() {
        CustomerOrder order = confirmedPaid().paymentStatus(PaymentStatus.PENDING);
        assertThat(orderShipmentService.canBook(order)).isFalse();
    }

    @Test
    void canBook_alreadyTracked_isFalse() {
        CustomerOrder order = confirmedPaid().trackingNumber("AWB-1");
        assertThat(orderShipmentService.canBook(order)).isFalse();
    }

    @Test
    void bookShipment_manual_persistsAwbAndTracking() {
        CustomerOrder order = packedConfirmedPaid();
        when(customerOrderRepository.findOneByOrderNumber("MNM-1")).thenReturn(Optional.of(order));

        CustomerOrder result = orderShipmentService.bookShipment(
            "MNM-1",
            ReturnLogisticsMode.MANUAL,
            "Delhivery",
            "AWB-99",
            "https://track/AWB-99",
            "https://label/AWB-99",
            false,
            null
        );

        assertThat(result.getTrackingNumber()).isEqualTo("AWB-99");
        assertThat(result.getTrackingCarrier()).isEqualTo("Delhivery");
        assertThat(result.getTrackingUrl()).isEqualTo("https://track/AWB-99");
        assertThat(result.getShipmentLabelUrl()).isEqualTo("https://label/AWB-99");
        assertThat(result.getShipmentLogisticsMode()).isEqualTo(ReturnLogisticsMode.MANUAL);
        assertThat(result.getShipmentDate()).isNotBlank();
        verify(orderEventService).syncLifecycleEvents(result);
    }

    @Test
    void bookShipment_xpressBees_rejectsWhenPackageIncomplete() {
        when(customerOrderRepository.findOneByOrderNumber("MNM-1")).thenReturn(Optional.of(confirmedPaid()));

        assertThatThrownBy(() ->
            orderShipmentService.bookShipment("MNM-1", ReturnLogisticsMode.XPRESSBEES, null, null, null, null, false, null)
        )
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
            .hasMessageContaining("Create a package");
    }

    @Test
    void bookShipment_manual_rejectsWhenPackageIncomplete() {
        when(customerOrderRepository.findOneByOrderNumber("MNM-1")).thenReturn(Optional.of(confirmedPaid()));

        assertThatThrownBy(() ->
            orderShipmentService.bookShipment("MNM-1", ReturnLogisticsMode.MANUAL, "Delhivery", "AWB-99", null, null, false, null)
        )
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
            .hasMessageContaining("Create a package");
    }

    @Test
    void bookShipment_xpressBees_persistsCarrierResult() throws Exception {
        CustomerOrder order = packedConfirmedPaid();
        when(customerOrderRepository.findOneByOrderNumber("MNM-1")).thenReturn(Optional.of(order));
        when(xpressBeesClient.isForwardShipmentConfigured()).thenReturn(true);
        when(xpressBeesClient.createForwardShipment(any())).thenReturn(
            new XpressBeesClient.ForwardShipmentResult("XB-1", "https://track/XB-1", "https://label/XB-1", "REQ-1", "ok")
        );
        when(xpressBeesClient.buildTrackingUrl("XB-1")).thenReturn("https://track/XB-1");
        when(orderItemRepository.findAllByOrderId(10L)).thenReturn(
            List.of(new OrderItem().productName("Tee").variantSku("TEE-1").qty(1).price(BigDecimal.TEN))
        );

        CustomerOrder result = orderShipmentService.bookShipment(
            "MNM-1",
            ReturnLogisticsMode.XPRESSBEES,
            null,
            null,
            null,
            null,
            false,
            null
        );

        assertThat(result.getTrackingNumber()).isEqualTo("XB-1");
        assertThat(result.getTrackingCarrier()).isEqualTo("XpressBees");
        assertThat(result.getShipmentLabelUrl()).isEqualTo("https://label/XB-1");
        assertThat(result.getShipmentProviderRequestId()).isEqualTo("REQ-1");
        assertThat(result.getShipmentLogisticsMode()).isEqualTo(ReturnLogisticsMode.XPRESSBEES);
        assertThat(result.getShipmentCarrierStatus()).isEqualTo("booked");
        assertThat(result.getShipmentDate()).isNull();

        ArgumentCaptor<XpressBeesClient.ForwardShipmentRequest> captor = ArgumentCaptor.forClass(
            XpressBeesClient.ForwardShipmentRequest.class
        );
        verify(xpressBeesClient).createForwardShipment(captor.capture());
        assertThat(captor.getValue().paymentType()).isEqualTo("prepaid");
        assertThat(captor.getValue().caseReference()).isEqualTo("MNM-1");
        assertThat(captor.getValue().orderAmount()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(captor.getValue().collectableAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void bookShipment_manual_persistsPackageTypeFromShipmentStep() {
        CustomerOrder order = packedConfirmedPaid();
        when(customerOrderRepository.findOneByOrderNumber("MNM-1")).thenReturn(Optional.of(order));

        CustomerOrder result = orderShipmentService.bookShipment(
            "MNM-1",
            ReturnLogisticsMode.MANUAL,
            "Delhivery",
            "AWB-99",
            null,
            null,
            false,
            null,
            null,
            null,
            "envelope"
        );

        assertThat(result.getPackageType()).isEqualTo(PackageType.ENVELOPE);
    }

    @Test
    void bookShipment_xpressBees_appliesSelectedCourierWithoutRepacking() throws Exception {
        CustomerOrder order = packedConfirmedPaid();
        order.setPackageCourierId("old-courier");
        when(customerOrderRepository.findOneByOrderNumber("MNM-1")).thenReturn(Optional.of(order));
        when(xpressBeesClient.isForwardShipmentConfigured()).thenReturn(true);
        when(xpressBeesClient.createForwardShipment(any())).thenReturn(
            new XpressBeesClient.ForwardShipmentResult("XB-1", "https://track/XB-1", "https://label/XB-1", "REQ-1", "ok")
        );
        when(xpressBeesClient.buildTrackingUrl("XB-1")).thenReturn("https://track/XB-1");
        when(orderItemRepository.findAllByOrderId(10L)).thenReturn(
            List.of(new OrderItem().productName("Tee").variantSku("TEE-1").qty(1).price(BigDecimal.TEN))
        );

        orderShipmentService.bookShipment(
            "MNM-1",
            ReturnLogisticsMode.XPRESSBEES,
            null,
            null,
            null,
            null,
            false,
            null,
            "xb-2",
            "Xpressbees 2 K.G"
        );

        ArgumentCaptor<XpressBeesClient.ForwardShipmentRequest> captor = ArgumentCaptor.forClass(
            XpressBeesClient.ForwardShipmentRequest.class
        );
        verify(xpressBeesClient).createForwardShipment(captor.capture());
        assertThat(captor.getValue().courierId()).isEqualTo("xb-2");
        assertThat(order.getPackageCourierId()).isEqualTo("xb-2");
        assertThat(order.getPackageCourierName()).isEqualTo("Xpressbees 2 K.G");
    }

    @Test
    void bookShipment_xpressBees_usesSavedPackageDimensions() throws Exception {
        CustomerOrder order = packedConfirmedPaid()
            .packageWeightGrams(750)
            .packageLengthCm(20)
            .packageBreadthCm(15)
            .packageHeightCm(10)
            .packageCourierId("2");
        when(customerOrderRepository.findOneByOrderNumber("MNM-1")).thenReturn(Optional.of(order));
        when(xpressBeesClient.isForwardShipmentConfigured()).thenReturn(true);
        when(xpressBeesClient.createForwardShipment(any())).thenReturn(
            new XpressBeesClient.ForwardShipmentResult("XB-1", "https://track/XB-1", null, "REQ-1", "ok")
        );
        when(xpressBeesClient.buildTrackingUrl("XB-1")).thenReturn("https://track/XB-1");

        orderShipmentService.bookShipment("MNM-1", ReturnLogisticsMode.XPRESSBEES, null, null, null, null, false, null);

        ArgumentCaptor<XpressBeesClient.ForwardShipmentRequest> captor = ArgumentCaptor.forClass(
            XpressBeesClient.ForwardShipmentRequest.class
        );
        verify(xpressBeesClient).createForwardShipment(captor.capture());
        assertThat(captor.getValue().packageWeightGrams()).isEqualTo(750);
        assertThat(captor.getValue().packageLengthCm()).isEqualTo(20);
        assertThat(captor.getValue().packageBreadthCm()).isEqualTo(15);
        assertThat(captor.getValue().packageHeightCm()).isEqualTo(10);
        assertThat(captor.getValue().courierId()).isEqualTo("2");
    }

    @Test
    void applyProviderStatus_outForDeliveryStaysStickyAgainstWeakerScans() {
        CustomerOrder order = confirmedPaid()
            .trackingNumber("XB-1")
            .shipmentLogisticsMode(ReturnLogisticsMode.XPRESSBEES)
            .shipmentCarrierStatus("out_for_delivery")
            .shipmentDate("2026-09-20");
        when(customerOrderRepository.save(order)).thenReturn(order);

        CustomerOrder afterHub = orderShipmentService.applyProviderStatus(order, "Reached at nearest hub");
        assertThat(afterHub.getShipmentCarrierStatus()).isEqualTo("out_for_delivery");

        CustomerOrder afterFail = orderShipmentService.applyProviderStatus(order, "undelivered");
        assertThat(afterFail.getShipmentCarrierStatus()).isEqualTo("delivery_failed");

        CustomerOrder afterReattempt = orderShipmentService.applyProviderStatus(order, "OUT FOR DELIVERY");
        assertThat(afterReattempt.getShipmentCarrierStatus()).isEqualTo("out_for_delivery");
    }

    @Test
    void applyProviderStatus_carrierCancelBeforePickupNotifiesAdminAndKeepsOrderOpen() {
        CustomerOrder order = confirmedPaid()
            .trackingNumber("XB-1")
            .shipmentLogisticsMode(ReturnLogisticsMode.XPRESSBEES)
            .shipmentCarrierStatus("booked");
        when(customerOrderRepository.save(order)).thenReturn(order);

        CustomerOrder result = orderShipmentService.applyProviderStatus(order, "Cancelled");

        assertThat(result.getShipmentCarrierStatus()).isEqualTo("carrier_cancelled");
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(result.getTrackingNumber()).isEqualTo("XB-1");
        assertThat(result.getShipmentCancelledAt()).isNull();
        verify(orderMailService).sendForwardCarrierCancelledAdminNotice(order);
    }

    @Test
    void applyProviderStatus_carrierCancelDoesNotRollBackInTransit() {
        CustomerOrder order = confirmedPaid()
            .trackingNumber("XB-1")
            .shipmentLogisticsMode(ReturnLogisticsMode.XPRESSBEES)
            .shipmentCarrierStatus("in_transit")
            .shipmentDate("2026-09-20");
        when(customerOrderRepository.save(order)).thenReturn(order);

        CustomerOrder result = orderShipmentService.applyProviderStatus(order, "cancelled");

        assertThat(result.getShipmentCarrierStatus()).isEqualTo("in_transit");
        org.mockito.Mockito.verify(orderMailService, org.mockito.Mockito.never()).sendForwardCarrierCancelledAdminNotice(any());
    }

    @Test
    void isShipmentEligible_carrierCancelledCanBeRebooked() {
        CustomerOrder order = packedConfirmedPaid()
            .trackingNumber("XB-1")
            .shipmentLogisticsMode(ReturnLogisticsMode.XPRESSBEES)
            .shipmentCarrierStatus("carrier_cancelled");

        assertThat(OrderShipmentService.isCarrierCancelledShipment(order)).isTrue();
        assertThat(OrderShipmentService.isShipmentEligible(order)).isTrue();
        assertThat(orderShipmentService.canBook(order)).isTrue();
        assertThat(orderShipmentService.canRebookCarrierCancelled(order)).isTrue();
    }

    @Test
    void bookShipment_rebookAfterCarrierCancelSkipsAlreadyBookedConflict() throws Exception {
        CustomerOrder order = packedConfirmedPaid()
            .trackingNumber("XB-OLD")
            .trackingCarrier("XpressBees")
            .shipmentLogisticsMode(ReturnLogisticsMode.XPRESSBEES)
            .shipmentCarrierStatus("carrier_cancelled");
        when(customerOrderRepository.findOneByOrderNumber("MNM-1")).thenReturn(Optional.of(order));
        when(xpressBeesClient.isForwardShipmentConfigured()).thenReturn(true);
        when(xpressBeesClient.createForwardShipment(any())).thenReturn(
            new XpressBeesClient.ForwardShipmentResult("XB-NEW", null, null, "REQ-2", "ok")
        );
        when(xpressBeesClient.buildTrackingUrl("XB-NEW")).thenReturn("https://track/XB-NEW");

        CustomerOrder result = orderShipmentService.bookShipment(
            "MNM-1",
            ReturnLogisticsMode.XPRESSBEES,
            null,
            null,
            null,
            null,
            false,
            null
        );

        org.mockito.Mockito.verify(xpressBeesClient, org.mockito.Mockito.never()).cancelForwardShipment(any(), any());
        assertThat(result.getTrackingNumber()).isEqualTo("XB-NEW");
        assertThat(result.getShipmentCarrierStatus()).isEqualTo("booked");
        assertThat(result.getShipmentCancelledAt()).isNull();
    }

    @Test
    void cancelShipment_skipsCarrierApiWhenAlreadyCarrierCancelled() {
        CustomerOrder order = confirmedPaid()
            .trackingNumber("XB-1")
            .trackingCarrier("XpressBees")
            .shipmentLogisticsMode(ReturnLogisticsMode.XPRESSBEES)
            .shipmentCarrierStatus("carrier_cancelled");
        when(customerOrderRepository.findOneByOrderNumber("MNM-1")).thenReturn(Optional.of(order));

        CustomerOrder result = orderShipmentService.cancelShipment(
            "MNM-1",
            ReturnLogisticsMode.XPRESSBEES,
            "Rebooking after courier void",
            "admin"
        );

        org.mockito.Mockito.verifyNoInteractions(xpressBeesClient);
        assertThat(result.getTrackingNumber()).isNull();
        assertThat(result.getShipmentCancelledAt()).isNotNull();
    }

    @Test
    void applyProviderStatus_pickupSetsShipmentDateWithoutFulfilling() {
        CustomerOrder order = confirmedPaid()
            .trackingNumber("XB-1")
            .shipmentLogisticsMode(ReturnLogisticsMode.XPRESSBEES)
            .shipmentCarrierStatus("booked");
        when(customerOrderRepository.save(order)).thenReturn(order);

        CustomerOrder result = orderShipmentService.applyProviderStatus(order, "Picked Up");

        assertThat(result.getShipmentCarrierStatus()).isEqualTo("in_transit");
        assertThat(result.getShipmentDate()).isNotBlank();
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(orderEventService).syncLifecycleEvents(order);
    }

    @Test
    void applyProviderStatus_deliveredMarksFulfilled() {
        CustomerOrder order = confirmedPaid()
            .trackingNumber("XB-1")
            .shipmentLogisticsMode(ReturnLogisticsMode.XPRESSBEES)
            .shipmentCarrierStatus("in_transit");
        when(customerOrderRepository.save(order)).thenReturn(order);

        CustomerOrder result = orderShipmentService.applyProviderStatus(order, "delivered");

        assertThat(result.getStatus()).isEqualTo(OrderStatus.FULFILLED);
        assertThat(result.getShipmentDeliveredAt()).isNotNull();
    }

    @Test
    void applyProviderStatus_duplicateDeliveryDoesNotExtendReturnWindow() {
        Instant firstDelivery = Instant.parse("2026-09-27T14:10:00Z");
        CustomerOrder order = confirmedPaid()
            .status(OrderStatus.FULFILLED)
            .trackingNumber("XB-1")
            .shipmentLogisticsMode(ReturnLogisticsMode.XPRESSBEES)
            .shipmentCarrierStatus("delivered")
            .shipmentDeliveredAt(firstDelivery);
        when(customerOrderRepository.save(order)).thenReturn(order);

        CustomerOrder result = orderShipmentService.applyProviderStatus(order, "delivered");

        assertThat(result.getShipmentDeliveredAt()).isEqualTo(firstDelivery);
    }

    @Test
    void applyProviderStatus_deliveredDoesNotFulfillCancelledOrder() {
        CustomerOrder order = confirmedPaid()
            .status(OrderStatus.CANCELLED)
            .trackingNumber("XB-1")
            .shipmentLogisticsMode(ReturnLogisticsMode.XPRESSBEES)
            .shipmentCarrierStatus("in_transit");

        CustomerOrder result = orderShipmentService.applyProviderStatus(order, "delivered");

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(result.getShipmentDeliveredAt()).isNull();
        assertThat(result.getShipmentCarrierStatus()).isEqualTo("in_transit");
    }

    @Test
    void refreshExpressBeesTracking_appliesWhenCarrierStatusChanges() {
        CustomerOrder order = confirmedPaid()
            .trackingNumber("XB-1")
            .shipmentLogisticsMode(ReturnLogisticsMode.XPRESSBEES)
            .shipmentCarrierStatus("booked");
        when(xpressBeesClient.fetchForwardTracking("XB-1")).thenReturn(
            Optional.of(new XpressBeesClient.ForwardTrackingResult("XB-1", "Picked Up", "Collected"))
        );
        when(customerOrderRepository.save(order)).thenReturn(order);

        CustomerOrder result = orderShipmentService.refreshExpressBeesTracking(order);

        assertThat(result.getShipmentCarrierStatus()).isEqualTo("in_transit");
        assertThat(result.getShipmentDate()).isNotBlank();
        verify(orderEventService).syncLifecycleEvents(order);
    }

    @Test
    void refreshExpressBeesTracking_skipsPersistWhenStatusUnchanged() {
        CustomerOrder order = confirmedPaid()
            .trackingNumber("XB-1")
            .shipmentLogisticsMode(ReturnLogisticsMode.XPRESSBEES)
            .shipmentCarrierStatus("booked");
        when(xpressBeesClient.fetchForwardTracking("XB-1")).thenReturn(
            Optional.of(new XpressBeesClient.ForwardTrackingResult("XB-1", "booked", "Label generated"))
        );

        CustomerOrder result = orderShipmentService.refreshExpressBeesTracking(order);

        assertThat(result.getShipmentDate()).isNull();
        org.mockito.Mockito.verify(customerOrderRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void bookShipment_xpressBeesCod_sendsCodPaymentType() throws Exception {
        CustomerOrder order = packedConfirmedPaid().paymentMethod("cod").paymentStatus(PaymentStatus.PENDING);
        when(customerOrderRepository.findOneByOrderNumber("MNM-1")).thenReturn(Optional.of(order));
        when(xpressBeesClient.isForwardShipmentConfigured()).thenReturn(true);
        when(xpressBeesClient.createForwardShipment(any())).thenReturn(
            new XpressBeesClient.ForwardShipmentResult("XB-COD", null, null, "REQ-COD", "ok")
        );
        when(xpressBeesClient.buildTrackingUrl("XB-COD")).thenReturn("https://www.xpressbees.com/track/?awb=XB-COD");

        orderShipmentService.bookShipment("MNM-1", ReturnLogisticsMode.XPRESSBEES, null, null, null, null, false, null);

        ArgumentCaptor<XpressBeesClient.ForwardShipmentRequest> captor = ArgumentCaptor.forClass(
            XpressBeesClient.ForwardShipmentRequest.class
        );
        verify(xpressBeesClient).createForwardShipment(captor.capture());
        assertThat(captor.getValue().paymentType()).isEqualTo("cod");
        assertThat(captor.getValue().collectableAmount()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(captor.getValue().orderAmount()).isEqualByComparingTo(BigDecimal.TEN);
    }

    @Test
    void bookShipment_xpressBeesNotConfigured_throwsServiceUnavailable() {
        when(customerOrderRepository.findOneByOrderNumber("MNM-1")).thenReturn(Optional.of(packedConfirmedPaid()));
        when(xpressBeesClient.isForwardShipmentConfigured()).thenReturn(false);

        assertThatThrownBy(() ->
            orderShipmentService.bookShipment("MNM-1", ReturnLogisticsMode.XPRESSBEES, null, null, null, null, false, null)
        )
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void bookShipment_manualWithoutAwb_throwsBadRequest() {
        when(customerOrderRepository.findOneByOrderNumber("MNM-1")).thenReturn(Optional.of(packedConfirmedPaid()));

        assertThatThrownBy(() ->
            orderShipmentService.bookShipment("MNM-1", ReturnLogisticsMode.MANUAL, "Delhivery", "", null, null, false, null)
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("AWB");
    }

    @Test
    void bookShipment_alreadyBooked_throwsConflict() {
        CustomerOrder order = confirmedPaid().trackingNumber("AWB-EXISTING");
        when(customerOrderRepository.findOneByOrderNumber("MNM-1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() ->
            orderShipmentService.bookShipment("MNM-1", ReturnLogisticsMode.MANUAL, "Delhivery", "AWB-2", null, null, false, null)
        )
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void bookShipment_cancelled_throwsBadRequest() {
        CustomerOrder order = confirmedPaid().status(OrderStatus.CANCELLED);
        when(customerOrderRepository.findOneByOrderNumber("MNM-1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() ->
            orderShipmentService.bookShipment("MNM-1", ReturnLogisticsMode.MANUAL, "Delhivery", "AWB-2", null, null, false, null)
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Cancelled");
    }

    @Test
    void cancelShipment_manualClearsAwbAndRecordsAudit() {
        CustomerOrder order = confirmedPaid()
            .trackingNumber("AWB-1")
            .trackingCarrier("Manual")
            .shipmentLogisticsMode(ReturnLogisticsMode.MANUAL);
        when(customerOrderRepository.findOneByOrderNumber("MNM-1")).thenReturn(Optional.of(order));

        CustomerOrder result = orderShipmentService.cancelShipment(
            "MNM-1",
            ReturnLogisticsMode.MANUAL,
            "Carrier confirmed cancellation",
            "admin"
        );

        assertThat(result.getTrackingNumber()).isNull();
        assertThat(result.getShipmentCarrierStatus()).isEqualTo("cancelled");
        assertThat(result.getShipmentCancelledAt()).isNotNull();
        assertThat(result.getShipmentCancelledBy()).isEqualTo("admin");
    }

    @Test
    void cancelShipment_xpressBeesCallsCarrierBeforeClearingAwb() throws Exception {
        CustomerOrder order = confirmedPaid()
            .trackingNumber("XB-1")
            .trackingCarrier("XpressBees")
            .shipmentLogisticsMode(ReturnLogisticsMode.XPRESSBEES);
        when(customerOrderRepository.findOneByOrderNumber("MNM-1")).thenReturn(Optional.of(order));
        when(xpressBeesClient.cancelForwardShipment("XB-1", "MNM-1")).thenReturn(
            new XpressBeesClient.CancelShipmentResult("cancel-1", "accepted")
        );

        CustomerOrder result = orderShipmentService.cancelShipment(
            "MNM-1",
            ReturnLogisticsMode.XPRESSBEES,
            "Customer cancellation approved",
            "admin"
        );

        verify(xpressBeesClient).cancelForwardShipment("XB-1", "MNM-1");
        assertThat(result.getTrackingNumber()).isNull();
    }

    @Test
    void canBookWithOverride_andCanBookExpressBeesWithOverride() {
        CustomerOrder order = packedConfirmedPaid();
        when(orderCancellationService.hasPendingRequest(order.getId())).thenReturn(true);

        assertThat(orderShipmentService.canBookWithOverride(order)).isTrue();
        assertThat(orderShipmentService.canBookExpressBeesWithOverride(order)).isTrue();
    }

    @Test
    void cancelShipment_validations() throws Exception {
        CustomerOrder order = confirmedPaid();
        when(customerOrderRepository.findOneByOrderNumber("MNM-1")).thenReturn(Optional.of(order));

        // Short reason (< 5 chars)
        assertThatThrownBy(() -> orderShipmentService.cancelShipment("MNM-1", ReturnLogisticsMode.MANUAL, "bad", "admin"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));

        // No tracking number
        assertThatThrownBy(() -> orderShipmentService.cancelShipment("MNM-1", ReturnLogisticsMode.MANUAL, "Valid reason", "admin"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(409));

        // Already delivered
        order.setTrackingNumber("TRK-1");
        order.setStatus(OrderStatus.FULFILLED);
        assertThatThrownBy(() -> orderShipmentService.cancelShipment("MNM-1", ReturnLogisticsMode.MANUAL, "Valid reason", "admin"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(409));

        // Invalid mode (SELF_SHIP)
        order.setStatus(OrderStatus.CONFIRMED);
        assertThatThrownBy(() -> orderShipmentService.cancelShipment("MNM-1", ReturnLogisticsMode.SELF_SHIP, "Valid reason", "admin"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));

        // ExpressBees mode but order was booked manual
        order.setShipmentLogisticsMode(ReturnLogisticsMode.MANUAL);
        assertThatThrownBy(() -> orderShipmentService.cancelShipment("MNM-1", ReturnLogisticsMode.XPRESSBEES, "Valid reason", "admin"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));

        // ExpressBees API exception on cancel
        order.setShipmentLogisticsMode(ReturnLogisticsMode.XPRESSBEES);
        org.mockito.Mockito.doThrow(new XpressBeesClient.XpressBeesApiException("carrier down"))
            .when(xpressBeesClient).cancelForwardShipment("TRK-1", "MNM-1");
        assertThatThrownBy(() -> orderShipmentService.cancelShipment("MNM-1", ReturnLogisticsMode.XPRESSBEES, "Valid reason", "admin"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(502));
    }

    @Test
    void bookShipment_validations() {
        CustomerOrder order = packedConfirmedPaid();
        when(customerOrderRepository.findOneByOrderNumber("MNM-1")).thenReturn(Optional.of(order));

        // Mode is null
        assertThatThrownBy(() -> orderShipmentService.bookShipment("MNM-1", null, null, null, null, null, false, null))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));

        // Mode is SELF_SHIP
        assertThatThrownBy(() -> orderShipmentService.bookShipment("MNM-1", ReturnLogisticsMode.SELF_SHIP, null, null, null, null, false, null))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));

        // Cancelled order cannot be booked
        order.setStatus(OrderStatus.CANCELLED);
        assertThatThrownBy(() -> orderShipmentService.bookShipment("MNM-1", ReturnLogisticsMode.MANUAL, "Carrier", "AWB-1", null, null, false, null))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));

        // Fulfilled order cannot be booked
        order.setStatus(OrderStatus.FULFILLED);
        assertThatThrownBy(() -> orderShipmentService.bookShipment("MNM-1", ReturnLogisticsMode.MANUAL, "Carrier", "AWB-1", null, null, false, null))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));

        // Already has tracking number
        order.setStatus(OrderStatus.CONFIRMED);
        order.setTrackingNumber("EXISTING-AWB");
        assertThatThrownBy(() -> orderShipmentService.bookShipment("MNM-1", ReturnLogisticsMode.MANUAL, "Carrier", "AWB-1", null, null, false, null))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(409));
    }

    @Test
    void isShipmentEligible_edgeCases() {
        assertThat(OrderShipmentService.isShipmentEligible(null)).isFalse();

        CustomerOrder order = confirmedPaid();
        assertThat(OrderShipmentService.isShipmentEligible(order)).isTrue();

        order.setStatus(OrderStatus.CANCELLED);
        assertThat(OrderShipmentService.isShipmentEligible(order)).isFalse();

        order.setStatus(OrderStatus.HAND_WRAPPED);
        assertThat(OrderShipmentService.isShipmentEligible(order)).isTrue();

        order.setPaymentStatus(PaymentStatus.PENDING);
        assertThat(OrderShipmentService.isShipmentEligible(order)).isFalse();

        // COD is eligible even when pending
        order.setPaymentMethod("cod");
        assertThat(OrderShipmentService.isShipmentEligible(order)).isTrue();
    }

    private CustomerOrder confirmedPaid() {
        return new CustomerOrder()
            .id(10L)
            .orderNumber("MNM-1")
            .status(OrderStatus.CONFIRMED)
            .paymentMethod("online")
            .paymentStatus(PaymentStatus.PAID)
            .total(BigDecimal.TEN)
            .placedAt(Instant.parse("2026-09-01T10:00:00Z"))
            .shippingAddressJson(
                "{\"full_name\":\"Jane Doe\",\"phone\":\"9876543210\",\"line1\":\"12 MG Road\",\"city\":\"Bengaluru\",\"state\":\"Karnataka\",\"pincode\":\"560001\"}"
            );
    }

    private CustomerOrder packedConfirmedPaid() {
        return confirmedPaid()
            .packedAt(Instant.parse("2026-09-18T10:00:00Z"))
            .packageWeightGrams(500)
            .packageLengthCm(10)
            .packageBreadthCm(10)
            .packageHeightCm(10)
            .packageChargeableWeightGrams(500);
    }
}
