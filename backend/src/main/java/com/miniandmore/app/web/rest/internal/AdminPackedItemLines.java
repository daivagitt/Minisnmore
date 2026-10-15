package com.miniandmore.app.web.rest.internal;

import com.miniandmore.app.service.PackageShortShipService;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public final class AdminPackedItemLines {

    private AdminPackedItemLines() {}

    public record PackedItemRequest(Long order_item_id, @NotNull @Min(0) Integer packed_qty) {}

    public static List<PackageShortShipService.PackedLine> toPackedLines(List<PackedItemRequest> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        return items
            .stream()
            .map(item -> new PackageShortShipService.PackedLine(item.order_item_id(), item.packed_qty()))
            .toList();
    }
}
