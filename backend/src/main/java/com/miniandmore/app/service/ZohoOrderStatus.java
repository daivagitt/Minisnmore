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
        if (
            order.getStatus() == OrderStatus.CONFIRMED &&
            !hasShippedSignal(salesOrder) &&
            hasPositiveLineQuantity(salesOrder, "quantity_packed")
        ) {
            order.setStatus(OrderStatus.HAND_WRAPPED);
        }
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

    /** A Zoho package is the operations-owned proof that wrapping/packing is complete. */
    public static void applyPackages(CustomerOrder order, List<Map<String, Object>> packages) {
        if (packages == null || packages.isEmpty() || order.getStatus() == OrderStatus.CANCELLED) {
            return;
        }
        for (Map<String, Object> pkg : packages) {
            String zohoStatus = firstNonBlank(
                stringValue(pkg.get("status")),
                stringValue(pkg.get("detailed_status")),
                stringValue(pkg.get("status_message"))
            );
            if (StringUtils.isNotBlank(zohoStatus)) {
                order.setZohoOrderStatus(zohoStatus);
            }
            String trackingNumber = firstNonBlank(
                stringValue(pkg.get("tracking_number")),
                stringValue(pkg.get("tracking_number_ext"))
            );
            if (StringUtils.isNotBlank(trackingNumber)) {
                order.setTrackingNumber(trackingNumber);
            }
            String carrier = firstNonBlank(stringValue(pkg.get("carrier")), stringValue(pkg.get("delivery_method")));
            if (StringUtils.isNotBlank(carrier)) {
                order.setTrackingCarrier(carrier);
            }
            String packageShipmentDate = stringValue(pkg.get("shipment_date"));
            if (StringUtils.isNotBlank(packageShipmentDate)) {
                order.setShipmentDate(packageShipmentDate.trim());
            }
        }
        if (packages.stream().anyMatch(ZohoOrderStatus::isFulfilledShipment)) {
            order.setStatus(OrderStatus.FULFILLED);
            return;
        }
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            return;
        }
        String trackingStatus = OrderTrackingStatusResolver.resolve(order);
        if ("shipped".equals(trackingStatus) || "delivered".equals(trackingStatus)) {
            return;
        }
        order.setStatus(OrderStatus.HAND_WRAPPED);
    }

    private static boolean isFulfilledShipment(Map<String, Object> pkg) {
        String status = normalize(stringValue(pkg.get("status")));
        String detailedStatus = normalize(stringValue(pkg.get("detailed_status")));
        String statusMessage = normalize(stringValue(pkg.get("status_message")));
        if ("delivered".equals(status) || "delivered".equals(detailedStatus) || "delivered".equals(statusMessage)) {
            return true;
        }
        // A sales order or invoice can become fulfilled during accounting. A package can only
        // become fulfilled after it has been converted to a shipment, identified by shipment_id.
        return "fulfilled".equals(status) && StringUtils.isNotBlank(stringValue(pkg.get("shipment_id")));
    }

    public static boolean isShipped(String zohoOrderStatus) {
        String normalized = normalize(zohoOrderStatus);
        return normalized != null
            && (normalized.equals("shipped")
                || normalized.equals("dispatched")
                || normalized.equals("in_transit")
                || normalized.equals("partially_shipped"));
    }

    public static boolean isDelivered(String zohoOrderStatus) {
        String normalized = normalize(zohoOrderStatus);
        // Zoho can report a sales order as "fulfilled" as a side effect of invoice/payment
        // processing. Only its explicit delivery state is safe to expose as delivered.
        return "delivered".equals(normalized);
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
        String status = normalize(stringValue(salesOrder.get("status")));
        if ("void".equals(status)) {
            order.setStatus(OrderStatus.CANCELLED);
            return;
        }
        String shippedStatus = normalize(stringValue(salesOrder.get("shipped_status")));
        // "fulfilled" is not delivery evidence: Zoho can set both the main sales-order status and
        // shipped_status to fulfilled while converting an order to an invoice or collecting its
        // payment. Delivery challans are handled separately by applyDeliveryChallans. Preserve an
        // explicit "delivered" signal here for Zoho organizations that expose one directly.
        if ("delivered".equals(status) || "delivered".equals(shippedStatus)) {
            order.setStatus(OrderStatus.FULFILLED);
            return;
        }
        if (order.getStatus() == OrderStatus.PENDING && order.getPaymentStatus() == PaymentStatus.PAID) {
            order.setStatus(OrderStatus.CONFIRMED);
        }
    }

    private static boolean hasShippedSignal(Map<String, Object> salesOrder) {
        if (StringUtils.isNotBlank(stringValue(salesOrder.get("shipment_date")))) {
            return true;
        }
        if (hasPositiveLineQuantity(salesOrder, "quantity_shipped")) {
            return true;
        }
        String shippedStatus = normalize(stringValue(salesOrder.get("shipped_status")));
        if (
            shippedStatus != null &&
            !shippedStatus.equals("pending") &&
            !shippedStatus.equals("not_shipped") &&
            !shippedStatus.equals("fulfilled") &&
            !shippedStatus.equals("delivered")
        ) {
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

    private static boolean hasPositiveLineQuantity(Map<String, Object> salesOrder, String field) {
        Object raw = salesOrder.get("line_items");
        if (!(raw instanceof List<?> lines)) {
            return false;
        }
        for (Object lineObj : lines) {
            if (!(lineObj instanceof Map<?, ?> line)) {
                continue;
            }
            Object value = line.get(field);
            if (value instanceof Number number && number.doubleValue() > 0) {
                return true;
            }
            if (value != null) {
                try {
                    if (Double.parseDouble(value.toString()) > 0) {
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                    // Ignore malformed quantities from optional Zoho response fields.
                }
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
