package com.miniandmore.app.service;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.enumeration.OrderStatus;
import com.miniandmore.app.domain.enumeration.PaymentStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/**
 * Maps Zoho Books sales order / delivery challan fields onto store order tracking columns.
 */
public final class ZohoOrderStatus {

    private ZohoOrderStatus() {}

    public static void applySalesOrderTracking(CustomerOrder order, Map<String, Object> salesOrder) {
        String status = stringValue(salesOrder.get("status"));
        if (StringUtils.isNotBlank(status)) {
            order.setZohoOrderStatus(status);
        }
        String shipmentDate = stringValue(salesOrder.get("shipment_date"));
        if (StringUtils.isNotBlank(shipmentDate)) {
            order.setShipmentDate(shipmentDate.trim());
        }
        String deliveryMethod = stringValue(salesOrder.get("delivery_method"));
        if (StringUtils.isNotBlank(deliveryMethod)) {
            order.setTrackingCarrier(deliveryMethod.trim());
        }
        String trackingNumber = stringValue(salesOrder.get("tracking_number"));
        if (StringUtils.isNotBlank(trackingNumber)) {
            order.setTrackingNumber(trackingNumber.trim());
        }
        applyCustomFields(order, salesOrder);
        if (hasShippedSignal(salesOrder) && StringUtils.isBlank(order.getShipmentDate())) {
            String fallbackDate = firstNonBlank(shipmentDate, stringValue(salesOrder.get("date")));
            order.setShipmentDate(StringUtils.isNotBlank(fallbackDate) ? fallbackDate.trim() : LocalDate.now().toString());
        }
        updateOrderStatusFromZoho(order, salesOrder);
    }

    @SuppressWarnings("unchecked")
    public static void applyDeliveryChallans(CustomerOrder order, List<Map<String, Object>> challans) {
        if (challans == null || challans.isEmpty()) {
            return;
        }
        boolean delivered = false;
        for (Map<String, Object> challan : challans) {
            String status = StringUtils.defaultString(stringValue(challan.get("status"))).toLowerCase();
            if ("delivered".equals(status)) {
                delivered = true;
            }
            String trackingNumber = firstNonBlank(
                stringValue(challan.get("tracking_number")),
                stringValue(challan.get("reference_number"))
            );
            if (StringUtils.isNotBlank(trackingNumber)) {
                order.setTrackingNumber(trackingNumber);
            }
            String carrier = stringValue(challan.get("delivery_method"));
            if (StringUtils.isNotBlank(carrier)) {
                order.setTrackingCarrier(carrier);
            }
            String challanShipmentDate = stringValue(challan.get("shipment_date"));
            if (StringUtils.isNotBlank(challanShipmentDate)) {
                order.setShipmentDate(challanShipmentDate.trim());
            }
        }
        if (delivered && order.getStatus() != OrderStatus.CANCELLED) {
            order.setStatus(OrderStatus.FULFILLED);
        }
    }

    public static boolean isShipped(String zohoOrderStatus) {
        String normalized = normalize(zohoOrderStatus);
        return normalized != null
            && (normalized.equals("shipped")
                || normalized.equals("fulfilled")
                || normalized.equals("dispatched")
                || normalized.equals("in_transit")
                || normalized.equals("partially_shipped"));
    }

    public static boolean isDelivered(String zohoOrderStatus) {
        String normalized = normalize(zohoOrderStatus);
        return normalized != null && normalized.equals("delivered");
    }

    @SuppressWarnings("unchecked")
    private static void applyCustomFields(CustomerOrder order, Map<String, Object> salesOrder) {
        Object raw = salesOrder.get("custom_fields");
        if (!(raw instanceof List<?> fields)) {
            return;
        }
        for (Object fieldObj : fields) {
            if (!(fieldObj instanceof Map<?, ?> field)) {
                continue;
            }
            String label = StringUtils.defaultString(stringValue(field.get("label"))).toLowerCase();
            String value = stringValue(field.get("value"));
            if (StringUtils.isBlank(value)) {
                continue;
            }
            if (label.contains("tracking") || label.contains("awb")) {
                order.setTrackingNumber(value.trim());
            } else if (label.contains("carrier") || label.contains("courier")) {
                order.setTrackingCarrier(value.trim());
            }
        }
    }

    private static void updateOrderStatusFromZoho(CustomerOrder order, Map<String, Object> salesOrder) {
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.FULFILLED) {
            return;
        }
        String status = StringUtils.defaultString(stringValue(salesOrder.get("status"))).toLowerCase();
        if ("void".equals(status)) {
            order.setStatus(OrderStatus.CANCELLED);
            return;
        }
        // Zoho "closed" / "invoiced" means the sales order is fully billed — not that the parcel was delivered.
        // Fulfillment is set only from delivery challans (see applyDeliveryChallans).
        if (order.getStatus() == OrderStatus.PENDING && order.getPaymentStatus() == PaymentStatus.PAID) {
            order.setStatus(OrderStatus.CONFIRMED);
        }
    }

    private static boolean hasShippedSignal(Map<String, Object> salesOrder) {
        if (StringUtils.isNotBlank(stringValue(salesOrder.get("shipment_date")))) {
            return true;
        }
        String shippedStatus = normalize(stringValue(salesOrder.get("shipped_status")));
        if (shippedStatus != null && !shippedStatus.equals("pending") && !shippedStatus.equals("not_shipped")) {
            return true;
        }
        Object raw = salesOrder.get("sub_statuses");
        if (!(raw instanceof List<?> subStatuses)) {
            return false;
        }
        for (Object subObj : subStatuses) {
            if (!(subObj instanceof Map<?, ?> subStatus)) {
                continue;
            }
            String label = firstNonBlank(
                stringValue(subStatus.get("display_name")),
                stringValue(subStatus.get("description")),
                stringValue(subStatus.get("status_code"))
            ).toLowerCase();
            if (label.contains("ship") || label.contains("dispatch") || label.contains("transit")) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String status) {
        if (status == null) {
            return null;
        }
        String trimmed = status.trim().toLowerCase().replace('-', '_');
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
