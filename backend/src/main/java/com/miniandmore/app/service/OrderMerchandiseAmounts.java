package com.miniandmore.app.service;

import com.miniandmore.app.domain.CustomerOrder;
import com.miniandmore.app.domain.OrderItem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * Single source for line-level merchandise refund math (discount allocation, proration).
 */
public final class OrderMerchandiseAmounts {

    private OrderMerchandiseAmounts() {}

    public static BigDecimal merchandiseRefundForQuantities(
        CustomerOrder order,
        List<OrderItem> items,
        Map<Long, Integer> qtyByItemId
    ) {
        if (order == null || items == null || items.isEmpty() || qtyByItemId == null || qtyByItemId.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal subtotal = positiveOrFallback(
            order.getSubtotal(),
            items
                .stream()
                .map(item -> money(item.getPrice()).multiply(BigDecimal.valueOf(PackageShortShipService.orderedQty(item))))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
        );
        boolean persistedAllocations = orderHasPersistedLineAllocations(items);
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItem item : items) {
            Integer qty = item.getId() == null ? null : qtyByItemId.get(item.getId());
            if (qty == null || qty <= 0) {
                continue;
            }
            int ordered = PackageShortShipService.orderedQty(item);
            BigDecimal gross = money(item.getPrice()).multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal discount = allocatedDiscountFor(
                item,
                qty,
                ordered,
                subtotal,
                money(order.getDiscount()),
                persistedAllocations
            );
            total = total.add(gross.subtract(discount).max(BigDecimal.ZERO));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    public static boolean orderHasPersistedLineAllocations(List<OrderItem> orderItems) {
        return orderItems.stream().anyMatch(item -> money(item.getDiscountAllocation()).signum() > 0);
    }

    public static BigDecimal allocatedDiscountFor(
        OrderItem item,
        int requestedQty,
        int orderedQty,
        BigDecimal orderSubtotal,
        BigDecimal orderDiscount,
        boolean usePersistedLineAllocation
    ) {
        BigDecimal gross = money(item.getPrice()).multiply(BigDecimal.valueOf(requestedQty)).setScale(2, RoundingMode.HALF_UP);
        if (usePersistedLineAllocation) {
            return money(item.getDiscountAllocation())
                .multiply(BigDecimal.valueOf(requestedQty))
                .divide(BigDecimal.valueOf(Math.max(1, orderedQty)), 2, RoundingMode.HALF_UP)
                .min(gross);
        }
        if (orderSubtotal.signum() == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return orderDiscount.multiply(gross).divide(orderSubtotal, 2, RoundingMode.HALF_UP).min(gross);
    }

    public static BigDecimal positiveOrFallback(BigDecimal preferred, BigDecimal fallback) {
        BigDecimal value = money(preferred);
        return value.signum() > 0 ? value : money(fallback);
    }

    public static BigDecimal money(BigDecimal value) {
        return ExchangeSettlementAmounts.money(value);
    }
}
