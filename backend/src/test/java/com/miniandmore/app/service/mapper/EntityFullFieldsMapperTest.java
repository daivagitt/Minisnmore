package com.miniandmore.app.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.domain.Category;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.Product;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.domain.enumeration.LedgerlySyncStatus;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class EntityFullFieldsMapperTest {

    @Test
    void productMapper_roundTripsAllFields() {
        ProductMapper mapper = MapperTestSupport.productMapper();
        Category category = new Category().id(9L).name("Dresses").slug("dresses");
        Product product = new Product()
            .id(1L)
            .slug("dress")
            .name("Summer Dress")
            .sku("SKU-1")
            .fabricType("Cotton")
            .description("Soft dress")
            .story("Handmade")
            .price(BigDecimal.valueOf(999))
            .compareAtPrice(BigDecimal.valueOf(1299))
            .currency("INR")
            .ageMinMonths(6)
            .ageMaxMonths(24)
            .sizesJson("[\"S\",\"M\"]")
            .sizeNotesJson("{\"6-12m\":\"This size has grippers\"}")
            .colorsJson("[\"Pink\"]")
            .imagesJson("[\"img.jpg\"]")
            .badge("New")
            .detailsKicker("Organic Cotton · Newborn")
            .stock(10)
            .sortOrder(1)
            .featured(true)
            .trending(false)
            .newArrival(true)
            .bestSeller(false)
            .active(true)
            .ledgerlyItemId("ledgerly-1")
            .ledgerlySyncStatus(LedgerlySyncStatus.SYNCED)
            .ledgerlyLastSyncedAt(Instant.parse("2026-01-01T00:00:00Z"))
            .ledgerlySyncError(null)
            .category(category);

        var dto = mapper.toDto(product);
        var roundTrip = mapper.toEntity(dto);

        assertThat(roundTrip.getSlug()).isEqualTo("dress");
        assertThat(roundTrip.getPrice()).isEqualByComparingTo("999");
        assertThat(roundTrip.getSizeNotesJson()).isEqualTo("{\"6-12m\":\"This size has grippers\"}");
        assertThat(roundTrip.getDetailsKicker()).isEqualTo("Organic Cotton · Newborn");
        assertThat(roundTrip.getFeatured()).isTrue();
        assertThat(roundTrip.getCategory()).isNotNull();
        assertThat(roundTrip.getCategory().getName()).isEqualTo("Dresses");
    }

    @Test
    void customerOrderMapper_roundTripsExtendedFields() {
        CustomerOrderMapper mapper = new CustomerOrderMapperImpl();
        CustomerOrder order = new CustomerOrder()
            .id(5L)
            .userLogin("user1")
            .orderNumber("MNM-001")
            .status(OrderStatus.CONFIRMED)
            .paymentStatus(PaymentStatus.PAID)
            .subtotal(BigDecimal.valueOf(900))
            .discount(BigDecimal.TEN)
            .shipping(BigDecimal.valueOf(99))
            .tax(BigDecimal.valueOf(45))
            .donation(BigDecimal.ZERO)
            .total(BigDecimal.valueOf(1034))
            .couponCode("SAVE10")
            .shippingAddressJson("{\"city\":\"City\"}")
            .notes("Gift wrap")
            .trackingNumber("TRACK1")
            .shipmentDate("2026-01-15")
            .ledgerlySalesOrderId("SO-1")
            .ledgerlyInvoiceId("INV-1")
            .ledgerlySyncStatus(LedgerlySyncStatus.SYNCED)
            .ledgerlySyncError(null)
            .ledgerlyLastSyncedAt(Instant.parse("2026-01-02T00:00:00Z"))
            .ledgerlyTrackingLastPulledAt(Instant.parse("2026-01-03T00:00:00Z"))
            .inventoryReserved(true)
            .inventoryReservedUntil(Instant.parse("2026-01-01T01:00:00Z"))
            .inventoryReleasedAt(null)
            .razorpayOrderId("order_1")
            .razorpayPaymentId("pay_1")
            .razorpaySignature("sig");

        var dto = mapper.toDto(order);
        var roundTrip = mapper.toEntity(dto);

        assertThat(roundTrip.getOrderNumber()).isEqualTo("MNM-001");
        assertThat(roundTrip.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(roundTrip.getTotal()).isEqualByComparingTo("1034");
        assertThat(roundTrip.getNotes()).isEqualTo("Gift wrap");
    }
}
