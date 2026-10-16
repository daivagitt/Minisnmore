import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@tanstack/react-start", () => ({
  createServerFn: () => ({
    inputValidator(validator: (d: unknown) => any) {
      return {
        handler(fn: ({ data }: { data: any }) => Promise<any>) {
          return async ({ data }: { data: unknown }) => fn({ data: validator(data) });
        },
      };
    },
  }),
}));

const postMock = vi.hoisted(() => vi.fn());
const getMock = vi.hoisted(() => vi.fn());

vi.mock("@/lib/api", () => ({
  api: { post: postMock, get: getMock },
}));

import {
  calculateBogoDiscount,
  couponEligibilityHint,
  couponValidationData,
  formatCouponOffer,
  listActiveCoupons,
  resolveBogoQuantities,
  validateCoupon,
} from "./coupons.functions";

describe("coupon functions", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("normalizes and validates coupon codes", async () => {
    postMock.mockResolvedValueOnce({ ok: true, code: "WELCOME10", discount: 150 });

    await expect(validateCoupon({ data: { code: " WELCOME10 ", subtotal: 1500 } })).resolves.toEqual({
      ok: true,
      code: "WELCOME10",
      discount: 150,
    });
    expect(postMock).toHaveBeenCalledWith("/api/v1/coupons/validate", {
      code: "WELCOME10",
      subtotal: 1500,
    }, { auth: true });
  });

  it("rejects invalid coupon input", async () => {
    await expect(validateCoupon({ data: { code: "bad code!", subtotal: 100 } })).rejects.toThrow();
    await expect(validateCoupon({ data: { code: "SAVE", subtotal: -1 } })).rejects.toThrow();
    expect(postMock).not.toHaveBeenCalled();
  });

  it("accepts boundary coupon codes and subtotals", async () => {
    postMock.mockResolvedValueOnce({ ok: true, code: "EDGE", discount: 1 });
    await expect(validateCoupon({ data: { code: "A".repeat(64), subtotal: 0 } })).resolves.toEqual({
      ok: true,
      code: "EDGE",
      discount: 1,
    });

    postMock.mockResolvedValueOnce({ ok: true, code: "MAX", discount: 2 });
    await expect(validateCoupon({ data: { code: "MAX_TOTAL", subtotal: 10_000_000 } })).resolves.toEqual({
      ok: true,
      code: "MAX",
      discount: 2,
    });
  });

  it("handles inactive and flat coupons", async () => {
    postMock.mockResolvedValueOnce({ ok: false, error: "Invalid code" });
    await expect(validateCoupon({ data: { code: "OFF", subtotal: 500 } })).resolves.toEqual({
      ok: false,
      error: "Invalid code",
    });

    postMock.mockResolvedValueOnce({ ok: true, code: "SAVE", discount: 50 });
    await expect(validateCoupon({ data: { code: "SAVE", subtotal: 500 } })).resolves.toEqual({
      ok: true,
      code: "SAVE",
      discount: 50,
    });
  });

  it("lists active coupons", async () => {
    const coupons = [
      { code: "WELCOME10", discount_type: "percent", discount_value: 10, min_order: 999, max_discount: 500 },
    ];
    getMock.mockResolvedValueOnce({ coupons });

    await expect(listActiveCoupons()).resolves.toEqual({ coupons });
    expect(getMock).toHaveBeenCalledWith("/api/v1/coupons/active", { auth: true });
  });

  it("includes cart lines when validating a coupon", async () => {
    postMock.mockResolvedValueOnce({ ok: true, code: "BOGO1", discount: 400 });
    const payload = couponValidationData("BOGO1", 1400, [
      { qty: 1, unitPrice: 1000 },
      { qty: 1, unitPrice: 400 },
    ]);
    await expect(validateCoupon({ data: payload })).resolves.toEqual({
      ok: true,
      code: "BOGO1",
      discount: 400,
    });
    expect(postMock).toHaveBeenCalledWith(
      "/api/v1/coupons/validate",
      {
        code: "BOGO1",
        subtotal: 1400,
        items: [
          { qty: 1, price: 1000 },
          { qty: 1, price: 400 },
        ],
      },
      { auth: true },
    );
  });

  it("clamps BOGO quantities from active coupon payloads", () => {
    expect(resolveBogoQuantities({ bogo_buy_qty: 0, bogo_get_qty: 99 })).toEqual({
      buy: 1,
      get: 10,
      groupSize: 11,
    });
    expect(couponEligibilityHint(
      {
        code: "BOGO1",
        discount_type: "bogo",
        discount_value: 100,
        min_order: 0,
        max_discount: null,
        bogo_buy_qty: 0,
        bogo_get_qty: 99,
      },
      1000,
      10,
    )).toBe("Add 1 more item to unlock");
  });

  it("formats BOGO offers and eligibility", () => {
    const bogo = {
      code: "BOGO1",
      discount_type: "bogo" as const,
      discount_value: 100,
      min_order: 0,
      max_discount: null,
      bogo_buy_qty: 1,
      bogo_get_qty: 1,
    };
    expect(formatCouponOffer(bogo)).toBe("Buy 1 Get 1 Free");
    expect(couponEligibilityHint(bogo, 1000, 1)).toBe("Add 1 more item to unlock");
    expect(couponEligibilityHint(bogo, 1400, 2)).toBeNull();
    expect(
      formatCouponOffer({
        ...bogo,
        discount_value: 50,
        bogo_buy_qty: 2,
        bogo_get_qty: 1,
      }),
    ).toBe("Buy 2 Get 1 at 50% off");
  });

  it("calculates BOGO discount using greedy descending pairing with monotonicity", () => {
    // 4 items: [1200, 1000, 500, 400]
    // Pair 1: (1200, 1000) -> 1000 free
    // Pair 2: (500, 400) -> 400 free
    // Total discount: 1400
    const res = calculateBogoDiscount(1, 1, 100, null, 3100, [
      { qty: 1, unitPrice: 1200 },
      { qty: 1, unitPrice: 1000 },
      { qty: 1, unitPrice: 500 },
      { qty: 1, unitPrice: 400 },
    ]);
    expect(res.eligible).toBe(true);
    expect(res.discount).toBe(1400);
    expect(res.appliedGroups).toBe(2);
    expect(res.nextTierMissing).toBe(0);

    // Monotonicity: adding an inexpensive accessory (200) does not reduce the 1000 discount
    const withoutAcc = calculateBogoDiscount(1, 1, 100, null, 2200, [
      { qty: 1, unitPrice: 1200 },
      { qty: 1, unitPrice: 1000 },
    ]);
    expect(withoutAcc.discount).toBe(1000);

    const withAcc = calculateBogoDiscount(1, 1, 100, null, 2400, [
      { qty: 1, unitPrice: 1200 },
      { qty: 1, unitPrice: 1000 },
      { qty: 1, unitPrice: 200 },
    ]);
    expect(withAcc.discount).toBe(1000);
    expect(withAcc.appliedGroups).toBe(1);
    expect(withAcc.nextTierMissing).toBe(1);
  });

  it("calculates Buy 2 Get 1 with partial percent off and cap", () => {
    const res = calculateBogoDiscount(2, 1, 50, 300, 2700, [
      { qty: 1, unitPrice: 1000 },
      { qty: 1, unitPrice: 900 },
      { qty: 1, unitPrice: 800 },
    ]);
    // 50% of 800 = 400, but capped at maxDiscount 300
    expect(res.eligible).toBe(true);
    expect(res.discount).toBe(300);
    expect(res.nextTierMissing).toBe(0);
  });

  it("clamps negative maxDiscount to 0 without producing negative discount", () => {
    const res = calculateBogoDiscount(1, 1, 100, -100, 2000, [
      { qty: 1, unitPrice: 1000 },
      { qty: 1, unitPrice: 1000 },
    ]);
    expect(res.discount).toBe(0);
  });

  it("caps massive cart to MAX_TOTAL_UNITS safely", () => {
    const res = calculateBogoDiscount(1, 1, 100, null, 500000, [
      { qty: 500, unitPrice: 100 },
      { qty: 500, unitPrice: 100 },
      { qty: 500, unitPrice: 100 },
      { qty: 500, unitPrice: 100 },
    ]);
    expect(res.eligible).toBe(true);
    expect(res.appliedGroups).toBe(1000); // 2000 / 2 = 1000
  });

  it("handles BOGO validate response with nudge and bogo_details", async () => {
    postMock.mockResolvedValueOnce({
      ok: true,
      code: "BOGO1",
      discount: 500,
      nudge: "Add 1 more item to get 1 more free item!",
      bogo_details: "Buy 1 Get 1 Free applied (1 free item)",
    });

    const res = await validateCoupon({
      data: {
        code: "BOGO1",
        subtotal: 1500,
        items: [{ qty: 3, price: 500 }],
      },
    });

    expect(res).toEqual({
      ok: true,
      code: "BOGO1",
      discount: 500,
      nudge: "Add 1 more item to get 1 more free item!",
      bogo_details: "Buy 1 Get 1 Free applied (1 free item)",
    });
  });
});

