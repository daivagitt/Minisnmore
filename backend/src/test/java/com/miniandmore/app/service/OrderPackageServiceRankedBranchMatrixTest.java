package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.client.XpressBeesClient;
import com.miniandmore.app.config.ApplicationProperties;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.OrderItemRepository;
import com.miniandmore.app.repository.StoreAddressRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderPackageServiceRankedBranchMatrixTest {

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
    private StoreAddressRepository storeAddresses;

    private OrderPackageService service;

    @BeforeEach
    void setUp() {
        ApplicationProperties.XpressBees config = new ApplicationProperties.XpressBees();
        config.setDefaultWeightGrams(250);
        config.setVolumetricDivisor(5000);
        org.mockito.Mockito.lenient().when(properties.getXpressbees()).thenReturn(config);
        service = new OrderPackageService(
            orders,
            items,
            client,
            properties,
            events,
            cancellations,
            new ObjectMapper(),
            storeAddresses
        );
    }

    @ParameterizedTest
    @MethodSource("ineligibleOrders")
    void eligibilityAndReason_coverEveryRankedGuard(CustomerOrder order, String reason) {
        assertThat(service.canCompletePackage(order)).isFalse();
        assertThat((String) ReflectionTestUtils.invokeMethod(service, "packageIneligibleReason", order)).isEqualTo(reason);
    }

    private static Stream<Arguments> ineligibleOrders() {
        return Stream.of(
            Arguments.of(new CustomerOrder().status(OrderStatus.CANCELLED), "Cancelled orders cannot be packed"),
            Arguments.of(new CustomerOrder().status(OrderStatus.FULFILLED), "Delivered orders cannot be packed"),
            Arguments.of(
                eligible().trackingNumber("AWB"),
                "Shipment already booked"
            ),
            Arguments.of(
                eligible().shipmentDate("2026-10-01"),
                "Shipment already booked"
            ),
            Arguments.of(new CustomerOrder().status(OrderStatus.PENDING), "Order is not ready to pack"),
            Arguments.of(
                eligible().paymentStatus(PaymentStatus.PENDING).paymentMethod("online"),
                "Order must be paid before packing"
            )
        );
    }

    @Test
    void canCompletePackage_handlesNullAndPendingCancellation() {
        assertThat(service.canCompletePackage(null)).isFalse();
        CustomerOrder order = eligible().id(9L);
        when(cancellations.hasPendingRequest(9L)).thenReturn(true);
        assertThat(service.canCompletePackage(order)).isFalse();
    }

    @Test
    void resolveActualWeight_prefersExplicitThenStoredThenCalculatedQuantity() {
        CustomerOrder order = eligible().id(10L).packageWeightGrams(700);
        assertThat((Integer) ReflectionTestUtils.invokeMethod(service, "resolveActualWeight", order, 900)).isEqualTo(900);
        assertThat((Integer) ReflectionTestUtils.invokeMethod(service, "resolveActualWeight", order, 0)).isEqualTo(700);

        order.setPackageWeightGrams(null);
        when(items.findAllByOrderId(10L)).thenReturn(
            List.of(new OrderItem().qty(2), new OrderItem().qty(null), new OrderItem().qty(0))
        );
        assertThat((Integer) ReflectionTestUtils.invokeMethod(service, "resolveActualWeight", order, (Integer) null)).isEqualTo(1000);
    }

    @Test
    void resolveActualWeight_handlesOrderWithoutIdAndNonPositiveDefaults() {
        ApplicationProperties.XpressBees config = new ApplicationProperties.XpressBees();
        config.setDefaultWeightGrams(0);
        when(properties.getXpressbees()).thenReturn(config);
        assertThat(service.defaultPackageWeightGrams(eligible())).isEqualTo(1);
    }

    @Test
    void previewRatesForDestination_addsConfiguredEmptyRatesWarning() {
        when(client.listForwardCourierOptions(any(), any(Integer.class), any(Integer.class), any(Integer.class), any(Integer.class), any(), any()))
            .thenReturn(List.of());
        when(client.isConfigured()).thenReturn(true);

        Map<String, Object> rates = service.previewRatesForDestination("560001", 500, 10, 10, 10, false, BigDecimal.TEN);

        assertThat(rates).containsKey("rates_warning");
    }

    private static CustomerOrder eligible() {
        return new CustomerOrder()
            .status(OrderStatus.CONFIRMED)
            .paymentMethod("online")
            .paymentStatus(PaymentStatus.PAID);
    }
}
