import { describe, expect, it } from "vitest";
import { normalizeWishlistIds, wishlistCountForProducts } from "./wishlist";

describe("wishlist helpers", () => {
  it("drops blank and duplicate ids", () => {
    expect(normalizeWishlistIds(["p1", "", "p1", "p2"])).toEqual(["p1", "p2"]);
  });

  it("counts only ids that still exist in the catalog", () => {
    expect(wishlistCountForProducts(["p1", "gone", ""], ["p1", "p2"])).toBe(1);
    expect(wishlistCountForProducts(["gone"], ["p1"])).toBe(0);
  });
});
