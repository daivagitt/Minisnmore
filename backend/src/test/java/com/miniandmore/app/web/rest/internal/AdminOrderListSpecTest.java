package com.miniandmore.app.web.rest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.ZohoSyncStatus;
import org.junit.jupiter.api.Test;
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
    }

    @Test
    void create_rejectsUnknownZohoFilterImmediately() {
        assertThatThrownBy(() -> AdminOrderListSpec.create(null, null, null, null, null, null, "nope", null))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
