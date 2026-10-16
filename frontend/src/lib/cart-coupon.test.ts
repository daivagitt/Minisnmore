import { beforeEach, describe, expect, it } from "vitest";
import { readStoredCoupon, storeCoupon } from "./cart-coupon";

describe("cart-coupon session storage", () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  it("round-trips applied coupon", () => {
    storeCoupon({ code: "WELCOME10", off: 150 });
    expect(readStoredCoupon()).toEqual({ code: "WELCOME10", off: 150 });
  });

  it("clears stored coupon", () => {
    storeCoupon({ code: "SAVE", off: 50 });
    storeCoupon(null);
    expect(readStoredCoupon()).toBeNull();
  });
});
