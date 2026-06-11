import { describe, expect, it } from "vitest";
import { DEFAULT_COMMERCE } from "./commerce";
import { formatInr, gstAmount, gstIncludedInPrice, orderTotals, roundInr, shippingForOrder } from "./money";

describe("money", () => {
  it("rounds to whole rupees", () => {
    expect(roundInr(1499.99)).toBe(1500);
    expect(roundInr(999.4)).toBe(999);
  });

  it("formats without decimal paise", () => {
    expect(formatInr(1499.99)).toBe("1,500");
  });

  it("extracts included GST at a configurable rate", () => {
    expect(gstIncludedInPrice(1050, 5)).toBe(50);
    expect(gstIncludedInPrice(1120, 12)).toBe(120);
  });

  it("computes GST included in price, not added on top", () => {
    expect(gstAmount(1050, DEFAULT_COMMERCE)).toBe(50);
    expect(orderTotals({ subtotal: 200, discount: 20, donation: 5 }).grand).toBe(284);
    expect(orderTotals({ subtotal: 200, discount: 20, donation: 5 }).gstIncluded).toBe(14); // 9 on ₹180 + 5 on ₹99 shipping
  });

  it("adds GST on top when prices are exclusive", () => {
    const commerce = { ...DEFAULT_COMMERCE, pricesIncludeGst: false };
    expect(orderTotals({ subtotal: 200, discount: 20, donation: 0, commerce }).grand).toBe(293); // 180 + 99 + 14 GST
  });

  it("applies standard shipping below free-shipping threshold", () => {
    expect(shippingForOrder(999, "std", DEFAULT_COMMERCE)).toBe(99);
    expect(shippingForOrder(2500, "std", DEFAULT_COMMERCE)).toBe(0);
  });

  it("applies express shipping regardless of threshold", () => {
    expect(shippingForOrder(999, "exp", DEFAULT_COMMERCE)).toBe(150);
    expect(shippingForOrder(3000, "exp", DEFAULT_COMMERCE)).toBe(150);
  });

  it("includes express shipping in order grand total", () => {
    expect(
      orderTotals({ subtotal: 200, discount: 0, donation: 0, shipMode: "exp", commerce: DEFAULT_COMMERCE }).grand,
    ).toBe(350);
  });
});
