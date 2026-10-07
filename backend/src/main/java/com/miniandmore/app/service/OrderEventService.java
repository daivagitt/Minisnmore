package com.miniandmore.app.service;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.domain.enumeration.ReturnLogisticsMode;
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
    public static final String TYPE_PAYMENT_PENDING = "payment_pending";
    public static final String TYPE_PAYMENT_RECEIVED = "payment_received";
    public static final String TYPE_HAND_WRAPPED = "hand_wrapped";
    public static final String TYPE_SHIPPED = "shipped";
    public static final String TYPE_OUT_FOR_DELIVERY = "out_for_delivery";
    public static final String TYPE_DELIVERED = "delivered";
    public static final String TYPE_CANCELLED = "cancelled";
    public static final String TYPE_REFUNDED = "refunded";
    public static final String TYPE_REFUND_INITIATED = "refund_initiated";
    public static final String TYPE_REFUND_FAILED = "refund_failed";
    public static final String TYPE_DELIVERY_FAILED = "delivery_failed";

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
        syncLifecycleEvents(order, true);
    }

    /**
     * @param notifyCancelled false when OrderCancellationService will send cancel mail/SMS itself
     */
    public void syncLifecycleEvents(CustomerOrder order, boolean notifyCancelled) {
        if (order.getId() == null) {
            return;
        }
        if (isCod(order) || order.getPaymentStatus() == PaymentStatus.PAID) {
            recordIfAbsent(order, TYPE_ORDER_PLACED, titleFor(TYPE_ORDER_PLACED), bodyFor(order, TYPE_ORDER_PLACED), placedAt(order));
        } else if (
            !isCod(order) &&
            order.getPaymentStatus() == PaymentStatus.PENDING &&
            order.getStatus() != OrderStatus.CANCELLED &&
            !orderEventRepository.existsByOrderIdAndEventType(order.getId(), TYPE_ORDER_PLACED)
        ) {
            recordIfAbsent(
                order,
                TYPE_PAYMENT_PENDING,
                titleFor(TYPE_PAYMENT_PENDING),
                bodyFor(order, TYPE_PAYMENT_PENDING),
                placedAt(order)
            );
        }

        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            recordIfAbsent(
                order,
                TYPE_PAYMENT_RECEIVED,
                titleFor(TYPE_PAYMENT_RECEIVED),
                bodyFor(order, TYPE_PAYMENT_RECEIVED),
                placedAt(order)
            );
        }
        if (order.getPaymentStatus() == PaymentStatus.REFUND_INITIATED) {
            recordIfAbsent(
                order,
                TYPE_REFUND_INITIATED,
                titleFor(TYPE_REFUND_INITIATED),
                bodyFor(order, TYPE_REFUND_INITIATED),
                Instant.now()
            );
        }
        if (order.getPaymentStatus() == PaymentStatus.REFUNDED) {
            recordIfAbsent(order, TYPE_REFUNDED, titleFor(TYPE_REFUNDED), bodyFor(order, TYPE_REFUNDED), Instant.now());
        }
        if (order.getPaymentStatus() == PaymentStatus.REFUND_FAILED) {
            recordIfAbsent(
                order,
                TYPE_REFUND_FAILED,
                titleFor(TYPE_REFUND_FAILED),
                bodyFor(order, TYPE_REFUND_FAILED),
                Instant.now()
            );
        }

        String tracking = OrderTrackingStatusResolver.resolve(order);
        switch (tracking) {
            case TYPE_HAND_WRAPPED:
                recordIfAbsent(
                    order,
                    TYPE_HAND_WRAPPED,
                    titleFor(TYPE_HAND_WRAPPED),
                    bodyFor(order, TYPE_HAND_WRAPPED),
                    Instant.now()
                );
                break;
            case TYPE_CANCELLED:
                recordIfAbsent(
                    order,
                    TYPE_CANCELLED,
                    titleFor(TYPE_CANCELLED),
                    bodyFor(order, TYPE_CANCELLED),
                    Instant.now(),
                    notifyCancelled
                );
                break;
            case TYPE_DELIVERED:
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
                    deliveryInstant(order).orElse(Instant.now())
                );
                break;
            case TYPE_OUT_FOR_DELIVERY:
                recordIfAbsent(
                    order,
                    TYPE_SHIPPED,
                    titleFor(TYPE_SHIPPED),
                    bodyFor(order, TYPE_SHIPPED),
                    shipmentInstant(order).orElse(Instant.now())
                );
                recordIfAbsent(
                    order,
                    TYPE_OUT_FOR_DELIVERY,
                    titleFor(TYPE_OUT_FOR_DELIVERY),
                    bodyFor(order, TYPE_OUT_FOR_DELIVERY),
                    Instant.now()
                );
                break;
            case TYPE_DELIVERY_FAILED:
                recordIfAbsent(
                    order,
                    TYPE_SHIPPED,
                    titleFor(TYPE_SHIPPED),
                    bodyFor(order, TYPE_SHIPPED),
                    shipmentInstant(order).orElse(Instant.now())
                );
                recordIfAbsent(
                    order,
                    TYPE_DELIVERY_FAILED,
                    titleFor(TYPE_DELIVERY_FAILED),
                    bodyFor(order, TYPE_DELIVERY_FAILED),
                    Instant.now()
                );
                break;
            case TYPE_SHIPPED:
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
        recordIfAbsent(order, eventType, title, body, createdAt, true);
    }

    private void recordIfAbsent(
        CustomerOrder order,
        String eventType,
        String title,
        String body,
        Instant createdAt,
        boolean queueNotification
    ) {
        int inserted = orderEventRepository.insertIfAbsent(
            order.getUserLogin(),
            order.getId(),
            order.getOrderNumber(),
            eventType,
            title,
            body,
            createdAt != null ? createdAt : Instant.now()
        );
        if (inserted > 0 && queueNotification) {
            queueLifecycleEmail(order, eventType);
        }
    }

    private void queueLifecycleEmail(CustomerOrder order, String eventType) {
        AfterCommit.run(() -> {
            switch (eventType) {
                case TYPE_ORDER_PLACED:
                    if (isCod(order)) {
                        notifyBothChannels(
                            eventType,
                            order,
                            () -> orderMailService.sendOrderConfirmation(order),
                            () -> orderSmsService.sendOrderConfirmation(order)
                        );
                    }
                    break;
                case TYPE_PAYMENT_RECEIVED:
                    if (!isCod(order)) {
                        notifyBothChannels(
                            eventType,
                            order,
                            () -> orderMailService.sendOrderConfirmation(order),
                            () -> orderSmsService.sendOrderConfirmation(order)
                        );
                    }
                    break;
                case TYPE_SHIPPED:
                    // ExpressBees reports a real OUT FOR DELIVERY scan; notify on that event instead of pickup.
                    if (!isExpressBeesShipment(order)) {
                        notifyBothChannels(
                            eventType,
                            order,
                            () -> orderMailService.sendOutForDelivery(order),
                            () -> orderSmsService.sendOutForDelivery(order)
                        );
                    }
                    break;
                case TYPE_OUT_FOR_DELIVERY:
                    notifyBothChannels(
                        eventType,
                        order,
                        () -> orderMailService.sendOutForDelivery(order),
                        () -> orderSmsService.sendOutForDelivery(order)
                    );
                    break;
                case TYPE_DELIVERED:
                    notifyBothChannels(
                        eventType,
                        order,
                        () -> orderMailService.sendOrderDelivered(order),
                        () -> orderSmsService.sendOrderDelivered(order)
                    );
                    break;
                case TYPE_CANCELLED:
                    notifyBothChannels(
                        eventType,
                        order,
                        () -> orderMailService.sendOrderCancelled(order),
                        () -> orderSmsService.sendOrderCancelled(order)
                    );
                    break;
                case TYPE_REFUND_INITIATED:
                    if (order.getStatus() == OrderStatus.CANCELLED) {
                        // OrderCancellationService already sent sendRefundInitiated for this cancel.
                        break;
                    }
                    notifyBothChannels(
                        eventType,
                        order,
                        () ->
                            orderMailService.sendRefundInitiated(
                                order,
                                order.getTotal(),
                                null,
                                order.getPaymentMethod()
                            ),
                        () -> {}
                    );
                    break;
                case TYPE_REFUNDED:
                    notifyBothChannels(
                        eventType,
                        order,
                        () -> orderMailService.sendRefundCompleted(order, order.getTotal(), null, order.getPaymentMethod()),
                        () -> {}
                    );
                    break;
                case TYPE_REFUND_FAILED:
                    notifyBothChannels(
                        eventType,
                        order,
                        () -> orderMailService.sendRefundFailed(order, order.getTotal(), null, order.getPaymentMethod()),
                        () -> {}
                    );
                    break;
                case TYPE_DELIVERY_FAILED:
                    notifyBothChannels(
                        eventType,
                        order,
                        () -> orderMailService.sendDeliveryFailed(order),
                        () -> {}
                    );
                    break;
                default:
                    break;
            }
        });
    }

    private void notifyBothChannels(String eventType, CustomerOrder order, Runnable mailSend, Runnable smsSend) {
        sendNotificationChannel("email", eventType, order, mailSend);
        sendNotificationChannel("SMS", eventType, order, smsSend);
    }

    private void sendNotificationChannel(String channel, String eventType, CustomerOrder order, Runnable send) {
        try {
            send.run();
        } catch (RuntimeException ex) {
            LOG.warn("Order {} notification failed (channel={}, eventType={})", order.getOrderNumber(), channel, eventType, ex);
        }
    }

    private static boolean isCod(CustomerOrder order) {
        return "cod".equalsIgnoreCase(StringUtils.trimToEmpty(order.getPaymentMethod()));
    }

    private static boolean isExpressBeesShipment(CustomerOrder order) {
        return order.getShipmentLogisticsMode() == ReturnLogisticsMode.XPRESSBEES;
    }

    private String titleFor(String eventType) {
        switch (eventType) {
            case TYPE_ORDER_PLACED:
                return "Order placed";
            case TYPE_PAYMENT_PENDING:
                return "Payment pending";
            case TYPE_PAYMENT_RECEIVED:
                return "Payment received";
            case TYPE_HAND_WRAPPED:
                return "Hand-wrapped";
            case TYPE_SHIPPED:
                return "On the way";
            case TYPE_OUT_FOR_DELIVERY:
                return "Out for delivery";
            case TYPE_DELIVERED:
                return "Delivered";
            case TYPE_DELIVERY_FAILED:
                return "Delivery attempt unsuccessful";
            case TYPE_CANCELLED:
                return "Order cancelled";
            case TYPE_REFUND_INITIATED:
                return "Refund initiated";
            case TYPE_REFUNDED:
                return "Refund processed";
            case TYPE_REFUND_FAILED:
                return "Refund failed";
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
        if (
            (TYPE_SHIPPED.equals(eventType) || TYPE_OUT_FOR_DELIVERY.equals(eventType)) &&
            StringUtils.isNotBlank(order.getTrackingNumber())
        ) {
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

    private Optional<Instant> deliveryInstant(CustomerOrder order) {
        return Optional.ofNullable(order.getShipmentDeliveredAt()).or(() -> shipmentInstant(order));
    }
}
