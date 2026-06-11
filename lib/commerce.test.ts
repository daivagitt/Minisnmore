import { describe, expect, it } from "vitest";
import { commerceFromRow, DEFAULT_COMMERCE } from "./commerce";

describe("commerceFromRow", () => {
  it("rejects out-of-range values and falls back to defaults", () => {
    const c = commerceFromRow({
      gst_rate: 150,
      shipping_standard_inr: 99_999,
      shipping_express_inr: 200,
      free_shipping_min_inr: 9_999_999,
    });
    expect(c.gstRate).toBe(DEFAULT_COMMERCE.gstRate);
    expect(c.shippingStandardInr).toBe(DEFAULT_COMMERCE.shippingStandardInr);
    expect(c.shippingExpressInr).toBe(200);
    expect(c.freeShippingMinInr).toBe(DEFAULT_COMMERCE.freeShippingMinInr);
  });
});
