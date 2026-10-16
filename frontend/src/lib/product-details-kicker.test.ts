import { describe, expect, it } from "vitest";
import {
  DETAILS_KICKER_MAX_LEN,
  detailsKickerValidationErrors,
  normalizeDetailsKicker,
  productDetailsKicker,
} from "./product-details-kicker";

describe("normalizeDetailsKicker", () => {
  it("trims and collapses whitespace", () => {
    expect(normalizeDetailsKicker(null)).toBeNull();
    expect(normalizeDetailsKicker("  ")).toBeNull();
    expect(normalizeDetailsKicker(" Organic  Cotton · Newborn ")).toBe("Organic Cotton · Newborn");
  });
});

describe("detailsKickerValidationErrors", () => {
  it("rejects overlong labels", () => {
    expect(detailsKickerValidationErrors("Organic Cotton · Newborn")).toEqual([]);
    expect(detailsKickerValidationErrors("x".repeat(DETAILS_KICKER_MAX_LEN + 1))).toEqual([
      `Details card label must be ${DETAILS_KICKER_MAX_LEN} characters or fewer.`,
    ]);
  });
});

describe("productDetailsKicker", () => {
  it("prefers the admin label and falls back to fabric · category", () => {
    expect(
      productDetailsKicker({
        detailsKicker: "Muslin · Infant",
        fabric: "Organic Cotton",
        category: "Newborn",
      }),
    ).toBe("Muslin · Infant");
    expect(productDetailsKicker({ fabric: "Organic Cotton", category: "Newborn" })).toBe(
      "Organic Cotton · Newborn",
    );
  });
});
