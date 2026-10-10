package com.miniandmore.app.web.rest.internal;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.enumeration.LedgerlySyncStatus;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.domain.enumeration.ReturnLogisticsMode;
import com.miniandmore.app.domain.enumeration.ZohoSyncStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class AdminOrderListSpec {

    private static final char LIKE_ESCAPE = '\\';
    private static final Set<String> TRACKING_STATUS_FILTERS = Set.of(
        "awaiting_pickup",
        "shipped",
        "out_for_delivery",
        "delivery_failed",
        "carrier_cancelled"
    );
    private static final List<String> AWAITING_PICKUP_CARRIER = List.of(
        "booked",
        "pending",
        "created",
        "data_received",
        "shipment_created",
        "label_generated",
        "manifest_generated",
        "pickup_scheduled",
        "pending_pickup",
        "out_for_pickup",
        "scheduled"
    );

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
        return create(from, to, q, statusRaw, paymentStatusRaw, paymentMethodRaw, zohoSyncStatusRaw, ledgerlySyncStatusRaw, null);
    }

    static Specification<CustomerOrder> create(
        Instant from,
        Instant to,
        String q,
        String statusRaw,
        String paymentStatusRaw,
        String paymentMethodRaw,
        String zohoSyncStatusRaw,
        String ledgerlySyncStatusRaw,
        String trackingStatusRaw
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
        String trackingStatus = parseTrackingStatus(trackingStatusRaw);
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
            if (trackingStatus != null) {
                predicates.add(trackingStatusPredicate(root, cb, trackingStatus));
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
                            cb.or(
                                cb.equal(root.get("status"), OrderStatus.CONFIRMED),
                                cb.equal(root.get("status"), OrderStatus.HAND_WRAPPED)
                            )
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

    static String parseTrackingStatus(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        if (!TRACKING_STATUS_FILTERS.contains(key)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid trackingStatus");
        }
        return key;
    }

    private static Predicate trackingStatusPredicate(Root<CustomerOrder> root, CriteriaBuilder cb, String trackingStatus) {
        return switch (trackingStatus) {
            case "awaiting_pickup" -> awaitingPickup(root, cb);
            case "out_for_delivery" -> outForDelivery(root, cb);
            case "delivery_failed" -> deliveryFailed(root, cb);
            case "carrier_cancelled" -> carrierCancelled(root, cb);
            default -> shipped(root, cb);
        };
    }

    private static Predicate shipped(Root<CustomerOrder> root, CriteriaBuilder cb) {
        return cb.and(
            hasForwardShipment(root, cb),
            cb.not(root.get("status").in(OrderStatus.FULFILLED, OrderStatus.CANCELLED)),
            cb.isNull(root.get("shipmentCancelledAt")),
            cb.not(awaitingPickup(root, cb)),
            cb.not(outForDelivery(root, cb)),
            cb.not(deliveryFailed(root, cb)),
            cb.not(carrierCancelled(root, cb))
        );
    }

    private static Predicate awaitingPickup(Root<CustomerOrder> root, CriteriaBuilder cb) {
        Expression<String> carrier = carrierStatus(root, cb);
        List<Predicate> awaitingCarrier = new ArrayList<>();
        awaitingCarrier.add(cb.equal(carrier, ""));
        for (String status : AWAITING_PICKUP_CARRIER) {
            awaitingCarrier.add(cb.equal(carrier, status));
        }
        return cb.and(
            hasAwb(root, cb),
            cb.equal(root.get("shipmentLogisticsMode"), ReturnLogisticsMode.XPRESSBEES),
            cb.isNull(root.get("shipmentCancelledAt")),
            cb.not(root.get("status").in(OrderStatus.FULFILLED, OrderStatus.CANCELLED)),
            cb.or(awaitingCarrier.toArray(Predicate[]::new))
        );
    }

    private static Predicate outForDelivery(Root<CustomerOrder> root, CriteriaBuilder cb) {
        Expression<String> carrier = carrierStatus(root, cb);
        return cb.and(
            hasForwardShipment(root, cb),
            cb.isNull(root.get("shipmentCancelledAt")),
            cb.not(root.get("status").in(OrderStatus.FULFILLED, OrderStatus.CANCELLED)),
            cb.or(
                cb.equal(carrier, "ofd"),
                cb.like(carrier, "%out_for_delivery%"),
                cb.like(carrier, "%out-for-delivery%")
            )
        );
    }

    private static Predicate deliveryFailed(Root<CustomerOrder> root, CriteriaBuilder cb) {
        Expression<String> carrier = carrierStatus(root, cb);
        return cb.and(
            hasForwardShipment(root, cb),
            cb.isNull(root.get("shipmentCancelledAt")),
            cb.not(root.get("status").in(OrderStatus.FULFILLED, OrderStatus.CANCELLED)),
            cb.or(
                cb.like(carrier, "%undelivered%"),
                cb.like(carrier, "%not_delivered%"),
                cb.like(carrier, "%not-delivered%"),
                cb.like(carrier, "%delivery_failed%"),
                cb.like(carrier, "%delivery-failed%"),
                cb.like(carrier, "%failed_delivery%"),
                cb.like(carrier, "%failed-delivery%")
            )
        );
    }

    private static Predicate carrierCancelled(Root<CustomerOrder> root, CriteriaBuilder cb) {
        return cb.and(
            hasAwb(root, cb),
            cb.equal(root.get("shipmentLogisticsMode"), ReturnLogisticsMode.XPRESSBEES),
            cb.isNull(root.get("shipmentCancelledAt")),
            cb.not(root.get("status").in(OrderStatus.FULFILLED, OrderStatus.CANCELLED)),
            cb.or(
                cb.equal(carrierStatus(root, cb), "carrier_cancelled"),
                cb.equal(carrierStatus(root, cb), "cancelled"),
                cb.equal(carrierStatus(root, cb), "canceled")
            )
        );
    }

    private static Predicate hasForwardShipment(Root<CustomerOrder> root, CriteriaBuilder cb) {
        return cb.or(hasAwb(root, cb), hasText(root, cb, "shipmentDate"));
    }

    private static Predicate hasAwb(Root<CustomerOrder> root, CriteriaBuilder cb) {
        return hasText(root, cb, "trackingNumber");
    }

    private static Predicate hasText(Root<CustomerOrder> root, CriteriaBuilder cb, String field) {
        return cb.and(cb.isNotNull(root.get(field)), cb.notEqual(cb.trim(root.get(field)), ""));
    }

    private static Expression<String> carrierStatus(Root<CustomerOrder> root, CriteriaBuilder cb) {
        return cb.lower(cb.coalesce(root.get("shipmentCarrierStatus"), ""));
    }
}
