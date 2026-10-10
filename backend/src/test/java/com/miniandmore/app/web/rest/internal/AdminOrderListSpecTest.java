package com.miniandmore.app.web.rest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyChar;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.ArgumentMatchers;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.atLeastOnce;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.ZohoSyncStatus;
import java.util.List;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AdminOrderListSpecTest {

    @Test
    void parseEnum_acceptsBlankAsNull() {
        assertThat(AdminOrderListSpec.parseEnum(OrderStatus.class, "  ", "status")).isNull();
        assertThat(AdminOrderListSpec.parseEnum(OrderStatus.class, null, "status")).isNull();
    }

    @Test
    void parseEnum_acceptsCaseInsensitiveValues() {
        assertThat(AdminOrderListSpec.parseEnum(OrderStatus.class, "confirmed", "status")).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(AdminOrderListSpec.parseEnum(ZohoSyncStatus.class, "Failed", "zohoSyncStatus")).isEqualTo(
            ZohoSyncStatus.FAILED
        );
    }

    @Test
    void parseEnum_rejectsUnknownValues() {
        assertThatThrownBy(() -> AdminOrderListSpec.parseEnum(OrderStatus.class, "nope", "status"))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void containsPattern_escapesSqlLikeWildcards() {
        assertThat(AdminOrderListSpec.containsPattern("  MNM_1%  ")).isEqualTo("%mnm\\_1\\%%");
        assertThat(AdminOrderListSpec.containsPattern("a\\b")).isEqualTo("%a\\\\b%");
    }

    @Test
    void create_acceptsAttentionAndNotSyncedZohoFilters() {
        assertThat(AdminOrderListSpec.create(null, null, null, null, null, null, "attention", null)).isNotNull();
        assertThat(AdminOrderListSpec.create(null, null, "Jane", "confirmed", "paid", "cod", "not_synced", "pending"))
            .isNotNull();
        assertThat(AdminOrderListSpec.create(null, null, null, null, null, null, null, null, "shipped")).isNotNull();
        assertThat(AdminOrderListSpec.create(null, null, null, "hand_wrapped", null, null, null, null, "awaiting_pickup"))
            .isNotNull();
    }

    @Test
    void parseTrackingStatus_acceptsKnownFulfillmentStates() {
        assertThat(AdminOrderListSpec.parseTrackingStatus("  Shipped  ")).isEqualTo("shipped");
        assertThat(AdminOrderListSpec.parseTrackingStatus("out_for_delivery")).isEqualTo("out_for_delivery");
        assertThat(AdminOrderListSpec.parseTrackingStatus(null)).isNull();
    }

    @Test
    void parseTrackingStatus_rejectsUnknownValues() {
        assertThatThrownBy(() -> AdminOrderListSpec.parseTrackingStatus("packed"))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_rejectsUnknownZohoFilterImmediately() {
        assertThatThrownBy(() -> AdminOrderListSpec.create(null, null, null, null, null, null, "nope", null))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_emptyFilters_returnsConjunction() {
        CriteriaMocks mocks = new CriteriaMocks();
        Specification<CustomerOrder> spec = AdminOrderListSpec.create(null, null, null, null, null, null, null, null);
        Predicate result = spec.toPredicate(mocks.root, mocks.query, mocks.cb);
        assertThat(result).isSameAs(mocks.conjunction);
        verify(mocks.cb).conjunction();
    }

    @Test
    void create_dateRange_addsPlacedAtPredicates() {
        CriteriaMocks mocks = new CriteriaMocks();
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-02-01T00:00:00Z");
        AdminOrderListSpec.create(from, to, null, null, null, null, null, null).toPredicate(mocks.root, mocks.query, mocks.cb);
        verify(mocks.cb).greaterThanOrEqualTo(any(Expression.class), any(Instant.class));
        verify(mocks.cb).lessThan(any(Expression.class), any(Instant.class));
    }

    @Test
    void create_statusPaymentAndMethod_addsEqualityPredicates() {
        CriteriaMocks mocks = new CriteriaMocks();
        AdminOrderListSpec.create(null, null, null, "confirmed", "paid", "cod", null, null)
            .toPredicate(mocks.root, mocks.query, mocks.cb);
        verify(mocks.root, atLeastOnce()).get("status");
        verify(mocks.root, atLeastOnce()).get("paymentStatus");
        verify(mocks.root, atLeastOnce()).get("paymentMethod");
    }

    @Test
    void create_zohoSyncedFilter_addsZohoStatusPredicate() {
        CriteriaMocks mocks = new CriteriaMocks();
        AdminOrderListSpec.create(null, null, null, null, null, null, "synced", null).toPredicate(mocks.root, mocks.query, mocks.cb);
        verify(mocks.root, atLeastOnce()).get("zohoSyncStatus");
    }

    @Test
    void create_ledgerlyFilter_addsLedgerlyPredicate() {
        CriteriaMocks mocks = new CriteriaMocks();
        AdminOrderListSpec.create(null, null, null, null, null, null, null, "synced")
            .toPredicate(mocks.root, mocks.query, mocks.cb);
        verify(mocks.root, atLeastOnce()).get("ledgerlySyncStatus");
    }

    @Test
    void create_searchQuery_addsLikePredicates() {
        CriteriaMocks mocks = new CriteriaMocks();
        AdminOrderListSpec.create(null, null, "MNM-1", null, null, null, null, null).toPredicate(mocks.root, mocks.query, mocks.cb);
        verify(mocks.cb, atLeastOnce()).like(any(Expression.class), anyString(), anyChar());
    }

    @Test
    void create_attentionZohoFilter_addsOrPredicatesForPaidAndCod() {
        CriteriaMocks mocks = new CriteriaMocks();
        AdminOrderListSpec.create(null, null, null, null, null, null, "attention", null)
            .toPredicate(mocks.root, mocks.query, mocks.cb);
        verify(mocks.root, atLeastOnce()).get("zohoSyncStatus");
        verify(mocks.root, atLeastOnce()).get("paymentStatus");
        verify(mocks.root, atLeastOnce()).get("status");
    }

    @Test
    void create_notSyncedZohoFilter_checksNullZohoStatus() {
        CriteriaMocks mocks = new CriteriaMocks();
        AdminOrderListSpec.create(null, null, null, null, null, null, "not_synced", null)
            .toPredicate(mocks.root, mocks.query, mocks.cb);
        verify(mocks.cb, atLeastOnce()).isNull(any(Expression.class));
    }

    @Test
    void create_trackingFilters_buildShipmentPredicates() {
        for (String tracking : List.of("awaiting_pickup", "shipped", "out_for_delivery", "delivery_failed")) {
            CriteriaMocks mocks = new CriteriaMocks();
            AdminOrderListSpec.create(null, null, null, null, null, null, null, null, tracking)
                .toPredicate(mocks.root, mocks.query, mocks.cb);
            verify(mocks.root, atLeastOnce()).get("trackingNumber");
        }
    }

    private static final class CriteriaMocks {

        final CriteriaBuilder cb = mock(CriteriaBuilder.class);
        final CriteriaQuery<?> query = mock(CriteriaQuery.class);
        final Root<CustomerOrder> root = mock(Root.class);
        final Predicate conjunction = mock(Predicate.class);
        final Predicate predicate = mock(Predicate.class);
        final Path<Object> path = mock(Path.class);
        final Expression<String> expression = mock(Expression.class);

        CriteriaMocks() {
            lenient().when(root.get(anyString())).thenReturn(path);
            lenient().when(cb.conjunction()).thenReturn(conjunction);
            lenient().when(cb.and(ArgumentMatchers.<Predicate>any())).thenReturn(predicate);
            lenient().when(cb.or(ArgumentMatchers.<Predicate>any())).thenReturn(predicate);
            lenient().when(cb.equal(any(), any())).thenReturn(predicate);
            lenient().when(cb.notEqual(any(), any())).thenReturn(predicate);
            lenient().when(cb.isNull(any())).thenReturn(predicate);
            lenient().when(cb.isNotNull(any())).thenReturn(predicate);
            lenient().when(cb.not(any(Predicate.class))).thenReturn(predicate);
            lenient().when(cb.not(any(Expression.class))).thenReturn(predicate);
            lenient().when(cb.lower(any())).thenReturn(expression);
            lenient().when(cb.coalesce(any(Expression.class), any())).thenReturn(expression);
            lenient().when(cb.trim(any())).thenReturn(expression);
            lenient().when(cb.like(any(Expression.class), anyString(), anyChar())).thenReturn(predicate);
            lenient().when(cb.greaterThanOrEqualTo(any(Expression.class), any(Instant.class))).thenReturn(predicate);
            lenient().when(cb.lessThan(any(Expression.class), any(Instant.class))).thenReturn(predicate);
            lenient().when(path.in(anyCollection())).thenReturn(predicate);
        }
    }
}
