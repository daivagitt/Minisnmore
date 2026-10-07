import { describe, expect, it } from "vitest";
import { exchangeToFulfillmentOrder } from "./exchange-fulfillment";
import type { AdminOrderReturnRow } from "./orders.functions";

function row(overrides: Partial<AdminOrderReturnRow> = {}): AdminOrderReturnRow {
  return {
    id: 9,
    return_number: "RET-9",
    status: "ready_to_ship",
    request_type: "exchange",
    reason: "Wrong size delivered to customer",
    requested_at: "2026-10-02T00:00:00Z",
    product_name: "Body Suit",
    replacement_size: "6-12 M",
    replacement_color: "Snail",
    sku: "MN-12",
    can_complete_package: true,
    can_book_outbound: true,
    packed_at: "2026-10-02T01:00:00Z",
    package_weight_grams: 500,
    package_length_cm: 12,
    package_breadth_cm: 10,
    package_height_cm: 8,
    outbound_awb: "OUT-1",
    outbound_label_url: "https://label/out",
    ...overrides,
  };
}

describe("exchangeToFulfillmentOrder", () => {
  it("maps the replacement onto the forward package/shipment dialog shape", () => {
    const mapped = exchangeToFulfillmentOrder(row());
    expect(mapped.order_number).toBe("RET-9");
    expect(mapped.payment_method).toBe("prepaid");
    expect(mapped.can_book_shipment).toBe(true);
    expect(mapped.can_book_expressbees_shipment).toBe(true);
    expect(mapped.can_complete_package).toBe(true);
    expect(mapped.tracking_number).toBe("OUT-1");
    expect(mapped.shipment_label_url).toBe("https://label/out");
    expect(mapped.order_items?.[0]).toMatchObject({
      product_name: "Body Suit",
      size: "6-12 M",
      color: "Snail",
      qty: 1,
    });
  });

  it("reflects server outbound eligibility flags", () => {
    const blocked = exchangeToFulfillmentOrder(
      row({ can_book_outbound: false, can_complete_package: false }),
    );
    expect(blocked.can_book_shipment).toBe(false);
    expect(blocked.can_book_expressbees_shipment).toBe(false);
    expect(blocked.can_complete_package).toBe(false);
  });
});
