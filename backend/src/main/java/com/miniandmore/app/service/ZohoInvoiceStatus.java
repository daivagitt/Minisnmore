package com.miniandmore.app.service;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import java.time.Instant;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/**
 * Maps Zoho Books invoice fields onto store order invoice/tracking columns.
 */
public final class ZohoInvoiceStatus {

    private ZohoInvoiceStatus() {}

    /**
     * Applies invoice status from a Zoho invoice payload or GET /invoices response.
     *
     * @return true when {@link OrderStatus} changed (e.g. void cancellation)
     */
    public static boolean applyInvoice(CustomerOrder order, Map<String, Object> invoice) {
        OrderStatus statusBefore = order.getStatus();
        String invoiceId = ZohoWebhookSupport.firstNonBlank(
            ZohoWebhookSupport.stringValue(invoice.get("invoice_id")),
            ZohoWebhookSupport.stringValue(invoice.get("invoiceId")),
            ZohoWebhookSupport.stringValue(invoice.get("zohoInvoiceId"))
        );
        if (StringUtils.isNotBlank(invoiceId) && StringUtils.isBlank(order.getZohoInvoiceId())) {
            order.setZohoInvoiceId(invoiceId.trim());
        }
        String status = ZohoWebhookSupport.firstNonBlank(ZohoWebhookSupport.stringValue(invoice.get("status")));
        if (StringUtils.isNotBlank(status) && !shouldPreserveExistingZohoOrderStatus(order.getZohoOrderStatus(), status)) {
            order.setZohoOrderStatus(status);
        }
        order.setZohoTrackingLastPulledAt(Instant.now());
        if (isVoid(status)) {
            if (order.getStatus() != OrderStatus.CANCELLED && order.getStatus() != OrderStatus.FULFILLED) {
                order.setStatus(OrderStatus.CANCELLED);
                if (StringUtils.isBlank(order.getCancellationReason())) {
                    order.setCancellationReason(
                        ZohoWebhookSupport.firstNonBlank(
                            ZohoWebhookSupport.stringValue(invoice.get("reason")),
                            "Invoice voided in Zoho"
                        )
                    );
                }
            }
        }
        return order.getStatus() != statusBefore;
    }

    public static boolean isVoid(String status) {
        String normalized = normalize(status);
        return "void".equals(normalized) || "voided".equals(normalized);
    }

    public static boolean shouldCachePdf(String status) {
        String normalized = normalize(status);
        return "sent".equals(normalized) || "paid".equals(normalized);
    }

    /**
     * Invoice statuses (sent/paid/draft) must not clobber sales-order fulfillment statuses
     * already stored in {@code zoho_order_status} (shipped/in_transit/fulfilled).
     */
    static boolean shouldPreserveExistingZohoOrderStatus(String existingStatus, String incomingStatus) {
        if (StringUtils.isBlank(existingStatus) || isVoid(incomingStatus) || !isInvoiceOnlyStatus(incomingStatus)) {
            return false;
        }
        return isFulfillmentStatus(existingStatus);
    }

    private static boolean isInvoiceOnlyStatus(String status) {
        String normalized = normalize(status);
        if (normalized == null) {
            return false;
        }
        return (
            "sent".equals(normalized) ||
            "paid".equals(normalized) ||
            "draft".equals(normalized) ||
            "overdue".equals(normalized) ||
            "partially_paid".equals(normalized) ||
            "unpaid".equals(normalized)
        );
    }

    private static boolean isFulfillmentStatus(String status) {
        String normalized = normalize(status);
        if (normalized == null) {
            return false;
        }
        return ZohoOrderStatus.isShipped(status) || ZohoOrderStatus.isDelivered(status) || "fulfilled".equals(normalized);
    }

    private static String normalize(String value) {
        String trimmed = StringUtils.trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase();
    }
}
