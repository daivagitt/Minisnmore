import { describe, expect, it } from "vitest";
import {
  commerceFromRow,
  DEFAULT_COMMERCE,
  DEFAULT_DELIVERY_ETA_BUFFER_DAYS,
  parseDeliveryEtaBufferDays,
} from "./commerce";

describe("commerceFromRow", () => {
  it("rejects out-of-range values and falls back to defaults", () => {
    const c = commerceFromRow({
      gst_rate: 150,
      shipping_standard_inr: 99_999,
      shipping_express_inr: 200,
      free_shipping_min_inr: 9_999_999,
      cod_fee_inr: 149,
    });
    expect(c.gstRate).toBe(DEFAULT_COMMERCE.gstRate);
    expect(c.shippingStandardInr).toBe(DEFAULT_COMMERCE.shippingStandardInr);
    expect(c.shippingExpressInr).toBe(200);
    expect(c.freeShippingMinInr).toBe(DEFAULT_COMMERCE.freeShippingMinInr);
    expect(c.codFeeInr).toBe(149);
  });

  it("uses the default COD fee for missing or invalid settings", () => {
    expect(commerceFromRow({}).codFeeInr).toBe(99);
    expect(commerceFromRow({ cod_fee_inr: -1 }).codFeeInr).toBe(99);
    expect(commerceFromRow({ cod_fee_inr: 0 }).codFeeInr).toBe(0);
  });
});

describe("parseDeliveryEtaBufferDays", () => {
  it("parses numbers and stringy numbers within range", () => {
    expect(parseDeliveryEtaBufferDays(2)).toBe(2);
    expect(parseDeliveryEtaBufferDays("2")).toBe(2);
    expect(parseDeliveryEtaBufferDays(0)).toBe(0);
  });

  it("falls back for missing, negative, or out-of-range values", () => {
    expect(parseDeliveryEtaBufferDays(undefined)).toBe(DEFAULT_DELIVERY_ETA_BUFFER_DAYS);
    expect(parseDeliveryEtaBufferDays(-1)).toBe(DEFAULT_DELIVERY_ETA_BUFFER_DAYS);
    expect(parseDeliveryEtaBufferDays(999)).toBe(DEFAULT_DELIVERY_ETA_BUFFER_DAYS);
    expect(parseDeliveryEtaBufferDays("nope")).toBe(DEFAULT_DELIVERY_ETA_BUFFER_DAYS);
  });
});
