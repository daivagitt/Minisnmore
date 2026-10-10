package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniandmore.app.config.ApplicationProperties;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CaseSlaSupportTest {

    private CaseSlaSupport caseSlaSupport;

    @BeforeEach
    void setUp() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getPostOrder().getSla().setReturnRequestedHours(48);
        caseSlaSupport = new CaseSlaSupport(properties);
    }

    @Test
    void enrichReturnCase_marksOverdueOpenRequest() {
        var data = new LinkedHashMap<String, Object>();
        data.put("status", "requested");
        data.put("requested_at", Instant.now().minus(72, ChronoUnit.HOURS));

        caseSlaSupport.enrichReturnCase(data);

        assertThat(data.get("case_age_hours")).isEqualTo(72L);
        assertThat(data.get("sla_overdue")).isEqualTo(true);
        assertThat(data.get("sla_limit_hours")).isEqualTo(48);
    }

    @Test
    void isReturnOverdue_trueForOldRequestedCase() {
        assertThat(caseSlaSupport.isReturnOverdue("requested", Instant.now().minus(72, ChronoUnit.HOURS))).isTrue();
        assertThat(caseSlaSupport.isReturnOverdue("requested", Instant.now().minus(1, ChronoUnit.HOURS))).isFalse();
        assertThat(caseSlaSupport.isReturnOverdue("refunded", Instant.now().minus(72, ChronoUnit.HOURS))).isFalse();
    }

    @Test
    void enrichCancellationCase_skipsSlaForTerminalStatus() {
        var data = new LinkedHashMap<String, Object>();
        data.put("status", "rejected");
        data.put("requested_at", Instant.now().minus(96, ChronoUnit.HOURS));

        caseSlaSupport.enrichCancellationCase(data);

        assertThat(data.get("sla_overdue")).isEqualTo(false);
    }

    @Test
    void enrichCancellationCase_openRequest() {
        var data = new LinkedHashMap<String, Object>();
        data.put("status", "requested");
        data.put("requested_at", Instant.now().minus(10, ChronoUnit.HOURS).toString());

        caseSlaSupport.enrichCancellationCase(data);

        assertThat(data.get("case_age_hours")).isNotNull();
        assertThat(data.get("sla_limit_hours")).isEqualTo(24);
    }

    @Test
    void enrichCancellationCase_codRefundPending() {
        var data = new LinkedHashMap<String, Object>();
        data.put("status", "approved");
        data.put("cod_refund_pending", true);
        data.put("requested_at", Instant.now().minus(100, ChronoUnit.HOURS));

        caseSlaSupport.enrichCancellationCase(data);

        assertThat(data.get("sla_overdue")).isEqualTo(true);
        assertThat(data.get("sla_due_at")).isNotNull();
    }

    @Test
    void enrichReturnCase_variousStatuses() {
        // null data
        caseSlaSupport.enrichReturnCase(null);
        caseSlaSupport.enrichCancellationCase(null);

        // approved
        var approved = new LinkedHashMap<String, Object>();
        approved.put("status", "approved");
        approved.put("requested_at", Instant.now().minus(1, ChronoUnit.HOURS));
        caseSlaSupport.enrichReturnCase(approved);
        assertThat(approved.get("sla_overdue")).isEqualTo(false);

        // pickup_failed
        var failed = new LinkedHashMap<String, Object>();
        failed.put("status", "pickup_failed");
        failed.put("requested_at", Instant.now().minus(1, ChronoUnit.HOURS));
        caseSlaSupport.enrichReturnCase(failed);
        assertThat(failed.get("sla_limit_hours")).isNotNull();

        // received / inspected
        var received = new LinkedHashMap<String, Object>();
        received.put("status", "received");
        received.put("requested_at", Instant.now().minus(1, ChronoUnit.HOURS));
        caseSlaSupport.enrichReturnCase(received);
        assertThat(received.get("sla_limit_hours")).isNotNull();

        // default status
        var other = new LinkedHashMap<String, Object>();
        other.put("status", "other_status");
        other.put("requested_at", Instant.now().minus(1, ChronoUnit.HOURS));
        caseSlaSupport.enrichReturnCase(other);
        assertThat(other.get("sla_limit_hours")).isNotNull();

        // terminal return status
        var terminal = new LinkedHashMap<String, Object>();
        terminal.put("status", "refunded");
        terminal.put("requested_at", Instant.now().minus(1, ChronoUnit.HOURS));
        caseSlaSupport.enrichReturnCase(terminal);
        assertThat(terminal.get("sla_overdue")).isEqualTo(false);
        assertThat(terminal.get("sla_due_at")).isNull();

        // null requested_at
        var noDate = new LinkedHashMap<String, Object>();
        noDate.put("status", "requested");
        noDate.put("requested_at", null);
        caseSlaSupport.enrichReturnCase(noDate);
        assertThat(noDate.get("sla_due_at")).isNull();

        // invalid requested_at type
        var invalidDate = new LinkedHashMap<String, Object>();
        invalidDate.put("status", "requested");
        invalidDate.put("requested_at", 12345);
        caseSlaSupport.enrichReturnCase(invalidDate);
        assertThat(invalidDate.get("sla_due_at")).isNull();
    }
}
