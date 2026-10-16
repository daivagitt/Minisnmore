import { describe, expect, it } from "vitest";
import { isBestsellerFromBadge, isNewFromBadge, isTrendingFromBadge } from "./product-flags";

describe("product-flags", () => {
  it("detects bestseller badges without matching unrelated words", () => {
    expect(isBestsellerFromBadge("Bestseller")).toBe(true);
    expect(isBestsellerFromBadge("best seller")).toBe(true);
    expect(isBestsellerFromBadge("Comfortable")).toBe(false);
  });

  it("detects new badges but not newborn", () => {
    expect(isNewFromBadge("New arrival")).toBe(true);
    expect(isNewFromBadge("new")).toBe(true);
    expect(isNewFromBadge("newborn essentials")).toBe(false);
  });

  it("detects trending badges", () => {
    expect(isTrendingFromBadge("Trending")).toBe(true);
    expect(isTrendingFromBadge("trend")).toBe(true);
    expect(isTrendingFromBadge("Contemporary")).toBe(false);
  });
});
