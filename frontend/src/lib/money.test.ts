import { describe, expect, it } from "vitest";
import { DEFAULT_COMMERCE } from "./commerce";
import { formatInr, gstAmount, gstIncludedInPrice, orderTotals, roundInr, shippingForOrder } from "./money";

describe("money", () => {
  it("rounds INR to paise", () => {
    expect(roundInr(1499.999)).toBe(1500);
    expect(roundInr(999.404)).toBe(999.4);
  });

  it("formats paise when present", () => {
    expect(formatInr(1499.99)).toBe("1,499.99");
  });

  it("extracts included GST at a configurable rate", () => {
    expect(gstIncludedInPrice(1050, 5)).toBe(50);
    expect(gstIncludedInPrice(1120, 12)).toBe(120);
  });

  it("extracts GST after discount and excludes shipping", () => {
    expect(orderTotals({ subtotal: 1000, discount: 100, shipMode: "std", commerce: DEFAULT_COMMERCE }).gst).toBe(42.86);
    expect(orderTotals({ subtotal: 1000, discount: 100, shipMode: "std", commerce: DEFAULT_COMMERCE }).shippingGst).toBe(0);
  });

  it("computes GST included in price, not added on top", () => {
    expect(gstAmount(1050, DEFAULT_COMMERCE)).toBe(50);
    expect(orderTotals({ subtotal: 200, discount: 20, donation: 5 }).grand).toBe(284);
    expect(orderTotals({ subtotal: 200, discount: 20, donation: 5 }).gstIncluded).toBe(8.57);
  });

  it("adds GST on top when prices are exclusive", () => {
    const commerce = { ...DEFAULT_COMMERCE, pricesIncludeGst: false };
    expect(orderTotals({ subtotal: 200, discount: 20, donation: 0, commerce }).grand).toBe(288); // 180 + 99 + 9 GST
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

  it("matches all five workbook scenarios with one aggregate GST amount", () => {
    const freeAbove1000 = { ...DEFAULT_COMMERCE, freeShippingMinInr: 1000 };
    const scenarios = [
      {
        name: "COD and paid shipping",
        input: { subtotal: 279.2, discount: 27.92, codFee: 99 },
        expected: { grand: 449.28, gst: 16.68, taxableTotal: 432.6 },
      },
      {
        name: "prepaid and paid shipping",
        input: { subtotal: 279.2, discount: 27.92 },
        expected: { grand: 350.28, gst: 11.97, taxableTotal: 338.31 },
      },
      {
        name: "prepaid with free shipping",
        input: { subtotal: 1685.6, discount: 168.56, commerce: freeAbove1000 },
        expected: { grand: 1517.04, gst: 72.24, taxableTotal: 1444.8 },
      },
      {
        name: "COD with free shipping",
        input: { subtotal: 1685.6, discount: 168.56, codFee: 99, commerce: freeAbove1000 },
        expected: { grand: 1616.04, gst: 76.95, taxableTotal: 1539.09 },
      },
      {
        name: "out-of-state COD and paid shipping",
        input: { subtotal: 279.2, discount: 27.92, codFee: 99 },
        expected: { grand: 449.28, gst: 16.68, taxableTotal: 432.6 },
      },
    ];

    for (const scenario of scenarios) {
      const totals = orderTotals(scenario.input);
      expect(totals, scenario.name).toMatchObject({ ...scenario.expected, roundOff: 0 });
    }
  });
});
