import { describe, expect, it } from "vitest";
import { mrpFromSelling, sellingFromMrp, sellingMultiplier } from "@/lib/pricing";

describe("pricing", () => {
  it("sellingMultiplier handles 0%, 20%, and 100%", () => {
    expect(sellingMultiplier(0)).toBe(1);
    expect(sellingMultiplier(20)).toBe(0.8);
    expect(sellingMultiplier(100)).toBe(0);
  });

  it("sellingFromMrp applies discount and rounds to 2dp", () => {
    expect(sellingFromMrp(1000, 20)).toBe(800);
    expect(sellingFromMrp(999, 20)).toBe(799.2);
    expect(sellingFromMrp(1000, 0)).toBe(1000);
    expect(sellingFromMrp(1000, 100)).toBe(0);
    expect(sellingFromMrp(0, 20)).toBe(0);
  });

  it("mrpFromSelling inverts sellingFromMrp", () => {
    expect(mrpFromSelling(800, 20)).toBe(1000);
    expect(mrpFromSelling(0, 20)).toBe(0);
    expect(mrpFromSelling(500, 100)).toBe(500);
  });
});
