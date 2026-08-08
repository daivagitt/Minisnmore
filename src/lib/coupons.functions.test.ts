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

import { listActiveCoupons, validateCoupon } from "./coupons.functions";

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
    });
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
    expect(getMock).toHaveBeenCalledWith("/api/v1/coupons/active");
  });
});
