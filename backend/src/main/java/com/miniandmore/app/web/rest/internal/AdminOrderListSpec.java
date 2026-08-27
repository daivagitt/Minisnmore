package com.miniandmore.app.web.rest.internal;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.enumeration.LedgerlySyncStatus;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.domain.enumeration.ZohoSyncStatus;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class AdminOrderListSpec {

    private static final char LIKE_ESCAPE = '\\';

    private AdminOrderListSpec() {}

    static Specification<CustomerOrder> create(
        Instant from,
        Instant to,
        String q,
        String statusRaw,
        String paymentStatusRaw,
        String paymentMethodRaw,
        String zohoSyncStatusRaw,
        String ledgerlySyncStatusRaw
    ) {
        OrderStatus status = parseEnum(OrderStatus.class, statusRaw, "status");
        PaymentStatus paymentStatus = parseEnum(PaymentStatus.class, paymentStatusRaw, "paymentStatus");
        String paymentMethod = StringUtils.trimToNull(paymentMethodRaw);
        String zohoKey = StringUtils.trimToEmpty(zohoSyncStatusRaw).toLowerCase(Locale.ROOT);
        boolean zohoAttention = "attention".equals(zohoKey);
        boolean zohoNotSynced = "not_synced".equals(zohoKey);
        ZohoSyncStatus zohoStatus = zohoKey.isEmpty() || zohoAttention || zohoNotSynced
            ? null
            : parseEnum(ZohoSyncStatus.class, zohoKey, "zohoSyncStatus");
        LedgerlySyncStatus ledgerlySyncStatus = parseEnum(
            LedgerlySyncStatus.class,
            ledgerlySyncStatusRaw,
            "ledgerlySyncStatus"
        );
        String query = StringUtils.trimToNull(q);
        return (root, ignored, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (from != null && to != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("placedAt"), from));
                predicates.add(cb.lessThan(root.get("placedAt"), to));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (paymentStatus != null) {
                predicates.add(cb.equal(root.get("paymentStatus"), paymentStatus));
            }
            if (paymentMethod != null) {
                predicates.add(
                    cb.equal(
                        cb.lower(cb.coalesce(root.get("paymentMethod"), "")),
                        paymentMethod.toLowerCase(Locale.ROOT)
                    )
                );
            }
            if (zohoAttention) {
                predicates.add(
                    cb.or(
                        cb.isNull(root.get("zohoSyncStatus")),
                        cb.notEqual(root.get("zohoSyncStatus"), ZohoSyncStatus.SYNCED)
                    )
                );
                predicates.add(
                    cb.or(
                        cb.equal(root.get("paymentStatus"), PaymentStatus.PAID),
                        cb.and(
                            cb.equal(cb.lower(cb.coalesce(root.get("paymentMethod"), "")), "cod"),
                            cb.equal(root.get("status"), OrderStatus.CONFIRMED)
                        )
                    )
                );
            } else if (zohoNotSynced) {
                predicates.add(cb.isNull(root.get("zohoSyncStatus")));
            } else if (zohoStatus != null) {
                predicates.add(cb.equal(root.get("zohoSyncStatus"), zohoStatus));
            }
            if (ledgerlySyncStatus != null) {
                predicates.add(cb.equal(root.get("ledgerlySyncStatus"), ledgerlySyncStatus));
            }
            if (query != null) {
                String like = containsPattern(query);
                predicates.add(
                    cb.or(
                        cb.like(cb.lower(cb.coalesce(root.get("orderNumber"), "")), like, LIKE_ESCAPE),
                        cb.like(cb.lower(cb.coalesce(root.get("userLogin"), "")), like, LIKE_ESCAPE),
                        cb.like(cb.lower(cb.coalesce(root.get("shippingAddressJson"), "")), like, LIKE_ESCAPE)
                    )
                );
            }
            if (predicates.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    static String containsPattern(String raw) {
        String escaped = raw
            .trim()
            .toLowerCase(Locale.ROOT)
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String field) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + field);
        }
    }
}
