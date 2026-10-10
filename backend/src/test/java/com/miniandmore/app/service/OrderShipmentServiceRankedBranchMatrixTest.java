package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.client.XpressBeesClient;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.ReturnLogisticsMode;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.OrderItemRepository;
import com.miniandmore.app.repository.StoreAddressRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderShipmentServiceRankedBranchMatrixTest {

    @Mock
    private CustomerOrderRepository orders;

    @Mock
    private OrderItemRepository items;

    @Mock
    private XpressBeesClient client;

    @Mock
    private ApplicationProperties properties;

    @Mock
    private OrderEventService events;

    @Mock
    private OrderCancellationService cancellations;

    @Mock
    private OrderMailService mail;

    @Mock
    private StoreAddressRepository storeAddresses;

    private OrderShipmentService service;

    @BeforeEach
    void setUp() {
        service = new OrderShipmentService(
            orders,
            items,
            client,
            properties,
            events,
            cancellations,
            mail,
            new ObjectMapper(),
            storeAddresses
        );
    }

    @ParameterizedTest
    @MethodSource("incompletePackages")
    void isPackageCompleted_rejectsEachMissingOrInvalidComponent(CustomerOrder order) {
        assertThat(OrderShipmentService.isPackageCompleted(order)).isFalse();
    }

    private static Stream<Arguments> incompletePackages() {
        Instant packed = Instant.parse("2026-10-01T00:00:00Z");
        return Stream.of(
            Arguments.of((CustomerOrder) null),
            Arguments.of(new CustomerOrder().packageLengthCm(1).packageBreadthCm(1).packageHeightCm(1)),
            Arguments.of(new CustomerOrder().packedAt(packed).packageBreadthCm(1).packageHeightCm(1)),
            Arguments.of(new CustomerOrder().packedAt(packed).packageLengthCm(1).packageHeightCm(1)),
            Arguments.of(new CustomerOrder().packedAt(packed).packageLengthCm(1).packageBreadthCm(1)),
            Arguments.of(new CustomerOrder().packedAt(packed).packageLengthCm(0).packageBreadthCm(1).packageHeightCm(1)),
            Arguments.of(new CustomerOrder().packedAt(packed).packageLengthCm(1).packageBreadthCm(0).packageHeightCm(1)),
            Arguments.of(new CustomerOrder().packedAt(packed).packageLengthCm(1).packageBreadthCm(1).packageHeightCm(0))
        );
    }

    @ParameterizedTest
    @MethodSource("refreshNoOps")
    void refreshExpressBeesTracking_shortCircuitsInvalidStates(CustomerOrder order) {
        assertThat(service.refreshExpressBeesTracking(order)).isSameAs(order);
        verify(client, never()).fetchForwardTracking(any());
    }

    private static Stream<Arguments> refreshNoOps() {
        return Stream.of(
            Arguments.of((CustomerOrder) null),
            Arguments.of(new CustomerOrder().id(1L).shipmentLogisticsMode(ReturnLogisticsMode.MANUAL).trackingNumber("A")),
            Arguments.of(new CustomerOrder().id(2L).shipmentLogisticsMode(ReturnLogisticsMode.XPRESSBEES)),
            Arguments.of(
                cancelledShipment()
            ),
            Arguments.of(
                new CustomerOrder()
                    .id(4L)
                    .status(OrderStatus.CANCELLED)
                    .shipmentLogisticsMode(ReturnLogisticsMode.XPRESSBEES)
                    .trackingNumber("A")
            ),
            Arguments.of(
                new CustomerOrder()
                    .id(5L)
                    .status(OrderStatus.FULFILLED)
                    .shipmentLogisticsMode(ReturnLogisticsMode.XPRESSBEES)
                    .trackingNumber("A")
            )
        );
    }

    private static CustomerOrder cancelledShipment() {
        CustomerOrder order = new CustomerOrder()
            .id(3L)
            .shipmentLogisticsMode(ReturnLogisticsMode.XPRESSBEES)
            .trackingNumber("A");
        order.setShipmentCancelledAt(Instant.now());
        return order;
    }

    @Test
    void refreshExpressBeesTracking_handlesEmptyAndClientFailureAndCachesPull() {
        CustomerOrder empty = trackable(20L, "EMPTY");
        when(client.fetchForwardTracking("EMPTY")).thenReturn(Optional.empty());
        assertThat(service.refreshExpressBeesTracking(empty)).isSameAs(empty);
        assertThat(service.isExpressBeesTrackingFresh(empty)).isTrue();
        service.refreshExpressBeesTracking(empty);
        verify(client).fetchForwardTracking("EMPTY");

        CustomerOrder failed = trackable(21L, "FAIL");
        when(client.fetchForwardTracking("FAIL")).thenThrow(new RuntimeException("offline"));
        assertThat(service.refreshExpressBeesTracking(failed)).isSameAs(failed);
    }

    @Test
    void trackingFresh_rejectsNullOrderAndNullId() {
        assertThat(service.isExpressBeesTrackingFresh(null)).isFalse();
        assertThat(service.isExpressBeesTrackingFresh(new CustomerOrder())).isFalse();
    }

    private static CustomerOrder trackable(long id, String awb) {
        return new CustomerOrder()
            .id(id)
            .status(OrderStatus.CONFIRMED)
            .shipmentLogisticsMode(ReturnLogisticsMode.XPRESSBEES)
            .trackingNumber(awb);
    }
}
