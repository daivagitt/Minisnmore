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
    void enrichCancellationCase_skipsSlaForTerminalStatus() {
        var data = new LinkedHashMap<String, Object>();
        data.put("status", "rejected");
        data.put("requested_at", Instant.now().minus(96, ChronoUnit.HOURS));

        caseSlaSupport.enrichCancellationCase(data);

        assertThat(data.get("sla_overdue")).isEqualTo(false);
    }
}
