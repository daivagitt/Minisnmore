package com.miniandmore.app.service;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.domain.OrderEvent;
import com.miniandmore.app.repository.OrderEventRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OrderEventService {

    public static final String TYPE_ORDER_PLACED = "order_placed";
    public static final String TYPE_PAYMENT_RECEIVED = "payment_received";
    public static final String TYPE_HAND_WRAPPED = "hand_wrapped";
    public static final String TYPE_SHIPPED = "shipped";
    public static final String TYPE_DELIVERED = "delivered";
    public static final String TYPE_CANCELLED = "cancelled";
    public static final String TYPE_REFUNDED = "refunded";

    private static final Logger LOG = LoggerFactory.getLogger(OrderEventService.class);
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final int MAX_EVENTS = 100;

    private final OrderEventRepository orderEventRepository;
    private final OrderMailService orderMailService;
    private final OrderSmsService orderSmsService;

    public OrderEventService(
        OrderEventRepository orderEventRepository,
        OrderMailService orderMailService,
        OrderSmsService orderSmsService
    ) {
        this.orderEventRepository = orderEventRepository;
        this.orderMailService = orderMailService;
        this.orderSmsService = orderSmsService;
    }

    public void onOrderCreated(CustomerOrder order) {
        syncLifecycleEvents(order);
    }

    public void onOrderPaid(CustomerOrder order) {
        recordIfAbsent(
            order,
            TYPE_PAYMENT_RECEIVED,
            titleFor(TYPE_PAYMENT_RECEIVED),
            bodyFor(order, TYPE_PAYMENT_RECEIVED),
            Instant.now()
        );
        syncLifecycleEvents(order);
    }

    /**
     * Idempotent: records any lifecycle events that match the order's current state but are not yet stored.
     */
    public void syncLifecycleEvents(CustomerOrder order) {
        if (order.getId() == null) {
            return;
        }
        recordIfAbsent(order, TYPE_ORDER_PLACED, titleFor(TYPE_ORDER_PLACED), bodyFor(order, TYPE_ORDER_PLACED), placedAt(order));

        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            recordIfAbsent(
                order,
                TYPE_PAYMENT_RECEIVED,
                titleFor(TYPE_PAYMENT_RECEIVED),
                bodyFor(order, TYPE_PAYMENT_RECEIVED),
                placedAt(order)
            );
        }
        if (order.getPaymentStatus() == PaymentStatus.REFUNDED) {
            recordIfAbsent(order, TYPE_REFUNDED, titleFor(TYPE_REFUNDED), bodyFor(order, TYPE_REFUNDED), Instant.now());
        }

        String tracking = OrderTrackingStatusResolver.resolve(order);
        switch (tracking) {
            case "hand_wrapped":
                recordIfAbsent(
                    order,
                    TYPE_HAND_WRAPPED,
                    titleFor(TYPE_HAND_WRAPPED),
                    bodyFor(order, TYPE_HAND_WRAPPED),
                    Instant.now()
                );
                break;
            case "cancelled":
                recordIfAbsent(
                    order,
                    TYPE_CANCELLED,
                    titleFor(TYPE_CANCELLED),
                    bodyFor(order, TYPE_CANCELLED),
                    Instant.now()
                );
                break;
            case "delivered":
                recordIfAbsent(
                    order,
                    TYPE_SHIPPED,
                    titleFor(TYPE_SHIPPED),
                    bodyFor(order, TYPE_SHIPPED),
                    shipmentInstant(order).orElse(placedAt(order))
                );
                recordIfAbsent(
                    order,
                    TYPE_DELIVERED,
                    titleFor(TYPE_DELIVERED),
                    bodyFor(order, TYPE_DELIVERED),
                    shipmentInstant(order).orElse(Instant.now())
                );
                break;
            case "shipped":
                recordIfAbsent(
                    order,
                    TYPE_SHIPPED,
                    titleFor(TYPE_SHIPPED),
                    bodyFor(order, TYPE_SHIPPED),
                    shipmentInstant(order).orElse(Instant.now())
                );
                break;
            default:
                break;
        }
    }

    public List<Map<String, Object>> listForUser(String userLogin) {
        return orderEventRepository
            .findAllByUserLoginOrderByCreatedAtDesc(userLogin, PageRequest.of(0, MAX_EVENTS))
            .stream()
            .map(this::toMap)
            .toList();
    }

    private Map<String, Object> toMap(OrderEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", event.getId());
        data.put("event_type", event.getEventType());
        data.put("title", event.getTitle());
        data.put("body", event.getBody());
        data.put("order_number", event.getOrderNumber());
        data.put("created_at", event.getCreatedAt() == null ? "" : event.getCreatedAt().toString());
        return data;
    }

    private void recordIfAbsent(
        CustomerOrder order,
        String eventType,
        String title,
        String body,
        Instant createdAt
    ) {
        if (orderEventRepository.existsByOrderIdAndEventType(order.getId(), eventType)) {
            return;
        }
        OrderEvent event = new OrderEvent()
            .userLogin(order.getUserLogin())
            .orderId(order.getId())
            .orderNumber(order.getOrderNumber())
            .eventType(eventType)
            .title(title)
            .body(body)
            .createdAt(createdAt != null ? createdAt : Instant.now());
        orderEventRepository.save(event);
        queueLifecycleEmail(order, eventType);
    }

    private void queueLifecycleEmail(CustomerOrder order, String eventType) {
        AfterCommit.run(() -> {
            try {
                switch (eventType) {
                    case TYPE_ORDER_PLACED:
                        if (isCod(order)) {
                            orderMailService.sendOrderConfirmation(order);
                            orderSmsService.sendOrderConfirmation(order);
                        }
                        break;
                    case TYPE_PAYMENT_RECEIVED:
                        if (!isCod(order)) {
                            orderMailService.sendOrderConfirmation(order);
                            orderSmsService.sendOrderConfirmation(order);
                        }
                        break;
                    case TYPE_SHIPPED:
                        orderMailService.sendOutForDelivery(order);
                        orderSmsService.sendOutForDelivery(order);
                        break;
                    case TYPE_CANCELLED:
                        orderMailService.sendOrderCancelled(order);
                        orderSmsService.sendOrderCancelled(order);
                        break;
                    default:
                        break;
                }
            } catch (RuntimeException ex) {
                LOG.warn("Order email/SMS failed for {} on {}", eventType, order.getOrderNumber(), ex);
            }
        });
    }

    private static boolean isCod(CustomerOrder order) {
        return "cod".equalsIgnoreCase(StringUtils.trimToEmpty(order.getPaymentMethod()));
    }

    private String titleFor(String eventType) {
        switch (eventType) {
            case TYPE_ORDER_PLACED:
                return "Order placed";
            case TYPE_PAYMENT_RECEIVED:
                return "Payment received";
            case TYPE_HAND_WRAPPED:
                return "Hand-wrapped";
            case TYPE_SHIPPED:
                return "On the way";
            case TYPE_DELIVERED:
                return "Delivered";
            case TYPE_CANCELLED:
                return "Order cancelled";
            case TYPE_REFUNDED:
                return "Refund processed";
            default:
                return "Order update";
        }
    }

    private String bodyFor(CustomerOrder order, String eventType) {
        StringBuilder body = new StringBuilder(order.getOrderNumber());
        BigDecimal total = order.getTotal();
        if (total != null && total.compareTo(BigDecimal.ZERO) > 0) {
            body.append(" · ₹").append(total.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString());
        }
        if (TYPE_SHIPPED.equals(eventType) && StringUtils.isNotBlank(order.getTrackingNumber())) {
            body.append(" · Tracking ").append(order.getTrackingNumber().trim());
        }
        return body.toString();
    }

    private Instant placedAt(CustomerOrder order) {
        return order.getPlacedAt() != null ? order.getPlacedAt() : Instant.now();
    }

    private Optional<Instant> shipmentInstant(CustomerOrder order) {
        String shipmentDate = StringUtils.trimToNull(order.getShipmentDate());
        if (shipmentDate == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(shipmentDate).atStartOfDay(INDIA).toInstant());
        } catch (DateTimeParseException ex) {
            try {
                return Optional.of(Instant.parse(shipmentDate));
            } catch (DateTimeParseException ignored) {
                return Optional.empty();
            }
        }
    }
}
