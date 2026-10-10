package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
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
import com.miniandmore.app.repository.CustomerOrderRepository;
import com.miniandmore.app.repository.OrderItemRepository;
import com.miniandmore.app.repository.StoreAddressRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class OrderPackageServiceTest {

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
    private StoreAddressRepository storeAddressRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private OrderPackageService orderPackageService;

    @BeforeEach
    void setUp() {
        ApplicationProperties.XpressBees xpressBees = new ApplicationProperties.XpressBees();
        xpressBees.setDefaultWeightGrams(500);
        xpressBees.setVolumetricDivisor(5000);
        org.mockito.Mockito.lenient().when(applicationProperties.getXpressbees()).thenReturn(xpressBees);
        org.mockito.Mockito.lenient().when(orderItemRepository.findAllByOrderId(any())).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.lenient().when(orderCancellationService.hasPendingRequest(any())).thenReturn(false);
    }

    @Test
    void volumetricWeightGrams_usesIndiaDivisor() {
        assertThat(OrderPackageService.volumetricWeightGrams(10, 10, 10, 5000)).isEqualTo(200);
        assertThat(OrderPackageService.volumetricWeightGrams(40, 30, 25, 5000)).isEqualTo(6000);
    }

    @Test
    void chargeableWeightGrams_picksTheHigherValue() {
        assertThat(OrderPackageService.chargeableWeightGrams(500, 200)).isEqualTo(500);
        assertThat(OrderPackageService.chargeableWeightGrams(400, 1750)).isEqualTo(1750);
    }

    @Test
    void previewRates_usesDefaultWeightAndMarksPreferredCourier() {
        CustomerOrder order = confirmedPaid();
        when(customerOrderRepository.findOneByOrderNumber("MNM-1")).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrderId(10L)).thenReturn(
            List.of(new OrderItem().qty(2).productName("Tee").productId("p1").price(BigDecimal.TEN))
        );
        when(
            xpressBeesClient.listForwardCourierOptions(
                eq("560001"),
                eq(1000),
                eq(10),
                eq(10),
                eq(10),
                eq("prepaid"),
                eq(BigDecimal.TEN),
                any()
            )
        )
            .thenReturn(
            List.of(
                new XpressBeesClient.CourierOption("8", "Express Reverse", 3, "3 days", null, false),
                new XpressBeesClient.CourierOption(
                    "2",
                    "Xpressbees 1 K.G",
                    2,
                    "2 days",
                    new BigDecimal("48.00"),
                    true
                )
            )
        );

        Map<String, Object> rates = orderPackageService.previewRates("MNM-1", null, 10, 10, 10);

        assertThat(rates.get("actual_weight_grams")).isEqualTo(1000);
        assertThat(rates.get("volumetric_weight_grams")).isEqualTo(200);
        assertThat(rates.get("chargeable_weight_grams")).isEqualTo(1000);
        assertThat(rates.get("preferred_courier_name")).isEqualTo("Xpressbees 1 K.G");
        assertThat(rates.get("preferred_courier_id")).isEqualTo("2");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> couriers = (List<Map<String, Object>>) rates.get("couriers");
        assertThat(couriers).hasSize(2);
        assertThat((BigDecimal) couriers.get(1).get("rate")).isEqualByComparingTo(new BigDecimal("48.00"));
    }

    @Test
    void completePackage_setsHandWrappedAndPersistsDimensions() {
        CustomerOrder order = confirmedPaid();
        when(customerOrderRepository.findOneByOrderNumber("MNM-1")).thenReturn(Optional.of(order));
        when(
            xpressBeesClient.listForwardCourierOptions(eq("560001"), anyInt(), anyInt(), anyInt(), anyInt(), any(), any(), any())
        )
            .thenReturn(
            List.of(new XpressBeesClient.CourierOption("1", "Surface Xpressbees 0.5 K.G", 3, "3 days", null, true))
        );

        CustomerOrder saved = orderPackageService.completePackage("MNM-1", 500, 12, 10, 8, "1", "Surface Xpressbees 0.5 K.G");

        assertThat(saved.getStatus()).isEqualTo(OrderStatus.HAND_WRAPPED);
        assertThat(saved.getPackageWeightGrams()).isEqualTo(500);
        assertThat(saved.getPackageLengthCm()).isEqualTo(12);
        assertThat(saved.getPackageBreadthCm()).isEqualTo(10);
        assertThat(saved.getPackageHeightCm()).isEqualTo(8);
        assertThat(saved.getPackageChargeableWeightGrams()).isEqualTo(500);
        assertThat(saved.getPackageCourierId()).isEqualTo("1");
        assertThat(saved.getPackageType()).isEqualTo(PackageType.CUSTOM);
        assertThat(saved.getPackedAt()).isNotNull();
        verify(orderEventService).syncLifecycleEvents(saved);
    }

    @Test
    void completePackage_rejectsUnpaidOnlineOrder() {
        CustomerOrder order = confirmedPaid().paymentStatus(PaymentStatus.PENDING);
        when(customerOrderRepository.findOneByOrderNumber("MNM-1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderPackageService.completePackage("MNM-1", 500, 10, 10, 10, null, null))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void previewRates_rejectsCodOrderWithoutTotal() {
        CustomerOrder order = confirmedPaid().paymentMethod("cod").total(null);
        when(customerOrderRepository.findOneByOrderNumber("MNM-1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderPackageService.previewRates("MNM-1", 500, 10, 10, 10))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST))
            .hasMessageContaining("COD");
    }

    @Test
    void previewRates_rejectsMissingPincode() {
        CustomerOrder order = confirmedPaid().shippingAddressJson("{\"full_name\":\"Jane Doe\",\"city\":\"Bengaluru\"}");
        when(customerOrderRepository.findOneByOrderNumber("MNM-1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderPackageService.previewRates("MNM-1", 500, 10, 10, 10))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST))
            .hasMessageContaining("pincode");
    }

    @Test
    void completePackage_allowsCodWithoutPayment() {
        CustomerOrder order = confirmedPaid().paymentMethod("cod").paymentStatus(PaymentStatus.PENDING);
        when(customerOrderRepository.findOneByOrderNumber("MNM-1")).thenReturn(Optional.of(order));
        when(
            xpressBeesClient.listForwardCourierOptions(eq("560001"), anyInt(), anyInt(), anyInt(), anyInt(), any(), any(), any())
        )
            .thenReturn(
            List.of(new XpressBeesClient.CourierOption("1", "Surface Xpressbees 0.5 K.G", 3, "3 days", null, true))
        );

        CustomerOrder saved = orderPackageService.completePackage("MNM-1", 500, 10, 10, 10, null, null);

        assertThat(saved.getStatus()).isEqualTo(OrderStatus.HAND_WRAPPED);
        assertThat(saved.getPackageCourierName()).isEqualTo("Surface Xpressbees 0.5 K.G");
        assertThat(saved.getPackageCourierId()).isEqualTo("1");
    }

    @Test
    void completePackage_savesWithoutCourierWhenRatesUnavailable() {
        CustomerOrder order = confirmedPaid();
        when(customerOrderRepository.findOneByOrderNumber("MNM-1")).thenReturn(Optional.of(order));
        when(
            xpressBeesClient.listForwardCourierOptions(eq("560001"), anyInt(), anyInt(), anyInt(), anyInt(), any(), any(), any())
        )
            .thenReturn(List.of());

        CustomerOrder saved = orderPackageService.completePackage("MNM-1", 500, 10, 10, 10, null, null);

        assertThat(saved.getStatus()).isEqualTo(OrderStatus.HAND_WRAPPED);
        assertThat(saved.getPackedAt()).isNotNull();
        assertThat(saved.getPackageCourierId()).isNull();
        assertThat(saved.getPackageCourierName()).isNull();
    }

    @Test
    void completePackage_rejectsUnknownCourierId() {
        CustomerOrder order = confirmedPaid();
        when(customerOrderRepository.findOneByOrderNumber("MNM-1")).thenReturn(Optional.of(order));
        when(
            xpressBeesClient.listForwardCourierOptions(eq("560001"), anyInt(), anyInt(), anyInt(), anyInt(), any(), any(), any())
        )
            .thenReturn(
            List.of(new XpressBeesClient.CourierOption("1", "Surface Xpressbees 0.5 K.G", 3, "3 days", null, true))
        );

        assertThatThrownBy(() -> orderPackageService.completePackage("MNM-1", 500, 10, 10, 10, "99", "Unknown"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void completePackage_persistsZohoEnvelopeType() {
        CustomerOrder order = confirmedPaid();
        when(customerOrderRepository.findOneByOrderNumber("MNM-1")).thenReturn(Optional.of(order));
        when(
            xpressBeesClient.listForwardCourierOptions(eq("560001"), anyInt(), anyInt(), anyInt(), anyInt(), any(), any(), any())
        )
            .thenReturn(
            List.of(new XpressBeesClient.CourierOption("1", "Surface Xpressbees 0.5 K.G", 3, "3 days", null, true))
        );

        CustomerOrder saved = orderPackageService.completePackage(
            "MNM-1",
            250,
            32,
            24,
            2,
            "1",
            "Surface Xpressbees 0.5 K.G",
            "envelope"
        );

        assertThat(saved.getPackageType()).isEqualTo(PackageType.ENVELOPE);
        assertThat(saved.getPackageLengthCm()).isEqualTo(32);
        assertThat(saved.getPackageHeightCm()).isEqualTo(2);
    }

    @Test
    void completePackage_rejectsUnknownPackageType() {
        CustomerOrder order = confirmedPaid();
        when(customerOrderRepository.findOneByOrderNumber("MNM-1")).thenReturn(Optional.of(order));

        assertThatThrownBy(
            () -> orderPackageService.completePackage("MNM-1", 500, 10, 10, 10, null, null, "pallet")
        )
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST))
            .hasMessageContaining("Package type");
    }

    @Test
    void canCompletePackage_blocksWhenCancellationPending() {
        CustomerOrder order = confirmedPaid();
        when(orderCancellationService.hasPendingRequest(10L)).thenReturn(true);

        assertThat(orderPackageService.canCompletePackage(order)).isFalse();
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
}
