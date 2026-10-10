package com.miniandmore.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.SiteSetting;
import com.miniandmore.app.domain.enumeration.OrderReturnStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import com.miniandmore.app.repository.OrderItemRepository;
import com.miniandmore.app.repository.SiteSettingRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import com.miniandmore.app.domain.OrderCancellationRequest;
import com.miniandmore.app.domain.enumeration.OrderRequestType;
import tech.jhipster.config.JHipsterProperties;

/**
 * Customer transactional mail for order lifecycle, matching Minis n More auth email branding.
 */
@Service
public class OrderMailService {

    private static final Logger LOG = LoggerFactory.getLogger(OrderMailService.class);
    private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;
    private static final String CANCELLATION_NUMBER = "cancellationNumber";
    private static final String CANCELLATION_REASON = "cancellationReason";
    private static final String ADMIN_NOTES = "adminNotes";
    private static final String REFUND_AMOUNT = "refundAmount";
    private static final String REFUND_AMOUNT_FORMATTED = "refundAmountFormatted";
    private static final String RESOLVED_ON_FORMATTED = "resolvedOnFormatted";
    private static final String PAYMENT_METHOD = "paymentMethod";
    private static final String TRACKING_URL = "trackingUrl";
    private static final String DEFAULT_BASE_URL = "https://minisnmore.com";
    private static final String TEMPLATE_ORDER_RETURN = "mail/orderReturnEmail";
    private static final String FIELD_PHONE = "phone";

    private final JHipsterProperties jHipsterProperties;
    private final JavaMailSender javaMailSender;
    private final MessageSource messageSource;
    private final SpringTemplateEngine templateEngine;
    private final OrderItemRepository orderItemRepository;
    private final SiteSettingRepository siteSettingRepository;
    private final ObjectMapper objectMapper;

    public OrderMailService(
        JHipsterProperties jHipsterProperties,
        JavaMailSender javaMailSender,
        MessageSource messageSource,
        SpringTemplateEngine templateEngine,
        OrderItemRepository orderItemRepository,
        SiteSettingRepository siteSettingRepository,
        ObjectMapper objectMapper
    ) {
        this.jHipsterProperties = jHipsterProperties;
        this.javaMailSender = javaMailSender;
        this.messageSource = messageSource;
        this.templateEngine = templateEngine;
        this.orderItemRepository = orderItemRepository;
        this.siteSettingRepository = siteSettingRepository;
        this.objectMapper = objectMapper;
    }

    // Trigger 1: Order Confirmed
    public void sendOrderConfirmation(CustomerOrder order) {
        sendOrderTemplate(order, "mail/orderConfirmationEmail", "email.order.confirm.title", Map.of());
    }

    // Trigger 2: Out for Delivery
    public void sendOutForDelivery(CustomerOrder order) {
        sendOrderTemplate(order, "mail/orderOutForDeliveryEmail", "email.order.shipped.title", Map.of());
    }

    // Trigger 3: Order Delivered
    public void sendOrderDelivered(CustomerOrder order) {
        sendOrderTemplate(order, "mail/orderDeliveredEmail", "email.order.delivered.title", Map.of());
    }

    // Trigger 3b: Delivery Failed / Undelivered
    public void sendDeliveryFailed(CustomerOrder order) {
        sendOrderTemplate(order, "mail/deliveryFailedEmail", "email.order.delivery_failed.title", Map.of());
    }

    // Trigger 4: Cancellation Request Received
    public void sendCancellationRequested(CustomerOrder order, OrderCancellationRequest cancellation) {
        Map<String, Object> extra = new LinkedHashMap<>();
        if (cancellation != null) {
            extra.put(CANCELLATION_NUMBER, cancellation.getRequestNumber());
            extra.put(CANCELLATION_REASON, cancellation.getReason());
            extra.put("requestedOn", formatOrderDate(cancellation.getRequestedAt()));
            extra.put("requestedOnFormatted", formatOrderDate(cancellation.getRequestedAt()));
        }
        sendOrderTemplate(order, "mail/cancellationRequestedEmail", "email.order.cancel.requested.title", extra);
    }

