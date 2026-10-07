package com.miniandmore.app.service;

import com.miniandmore.app.domain.CustomerOrder;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/** Keeps the physical delivery timestamp independent from shipment/dispatch dates. */
public final class OrderDeliverySupport {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter ZOHO_OFFSET_NO_COLON = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
    private static final DateTimeFormatter SPACE_SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter SPACE_MINUTES = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private OrderDeliverySupport() {}

    /** Records delivery once; duplicate or delayed carrier updates must not extend the return window. */
    public static void markDelivered(CustomerOrder order, Instant deliveredAt) {
        if (order != null && order.getShipmentDeliveredAt() == null) {
            order.setShipmentDeliveredAt(deliveredAt != null ? deliveredAt : Instant.now());
        }
    }

    /** Records the best delivery/event timestamp exposed by a provider payload. */
    public static void markDelivered(CustomerOrder order, Map<String, Object> payload) {
        markDelivered(order, deliveredAt(payload));
    }

    static Instant deliveredAt(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        for (
            String key : new String[] {
                "shipment_delivered_date",
                "shipmentDeliveredDate",
                "shipment_delivered_date_formatted",
                "delivered_at",
                "deliveredAt",
                "delivered_date",
                "deliveredDate",
                "delivery_date",
                "deliveryDate",
                "event_time",
                "eventTime",
                "event_at",
                "eventAt",
                "timestamp",
                "last_modified_time",
                "lastModifiedTime",
                "updated_time",
                "updatedTime"
            }
        ) {
            Instant parsed = parseInstant(payload.get(key));
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    static Instant parseInstant(Object value) {
        String text = StringUtils.trimToNull(value == null ? null : value.toString());
        if (text == null || (text.startsWith("${") && text.endsWith("}"))) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ignored) {
            // Try provider-specific formats below.
        }
        try {
            return OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
        } catch (DateTimeParseException ignored) {
            // Try Zoho's offset format without a colon.
        }
        try {
            return OffsetDateTime.parse(text, ZOHO_OFFSET_NO_COLON).toInstant();
        } catch (DateTimeParseException ignored) {
            // Try local date/time formats in the store timezone.
        }
        for (DateTimeFormatter formatter : new DateTimeFormatter[] { DateTimeFormatter.ISO_LOCAL_DATE_TIME, SPACE_SECONDS, SPACE_MINUTES }) {
            try {
                return LocalDateTime.parse(text, formatter).atZone(INDIA).toInstant();
            } catch (DateTimeParseException ignored) {
                // Try next format.
            }
        }
        for (DateTimeFormatter formatter : new DateTimeFormatter[] { DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("dd/MM/yyyy") }) {
            try {
                // A date-only provider value has no delivery time. Use the end of that India day
                // so the customer's three-day window cannot expire prematurely.
                return LocalDate.parse(text, formatter).plusDays(1).atStartOfDay(INDIA).toInstant().minusNanos(1);
            } catch (DateTimeParseException ignored) {
                // Try next format.
            }
        }
        return null;
    }
}
