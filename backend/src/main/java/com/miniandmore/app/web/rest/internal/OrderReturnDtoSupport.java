package com.miniandmore.app.web.rest.internal;

import com.miniandmore.app.domain.OrderItem;
import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.OrderReturnItem;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared snake_case return payload fields for customer and admin APIs. */
final class OrderReturnDtoSupport {

    private OrderReturnDtoSupport() {}

    static Map<String, Object> toLineDto(OrderReturnItem line) {
        var data = new LinkedHashMap<String, Object>();
        OrderItem item = line.getOrderItem();
        if (item == null) {
            return data;
        }
        data.put("id", line.getId());
        data.put("order_item_id", item.getId());
        data.put("product_name", item.getProductName());
        data.put("product_slug", item.getProductSlug());
        data.put("product_image", item.getProductImage());
        data.put("size", item.getItemSize());
        data.put("color", item.getColor());
        data.put("sku", item.getVariantSku());
        data.put("variant_sku", item.getVariantSku());
        data.put("requested_qty", line.getRequestedQty());
        data.put("received_qty", line.getReceivedQty());
        data.put("accepted_qty", line.getAcceptedQty());
        data.put("unit_amount", line.getUnitAmount());
        data.put("gross_amount", line.getGrossAmount());
        data.put("discount_allocation", line.getDiscountAllocation());
        data.put("refund_amount", line.getRefundAmount());
        data.put("disposition", line.getDisposition() == null ? null : line.getDisposition().name().toLowerCase());
        return data;
    }

    static void putOriginalItemFields(Map<String, Object> data, OrderReturn orderReturn, List<Map<String, Object>> lines) {
        OrderItem item = orderReturn.getOrderItem();
        if (item != null) {
            data.put("order_item_id", item.getId());
            data.put("product_name", item.getProductName());
            data.put("product_slug", item.getProductSlug());
            data.put("product_image", item.getProductImage());
            data.put("original_size", item.getItemSize());
            data.put("original_color", item.getColor());
            data.put("sku", item.getVariantSku());
            data.put("variant_sku", item.getVariantSku());
            return;
        }
        if (lines == null || lines.isEmpty()) {
            return;
        }
        Map<String, Object> first = lines.get(0);
        data.put("order_item_id", first.get("order_item_id"));
        data.put("product_name", first.get("product_name"));
        data.put("product_slug", first.get("product_slug"));
        data.put("product_image", first.get("product_image"));
        data.put("original_size", first.get("size"));
        data.put("original_color", first.get("color"));
        data.put("sku", first.get("sku"));
        data.put("variant_sku", first.get("variant_sku"));
    }
}
