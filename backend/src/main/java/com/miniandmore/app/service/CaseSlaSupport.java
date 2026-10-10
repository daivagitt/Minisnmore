package com.miniandmore.app.service;

import com.miniandmore.app.config.ApplicationProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/** Computes queue age and SLA due/overdue flags for post-order admin cases. */
@Component
public class CaseSlaSupport {

    private static final Set<String> TERMINAL_RETURN_STATUSES = Set.of(
        "rejected",
        "refunded",
        "completed",
        "cancelled",
        "failed"
    );

    private static final Set<String> TERMINAL_CANCELLATION_STATUSES = Set.of("approved", "rejected", "withdrawn");

    private final ApplicationProperties.PostOrderSla sla;

    public CaseSlaSupport(ApplicationProperties applicationProperties) {
        this.sla = applicationProperties.getPostOrder().getSla();
    }

    public void enrichReturnCase(Map<String, Object> data) {
        if (data == null) {
            return;
        }
        String status = StringUtils.lowerCase(String.valueOf(data.getOrDefault("status", "")), Locale.ROOT);
        Instant requestedAt = toInstant(data.get("requested_at"));
        enrich(data, "return", status, requestedAt, TERMINAL_RETURN_STATUSES);
    }

    public boolean isReturnOverdue(String status, Instant requestedAt) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", status);
        data.put("requested_at", requestedAt);
        enrichReturnCase(data);
        return Boolean.TRUE.equals(data.get("sla_overdue"));
    }

    public void enrichCancellationCase(Map<String, Object> data) {
        if (data == null) {
            return;
        }
        String status = StringUtils.lowerCase(String.valueOf(data.getOrDefault("status", "")), Locale.ROOT);
        Instant requestedAt = toInstant(data.get("requested_at"));
        enrich(data, "cancellation", status, requestedAt, TERMINAL_CANCELLATION_STATUSES);
        boolean codRefundPending = Boolean.TRUE.equals(data.get("cod_refund_pending"));
        if (codRefundPending && requestedAt != null) {
            Instant dueAt = requestedAt.plus(sla.getCollectedCodRefundHours(), java.time.temporal.ChronoUnit.HOURS);
            data.put("sla_due_at", dueAt);
            data.put("sla_overdue", Instant.now().isAfter(dueAt));
        }
    }

    private void enrich(
        Map<String, Object> data,
        String caseType,
        String status,
        Instant requestedAt,
        Set<String> terminalStatuses
    ) {
        if (requestedAt == null) {
            return;
        }
        long ageHours = Duration.between(requestedAt, Instant.now()).toHours();
        data.put("case_age_hours", ageHours);
        if (terminalStatuses.contains(status)) {
            data.put("sla_due_at", null);
            data.put("sla_overdue", false);
            return;
        }
        int limitHours = slaHoursFor(caseType, status);
        Instant dueAt = requestedAt.plus(limitHours, java.time.temporal.ChronoUnit.HOURS);
        data.put("sla_due_at", dueAt);
        data.put("sla_overdue", Instant.now().isAfter(dueAt));
        data.put("sla_limit_hours", limitHours);
    }

    private int slaHoursFor(String caseType, String status) {
        if ("cancellation".equals(caseType)) {
            return sla.getCancellationRequestedHours();
        }
        return switch (status) {
            case "requested" -> sla.getReturnRequestedHours();
            case "approved", "pickup_requested", "pickup_scheduled" -> sla.getReturnPickupHours();
            case "pickup_failed" -> sla.getReturnPickupFailedHours();
            case "received", "inspected" -> sla.getReturnInspectionHours();
            default -> sla.getReturnDefaultHours();
        };
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof String text && StringUtils.isNotBlank(text)) {
            return Instant.parse(text);
        }
        return null;
    }
}