describe("coupon estimates at cart and offer boundaries", () => {
  it("keeps SKU and category eligibility identifiers and omits invalid category IDs", () => {
    const payload = couponValidationData("SAVE", 300, [
      { qty: 1, unitPrice: 100, product: { id: "p-1", categoryId: "7" }, variantSku: "P-1-S" },
      { qty: 1, unitPrice: 100, productId: "p-2", categoryId: "not-a-number" },
      { qty: 1, unitPrice: 100, categoryId: "" },
    ]);
    expect(payload.items).toEqual([
      { qty: 1, price: 100, product_id: "p-1", sku: "P-1-S", category_id: 7 },
      { qty: 1, price: 100, product_id: "p-2" },
      { qty: 1, price: 100 },
    ]);
  });

  it("handles empty carts and defaults missing BOGO quantities without granting an offer", () => {
    expect(resolveBogoQuantities({})).toEqual({ buy: 1, get: 1, groupSize: 2 });
    expect(calculateBogoDiscount(undefined, null, null, null, null, [])).toEqual({ discount: 0, missingItems: 2, appliedGroups: 0, nextTierMissing: 2, eligible: false });
    // Cached legacy cart data can contain deleted lines or unpriced products.
    const legacy = JSON.parse('[null,{"qty":0,"unitPrice":100},{"qty":1,"unitPrice":null},{"qty":1,"unitPrice":-10},{"qty":1,"unitPrice":100}]');
    expect(calculateBogoDiscount(1, 1, null, null, null, legacy)).toMatchObject({ eligible: true, discount: 0, appliedGroups: 1 });
  });

  it("caps discount estimates at the subtotal and clamps excessive percentages", () => {
    const cart = [{ qty: 2, unitPrice: 100 }];
    expect(calculateBogoDiscount(0, 0, 150, Infinity, 50, cart).discount).toBe(50);
    expect(calculateBogoDiscount(1, 1, -20, null, 200, cart).discount).toBe(0);
  });

  it("formats capped, uncapped, and flat offers without requiring BOGO fields", () => {
    const base = { code: "SAVE", discount_type: "percent", discount_value: 10, min_order: 1000, max_discount: 200 };
    expect(formatCouponOffer(base)).toBe("10% off up to ₹200");
    expect(formatCouponOffer({ ...base, max_discount: null })).toBe("10% off");
    expect(formatCouponOffer({ ...base, discount_type: "flat", discount_value: 50 })).toBe("₹50 off");
    expect(couponEligibilityHint(base, 800, 1)).toBe("Add ₹200 more to unlock");
    expect(couponEligibilityHint({ ...base, discount_type: "bogo", bogo_buy_qty: 2 }, 1000, 0)).toBe("Add 3 more items to unlock");
    expect(couponEligibilityHint({ ...base, discount_type: "bogo", eligibility_scope: "same_sku" }, 1000, 1)).toBeNull();
    expect(couponEligibilityHint({ ...base, discount_type: "bogo", eligibility_scope: "" }, 1000, 2)).toBeNull();
  });
});

describe("BOGO resilience", () => {
  it("uses the minimum free quantity for a zero-valued legacy offer", () => {
    expect(resolveBogoQuantities({ bogo_buy_qty: 2, bogo_get_qty: 0 })).toEqual({ buy: 2, get: 1, groupSize: 3 });
  });

  it("limits a cart that crosses the unit cap partway through a line", () => {
    const cart = Array.from({ length: 5 }, () => ({ qty: 499, unitPrice: 100 }));
    expect(calculateBogoDiscount(1, 1, 100, null, 249500, cart)).toMatchObject({ discount: 100000, appliedGroups: 1000, eligible: true });
  });
});