    // Trigger 5: Cancellation Accepted
    public void sendCancellationAccepted(CustomerOrder order, OrderCancellationRequest cancellation) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("hasRefund", false);
        if (cancellation != null) {
            extra.put(CANCELLATION_NUMBER, cancellation.getRequestNumber());
            extra.put(CANCELLATION_REASON, cancellation.getReason());
            extra.put(ADMIN_NOTES, cancellation.getAdminNotes());
            BigDecimal refund = null;
            if (cancellation.getRefundAmount() != null) {
                refund = cancellation.getRefundAmount();
            } else if (order != null && "online".equalsIgnoreCase(order.getPaymentMethod())) {
                refund = order.getTotal();
            }
            if (refund != null) {
                extra.put(REFUND_AMOUNT, formatInr(refund));
                extra.put(REFUND_AMOUNT_FORMATTED, formatInr(refund));
                extra.put("hasRefund", isPositive(refund));
            }
            extra.put("resolvedOn", formatOrderDate(cancellation.getResolvedAt()));
            extra.put(RESOLVED_ON_FORMATTED, formatOrderDate(cancellation.getResolvedAt()));
        }
        sendOrderTemplate(order, "mail/cancellationAcceptedEmail", "email.order.cancel.accepted.title", extra);
    }

    // Trigger 6: Cancellation Rejected
    public void sendCancellationRejected(CustomerOrder order, OrderCancellationRequest cancellation) {
        Map<String, Object> extra = new LinkedHashMap<>();
        if (cancellation != null) {
            extra.put(CANCELLATION_NUMBER, cancellation.getRequestNumber());
            extra.put(CANCELLATION_REASON, cancellation.getReason());
            extra.put("rejectionReason", cancellation.getRejectionReason());
            extra.put(ADMIN_NOTES, cancellation.getAdminNotes());
            extra.put("resolvedOn", formatOrderDate(cancellation.getResolvedAt()));
            extra.put(RESOLVED_ON_FORMATTED, formatOrderDate(cancellation.getResolvedAt()));
        }
        sendOrderTemplate(order, "mail/cancellationRejectedEmail", "email.order.cancel.rejected.title", extra);
    }

    // Trigger 7: Order Cancelled by Store
    public void sendOrderCancelled(CustomerOrder order) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put(CANCELLATION_REASON, order != null ? StringUtils.defaultString(order.getCancellationReason()) : "");
        extra.put("resolvedOnFormatted", formatOrderDate(Instant.now()));
        boolean refundedOnline =
            order != null &&
            (order.getPaymentStatus() == PaymentStatus.REFUNDED ||
                order.getPaymentStatus() == PaymentStatus.REFUND_INITIATED) &&
            !"cod".equalsIgnoreCase(order.getPaymentMethod());
        extra.put("hasRefund", refundedOnline && isPositive(order.getTotal()));
        if (refundedOnline) {
            extra.put(REFUND_AMOUNT, formatInr(order.getTotal()));
            extra.put(REFUND_AMOUNT_FORMATTED, formatInr(order.getTotal()));
        }
        sendOrderTemplate(order, "mail/orderCancelledEmail", "email.order.cancel.title", extra);
    }

    // Trigger 8: Refund Initiated
    public void sendRefundInitiated(CustomerOrder order, BigDecimal refundAmount, String refundId, String paymentMethod) {
        Map<String, Object> extra = new LinkedHashMap<>();
        BigDecimal amount = resolveRefundAmount(refundAmount, order);
        String method = displayPaymentMethod(resolvePaymentMethod(paymentMethod, order));
        extra.put(REFUND_AMOUNT, formatInr(amount));
        extra.put(REFUND_AMOUNT_FORMATTED, formatInr(amount));
        extra.put("refundDateFormatted", formatOrderDate(Instant.now()));
        extra.put("refundId", StringUtils.defaultString(refundId));
        extra.put(PAYMENT_METHOD, method);
        extra.put("paymentMethodDisplay", method);
        sendOrderTemplate(order, "mail/refundInitiatedEmail", "email.order.refund.initiated.title", extra);
    }

    // Trigger 9: Refund Completed
    public void sendRefundFailed(CustomerOrder order, BigDecimal refundAmount, String refundId, String paymentMethod) {
        Map<String, Object> extra = new LinkedHashMap<>();
        BigDecimal amount = resolveRefundAmount(refundAmount, order);
        String method = displayPaymentMethod(resolvePaymentMethod(paymentMethod, order));
        extra.put(REFUND_AMOUNT, formatInr(amount));
        extra.put(REFUND_AMOUNT_FORMATTED, formatInr(amount));
        extra.put("refundDateFormatted", formatOrderDate(Instant.now()));
        extra.put("refundId", StringUtils.defaultString(refundId));
        extra.put(PAYMENT_METHOD, method);
        extra.put("paymentMethodDisplay", method);
        sendOrderTemplate(order, "mail/refundFailedEmail", "email.order.refund.failed.title", extra);
    }

    public void sendRefundCompleted(CustomerOrder order, BigDecimal refundAmount, String refundId, String paymentMethod) {
        Map<String, Object> extra = new LinkedHashMap<>();
        BigDecimal amount = resolveRefundAmount(refundAmount, order);
        String method = displayPaymentMethod(resolvePaymentMethod(paymentMethod, order));
        extra.put(REFUND_AMOUNT, formatInr(amount));
        extra.put(REFUND_AMOUNT_FORMATTED, formatInr(amount));
        extra.put("refundDateFormatted", formatOrderDate(Instant.now()));
        extra.put("refundId", StringUtils.defaultString(refundId));
        extra.put(PAYMENT_METHOD, method);
        extra.put("paymentMethodDisplay", method);
        sendOrderTemplate(order, "mail/refundCompletedEmail", "email.order.refund.completed.title", extra);
    }

    private static BigDecimal resolveRefundAmount(BigDecimal refundAmount, CustomerOrder order) {
        if (refundAmount != null) {
            return refundAmount;
        }
        if (order != null && order.getTotal() != null) {
            return order.getTotal();
        }
        return BigDecimal.ZERO;
    }

    private static String resolvePaymentMethod(String paymentMethod, CustomerOrder order) {
        if (paymentMethod != null) {
            return paymentMethod;
        }
        if (order != null && order.getPaymentMethod() != null) {
            return order.getPaymentMethod();
        }
        return "";
    }

    // Trigger 10: Return Request Received
    public void sendReturnRequested(CustomerOrder order, OrderReturn orderReturn) {
        sendOrderTemplate(order, TEMPLATE_ORDER_RETURN, "email.order.return.requested.title", buildReturnExtra(orderReturn));
    }

    // Trigger 11: Return Accepted
    public void sendReturnAccepted(CustomerOrder order, OrderReturn orderReturn) {
        sendOrderTemplate(order, "mail/returnApprovedEmail", "email.order.return.approved.title", buildReturnExtra(orderReturn));
    }

    // Trigger 12: Return Rejected
    public void sendReturnRejected(CustomerOrder order, OrderReturn orderReturn) {
        sendOrderTemplate(order, "mail/returnRejectedEmail", "email.order.return.rejected.title", buildReturnExtra(orderReturn));
    }

    // Trigger 13: Pickup Scheduled
    public void sendPickupScheduled(CustomerOrder order, OrderReturn orderReturn) {
        sendOrderTemplate(order, "mail/pickupScheduledEmail", "email.order.return.pickup_scheduled.title", buildReturnExtra(orderReturn));
    }

    // Trigger 14: Out for Pickup
    public void sendOutForPickup(CustomerOrder order, OrderReturn orderReturn) {
        sendOrderTemplate(order, "mail/outForPickupEmail", "email.order.return.out_for_pickup.title", buildReturnExtra(orderReturn));
    }

    public void sendReturnReceived(CustomerOrder order, OrderReturn orderReturn) {
        boolean exchange = orderReturn != null && orderReturn.getRequestType() == OrderRequestType.EXCHANGE;
        sendOrderTemplate(
            order,
            "mail/returnReceivedEmail",
            exchange ? "email.order.exchange.received.title" : "email.order.return.received.title",
            buildReturnExtra(orderReturn)
        );
    }

    // Trigger 15: Return QC Failed
    public void sendReturnQcFailed(CustomerOrder order, OrderReturn orderReturn) {
        sendOrderTemplate(order, "mail/returnQcFailedEmail", "email.order.return.qc_failed.title", buildReturnExtra(orderReturn));
    }

    // Trigger 16: Exchange Accepted
    public void sendExchangeAccepted(CustomerOrder order, OrderReturn orderReturn) {
        sendOrderTemplate(order, "mail/exchangeApprovedEmail", "email.order.exchange.approved.title", buildReturnExtra(orderReturn));
    }

    // Trigger 17: Exchange Rejected
    public void sendExchangeRejected(CustomerOrder order, OrderReturn orderReturn) {
        sendOrderTemplate(order, "mail/exchangeRejectedEmail", "email.order.exchange.rejected.title", buildReturnExtra(orderReturn));
    }

    /** Diagram: pickup failure → Admin → "Exchange cancelled." */
    public void sendExchangeCancelled(CustomerOrder order, OrderReturn orderReturn) {
        sendOrderTemplate(
            order,
            "mail/exchangeCancelledEmail",
            "email.order.exchange.cancelled.title",
            buildReturnExtra(orderReturn)
        );
    }

    // Trigger 18: Exchange Out for Delivery
    public void sendExchangeOutForDelivery(CustomerOrder order, OrderReturn orderReturn) {
        Map<String, Object> extra = new LinkedHashMap<>(buildReturnExtra(orderReturn));
        if (orderReturn != null) {
            extra.put(TRACKING_URL, StringUtils.trimToNull(orderReturn.getOutboundTrackingUrl()));
            extra.put("trackingNumber", StringUtils.defaultString(orderReturn.getOutboundAwb()));
            extra.put("trackingCarrier", StringUtils.defaultString(orderReturn.getOutboundCarrierName()));
        }
        sendOrderTemplate(order, "mail/exchangeOutForDeliveryEmail", "email.order.exchange.shipped.title", extra);
    }

    // Trigger 19: Exchange Delivered
    public void sendExchangeDelivered(CustomerOrder order, OrderReturn orderReturn) {
        sendOrderTemplate(order, "mail/exchangeDeliveredEmail", "email.order.exchange.delivered.title", buildReturnExtra(orderReturn));
    }

    // Exchange Requested
    public void sendExchangeRequested(CustomerOrder order, OrderReturn orderReturn) {
        sendOrderTemplate(order, "mail/exchangeRequestedEmail", "email.order.exchange.requested.title", buildReturnExtra(orderReturn));
    }

    public void sendReturnUpdate(CustomerOrder order, OrderReturn orderReturn) {
        if (orderReturn == null || orderReturn.getStatus() == OrderReturnStatus.FAILED) {
            return;
        }
        boolean isExchange = orderReturn.getRequestType() == OrderRequestType.EXCHANGE;
        OrderReturnStatus status = orderReturn.getStatus();
        if (status == null) {
            status = OrderReturnStatus.REQUESTED;
        }

        if (isExchange) {
            switch (status) {
                case REQUESTED -> sendExchangeRequested(order, orderReturn);
                case APPROVED -> sendExchangeAccepted(order, orderReturn);
                case REJECTED -> sendExchangeRejected(order, orderReturn);
                case CANCELLED -> sendExchangeCancelled(order, orderReturn);
                case PICKUP_REQUESTED, PICKUP_SCHEDULED -> sendPickupScheduled(order, orderReturn);
                case PICKED_UP -> sendOutForPickup(order, orderReturn);
                case RECEIVED -> sendReturnReceived(order, orderReturn);
                case SHIPPED -> {
                    // Exchange OFD mail is owned by OrderReturnService.notifyExchangeOutForDelivery
                    // (non-XB at markShipped; XB on the carrier OFD webhook) — never from status sync here.
                }
                case DELIVERED, COMPLETED -> sendExchangeDelivered(order, orderReturn);
                case REFUNDED -> sendRefundOutcome(order, orderReturn);
                case PICKUP_FAILED, INSPECTED, ACCEPTED, ITEM_ALLOCATED, REPACKING, READY_TO_SHIP, FAILED -> {
                    // No customer mail. Listed so a new status fails compilation instead of sending the wrong template.
                }
            }
        } else {
            switch (status) {
                case REQUESTED -> sendOrderTemplate(order, TEMPLATE_ORDER_RETURN, "email.order.return.requested.title", buildReturnExtra(orderReturn));
                case APPROVED -> sendReturnAccepted(order, orderReturn);
                case REJECTED -> {
                    if (isQcFailure(orderReturn)) {
                        sendReturnQcFailed(order, orderReturn);
                    } else {
                        sendReturnRejected(order, orderReturn);
                    }
                }
                case PICKUP_SCHEDULED -> sendPickupScheduled(order, orderReturn);
                case PICKED_UP -> sendOutForPickup(order, orderReturn);
                case RECEIVED -> sendReturnReceived(order, orderReturn);
                case REFUNDED -> sendRefundOutcome(order, orderReturn);
                default -> sendOrderTemplate(order, TEMPLATE_ORDER_RETURN, "email.order.return." + returnStatusKey(status) + ".title", buildReturnExtra(orderReturn));
            }
        }
    }

    private void sendRefundOutcome(CustomerOrder order, OrderReturn orderReturn) {
        BigDecimal refundAmt = orderReturn.getRefundAmount();
        if (refundAmt != null && refundAmt.signum() <= 0) {
            // Convert-to-refund after the price difference was already paid out records 0.
            // Do not tell the customer a refund was initiated or completed.
            return;
        }
        if (refundAmt == null && order != null) {
            refundAmt = order.getTotal();
        }
        String refundId = StringUtils.firstNonBlank(orderReturn.getRazorpayRefundId(), orderReturn.getManualRefundReference());
        String paymentMethod = order != null ? order.getPaymentMethod() : null;
        if (StringUtils.isNotBlank(orderReturn.getRazorpayRefundId()) && orderReturn.getManualRefundRecordedAt() == null) {
            if (order != null && order.getPaymentStatus() == PaymentStatus.REFUNDED) {
                sendRefundCompleted(order, refundAmt, refundId, paymentMethod);
                return;
            }
            sendRefundInitiated(order, refundAmt, refundId, paymentMethod);
            return;
        }
        sendRefundCompleted(order, refundAmt, refundId, paymentMethod);
    }

    private boolean isQcFailure(OrderReturn orderReturn) {
        if (orderReturn == null) {
            return false;
        }
        String notes = StringUtils.defaultString(orderReturn.getAdminNotes()).toLowerCase(Locale.ROOT);
        String reason = StringUtils.defaultString(orderReturn.getRejectionReason()).toLowerCase(Locale.ROOT);
        return notes.contains("qc") || notes.contains("quality") || notes.contains("inspect")
            || reason.contains("qc") || reason.contains("quality") || reason.contains("inspect")
            || reason.contains("used") || reason.contains("damage") || reason.contains("tag");
    }

    private Map<String, Object> buildReturnExtra(OrderReturn orderReturn) {
        if (orderReturn == null) {
            return Map.of();
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        String statusKey = returnStatusKey(orderReturn.getStatus());
        extra.put("returnNumber", StringUtils.defaultString(orderReturn.getReturnNumber()));
        extra.put("requestType", orderReturn.getRequestType() == null ? "" : orderReturn.getRequestType().name());
        extra.put("returnStatus", statusKey);
        extra.put("returnReason", StringUtils.defaultString(orderReturn.getReason()));
        extra.put("exchangeReason", StringUtils.defaultString(orderReturn.getReason()));
        extra.put("rejectionReason", StringUtils.defaultString(orderReturn.getRejectionReason()));
        extra.put("adminNotes", StringUtils.defaultString(orderReturn.getAdminNotes()));
        extra.put("qcReason", StringUtils.defaultString(orderReturn.getRejectionReason()));
        extra.put("pickupWindow", StringUtils.defaultString(orderReturn.getPickupWindow(), "Within 2-3 business days"));
        extra.put("reverseAwb", StringUtils.defaultString(orderReturn.getReverseAwb()));
        extra.put("carrierName", StringUtils.defaultString(orderReturn.getCarrierName()));
        extra.put(TRACKING_URL, StringUtils.trimToNull(orderReturn.getTrackingUrl()));
        extra.put("requestedOn", formatOrderDate(orderReturn.getRequestedAt()));
        extra.put("requestedOnFormatted", formatOrderDate(orderReturn.getRequestedAt()));
        extra.put("reviewedOn", formatOrderDate(orderReturn.getResolvedAt()));
        extra.put(RESOLVED_ON_FORMATTED, formatOrderDate(orderReturn.getResolvedAt()));
        if (orderReturn.getRefundAmount() != null) {
            extra.put(REFUND_AMOUNT_FORMATTED, formatInr(orderReturn.getRefundAmount()));
        }
        extra.put("receivedOn", formatOrderDate(orderReturn.getResolvedAt() != null ? orderReturn.getResolvedAt() : Instant.now()));
        String baseUrl = StringUtils.defaultIfBlank(jHipsterProperties.getMail().getBaseUrl(), DEFAULT_BASE_URL);
        String returnNum = StringUtils.defaultString(orderReturn.getReturnNumber());
        extra.put("returnUrl", trimTrailingSlash(baseUrl) + "/returns/" + returnNum);
        extra.put("exchangeUrl", trimTrailingSlash(baseUrl) + "/exchanges/" + returnNum);

        if (orderReturn.getOrderItem() != null) {
            OrderItem oi = orderReturn.getOrderItem();
            extra.put("originalItemName", StringUtils.defaultString(oi.getProductName()));
            Map<String, String> row = lineItemRow(oi);
            String repl = joinNonBlank(" · ", orderReturn.getReplacementSize(), orderReturn.getReplacementColor(), orderReturn.getReplacementVariantSku());
            if (StringUtils.isNotBlank(repl)) {
                row.put("exchangeNote", repl);
            }
            extra.put("items", List.of(row));
        }

        return extra;
    }

    public void sendForwardCarrierCancelledAdminNotice(CustomerOrder order) {
        if (order == null) {
            return;
        }
        String to = resolveAdminRecipient();
        if (StringUtils.isBlank(to)) {
            LOG.warn(
                "ExpressBees cancelled shipment for order {} but no admin contact email is configured",
                order.getOrderNumber()
            );
            return;
        }
        String orderNumber = StringUtils.defaultString(order.getOrderNumber());
        String awb = StringUtils.defaultString(order.getTrackingNumber());
        String baseUrl = StringUtils.defaultIfBlank(jHipsterProperties.getMail().getBaseUrl(), DEFAULT_BASE_URL);
        String adminUrl = trimTrailingSlash(baseUrl) + "/admin/orders";
        String content =
            "<p>ExpressBees cancelled the forward shipment for order <strong>" +
            escape(orderNumber) +
            "</strong>" +
            (StringUtils.isNotBlank(awb) ? " (AWB <strong>" + escape(awb) + "</strong>)" : "") +
            ".</p><p>The customer order is still open. <a href=\"" +
            escape(adminUrl) +
            "\">Open orders</a> to rebook a shipment or cancel the order.</p>";
        sendHtml(to, "ExpressBees cancelled shipment · order " + orderNumber, wrapAdminNotice(content));
    }

    public void sendPickupFailedAdminNotice(CustomerOrder order, OrderReturn orderReturn) {
        if (order == null || orderReturn == null) {
            return;
        }
        String to = resolveAdminRecipient();
        if (StringUtils.isBlank(to)) {
            LOG.warn(
                "Pickup failed for return {} / order {} but no admin contact email is configured",
                orderReturn.getReturnNumber(),
                order.getOrderNumber()
            );
            return;
        }
        String returnNumber = StringUtils.defaultString(orderReturn.getReturnNumber());
        String orderNumber = StringUtils.defaultString(order.getOrderNumber());
        String reason = StringUtils.defaultIfBlank(orderReturn.getPickupFailureReason(), "Pickup failed");
        String baseUrl = StringUtils.defaultIfBlank(jHipsterProperties.getMail().getBaseUrl(), DEFAULT_BASE_URL);
        String adminUrl = trimTrailingSlash(baseUrl) + "/admin/returns";
        String content =
            "<p>ExpressBees could not complete pickup for return <strong>" +
            escape(returnNumber) +
            "</strong> (order <strong>" +
            escape(orderNumber) +
            "</strong>).</p><p>Reason: " +
            escape(reason) +
            "</p><p><a href=\"" +
            escape(adminUrl) +
            "\">Open order returns</a> to reschedule pickup or close the case.</p>";
        sendHtml(to, "Pickup failed · " + returnNumber + " · order " + orderNumber, wrapAdminNotice(content));
    }

    private String resolveAdminRecipient() {
        return siteSettingRepository
            .findFirstByOrderByIdAsc()
            .map(SiteSetting::getContactEmail)
            .filter(StringUtils::isNotBlank)
            .orElseGet(() -> StringUtils.trimToNull(jHipsterProperties.getMail().getFrom()));
    }

    private static String wrapAdminNotice(String body) {
        return "<html><body style=\"font-family:Georgia,serif;color:#4a3f45\">" + body + "</body></html>";
    }

    private static String escape(String value) {
        return StringUtils.defaultString(value)
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    private void sendOrderTemplate(CustomerOrder order, String templateName, String titleKey, Map<String, Object> extra) {
        if (order == null) {
            return;
        }
        String to = resolveRecipient(order);
        if (StringUtils.isBlank(to)) {
            LOG.debug("Skipping order email {}; no recipient for order '{}'", templateName, order.getOrderNumber());
            return;
        }
        try {
            Locale locale = DEFAULT_LOCALE;
            Context context = new Context(locale);
            String customerName = resolveCustomerName(order);
            String orderNumber = StringUtils.defaultString(order.getOrderNumber());
            String baseUrl = StringUtils.defaultIfBlank(jHipsterProperties.getMail().getBaseUrl(), DEFAULT_BASE_URL);
            String orderUrl = trimTrailingSlash(baseUrl) + "/account/orders/" + orderNumber;
            context.setVariable("baseUrl", baseUrl);
            context.setVariable("customerName", customerName);
            context.setVariable("orderNumber", orderNumber);
            context.setVariable("orderUrl", orderUrl);
            context.setVariable("supportEmail", StringUtils.defaultIfBlank(jHipsterProperties.getMail().getFrom(), "hello@minisnmore.com"));
            context.setVariable("placedOnFormatted", formatOrderDate(order.getPlacedAt()));
            context.setVariable("orderDate", formatOrderDate(order.getPlacedAt()));
            context.setVariable("totalFormatted", formatInr(order.getTotal()));
            context.setVariable("subtotalFormatted", formatInr(order.getSubtotal()));
            context.setVariable("hasDiscount", isPositive(order.getDiscount()));
            context.setVariable("discountFormatted", formatInr(order.getDiscount()));
            context.setVariable("hasShipping", isPositive(order.getShipping()));
            context.setVariable("shippingFormatted", formatInr(order.getShipping()));
            context.setVariable("hasCodFee", isPositive(order.getCodFee()));
            context.setVariable("codFeeFormatted", formatInr(order.getCodFee()));
            context.setVariable("hasTax", isPositive(order.getTax()));
            context.setVariable("taxFormatted", formatInr(order.getTax()));
            context.setVariable("hasDonation", isPositive(order.getDonation()));
            context.setVariable("donationFormatted", formatInr(order.getDonation()));
            context.setVariable("couponCode", StringUtils.defaultString(order.getCouponCode()));
            context.setVariable(PAYMENT_METHOD, displayPaymentMethod(order.getPaymentMethod()));
            context.setVariable("trackingNumber", StringUtils.defaultString(order.getTrackingNumber()));
            context.setVariable("trackingCarrier", StringUtils.defaultString(order.getTrackingCarrier()));
            context.setVariable(TRACKING_URL, StringUtils.trimToNull(order.getTrackingUrl()));
            context.setVariable("shippingAddress", formatAddress(order.getShippingAddressJson()));
            Map<String, Object> address = buildAddress(order.getShippingAddressJson());
            context.setVariable("addressName", address.get("name"));
            context.setVariable("addressPhone", address.get(FIELD_PHONE));
            context.setVariable("addressLines", address.get("lines"));
            context.setVariable("items", loadLineItems(order.getId()));
            extra.forEach(context::setVariable);
            if (templateUsesRefundTimeline(templateName) && !extra.containsKey("refundBusinessDays")) {
                putRefundBusinessDaysContext(context);
            }
            String content = templateEngine.process(templateName, context);
            String subject = messageSource.getMessage(titleKey, new Object[] { orderNumber }, locale);
            sendHtml(to, subject, content);
        } catch (RuntimeException ex) {
            LOG.warn("Order email '{}' could not be prepared for '{}'", templateName, order.getOrderNumber(), ex);
        }
    }

    private static boolean templateUsesRefundTimeline(String templateName) {
        if (templateName == null) {
            return false;
        }
        return (
            templateName.contains("refundInitiatedEmail") ||
            templateName.contains("refundCompletedEmail") ||
            templateName.contains("orderCancelledEmail") ||
            templateName.contains("cancellationAcceptedEmail")
        );
    }

    private void putRefundBusinessDaysContext(Context context) {
        SiteSetting settings = siteSettingRepository.findFirstByOrderByIdAsc().orElse(null);
        int refundMin = SiteSetting.resolveRefundMinBusinessDays(settings != null ? settings.getRefundMinBusinessDays() : null);
        int refundMax = SiteSetting.resolveRefundMaxBusinessDays(
            refundMin,
            settings != null ? settings.getRefundMaxBusinessDays() : null
        );
        context.setVariable("refundBusinessDays", SiteSetting.formatRefundBusinessDaysLabel(refundMin, refundMax));
    }

    private void sendHtml(String to, String subject, String content) {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        try {
            MimeMessageHelper message = new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());
            message.setTo(to);
            message.setFrom(jHipsterProperties.getMail().getFrom());
            message.setSubject(subject);
            message.setText(content, true);
            MailTimestamps.stampSentDate(mimeMessage, LOG);
            javaMailSender.send(mimeMessage);
            LOG.info("Sent order email to '{}' with subject '{}'", to, subject);
        } catch (MailException | MessagingException e) {
            LOG.warn("Order email could not be sent to '{}'", to, e);
        }
    }

    String resolveRecipient(CustomerOrder order) {
        JsonNode address = parseAddress(order.getShippingAddressJson());
        String fromAddress = validEmail(text(address, "email"));
        if (fromAddress != null) {
            return fromAddress;
        }
        return validEmail(order.getUserLogin());
    }

    String resolveCustomerName(CustomerOrder order) {
        JsonNode address = parseAddress(order.getShippingAddressJson());
        String fullName = text(address, "full_name");
        if (StringUtils.isNotBlank(fullName)) {
            return fullName.trim();
        }
        String email = resolveRecipient(order);
        if (StringUtils.contains(email, "@")) {
            return email.substring(0, email.indexOf('@'));
        }
        return StringUtils.defaultIfBlank(order.getUserLogin(), "Customer");
    }

    private List<Map<String, String>> loadLineItems(Long orderId) {
        List<Map<String, String>> rows = new ArrayList<>();
        if (orderId == null) {
            return rows;
        }
        List<OrderItem> items = orderItemRepository.findAllByOrderId(orderId);
        for (OrderItem item : items) {
            rows.add(lineItemRow(item));
        }
        return rows;
    }

    /** Order and return templates read different keys (name/qty vs title/quantity); SpEL fails on a missing map key. */
    private Map<String, String> lineItemRow(OrderItem item) {
        String name = StringUtils.defaultString(item.getProductName());
        String qty = item.getQty() == null ? "1" : String.valueOf(item.getQty());
        Map<String, String> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("title", name);
        row.put("details", joinNonBlank(" · ", item.getItemSize(), item.getColor()));
        row.put("qty", qty);
        row.put("quantity", qty);
        row.put("unitPrice", formatInr(item.getPrice()));
        row.put("amount", formatInr(lineAmount(item)));
        row.put("image", StringUtils.defaultString(item.getProductImage()));
        row.put("exchangeNote", "");
        return row;
    }

    private static BigDecimal lineAmount(OrderItem item) {
        BigDecimal price = item.getPrice() == null ? BigDecimal.ZERO : item.getPrice();
        int qty = item.getQty() == null ? 1 : item.getQty();
        return price.multiply(BigDecimal.valueOf(qty));
    }

    private JsonNode parseAddress(String json) {
        if (StringUtils.isBlank(json)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private String formatAddress(String json) {
        JsonNode address = parseAddress(json);
        return joinNonBlank(
            ", ",
            text(address, "line1"),
            text(address, "line2"),
            text(address, "city"),
            text(address, "state"),
            text(address, "pincode")
        );
    }

    private Map<String, Object> buildAddress(String json) {
        JsonNode node = parseAddress(json);
        List<String> lines = new ArrayList<>();
        String line1 = text(node, "line1");
        String line2 = text(node, "line2");
        String city = text(node, "city");
        String stateZip = joinNonBlank(" - ", text(node, "state"), text(node, "pincode"));
        if (StringUtils.isNotBlank(line1)) {
            lines.add(line1);
        }
        if (StringUtils.isNotBlank(line2)) {
            lines.add(line2);
        }
        if (StringUtils.isNotBlank(city)) {
            lines.add(city);
        }
        if (StringUtils.isNotBlank(stateZip)) {
            lines.add(stateZip);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", text(node, "full_name"));
        result.put(FIELD_PHONE, text(node, FIELD_PHONE));
        result.put("lines", lines);
        return result;
    }

    private static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static String formatOrderDate(Instant instant) {
        return MailTimestamps.format(instant);
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || !node.has(field)) {
            return "";
        }
        return StringUtils.trimToEmpty(node.path(field).asText(""));
    }

    private static String validEmail(String value) {
        String email = StringUtils.trimToNull(value);
        if (email == null || !email.contains("@") || email.length() > 160) {
            return null;
        }
        return email;
    }

    private static String formatInr(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        return "₹" + value.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private static String displayPaymentMethod(String paymentMethod) {
        if ("cod".equalsIgnoreCase(StringUtils.trimToEmpty(paymentMethod))) {
            return "Cash on Delivery";
        }
        return "Online payment";
    }

    private static String trimTrailingSlash(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    private static String joinNonBlank(String separator, String... parts) {
        List<String> present = new ArrayList<>();
        for (String part : parts) {
            if (StringUtils.isNotBlank(part)) {
                present.add(part.trim());
            }
        }
        return String.join(separator, present);
    }

    private static String returnStatusKey(OrderReturnStatus status) {
        if (status == null) {
            return "requested";
        }
        return status.name().toLowerCase(Locale.ROOT);
    }
}
