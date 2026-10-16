import { describe, expect, it } from "vitest";
import { formatReturnProductSubtitle, formatReturnVariant } from "@/lib/return-variant";

describe("return-variant helpers", () => {
  it("prefers variant_sku when sku is empty", () => {
    expect(formatReturnVariant({ original_size: "M", variant_sku: "SKU-1" })).toBe("M · SKU-1");
  });

  it("builds product subtitles for exchanges", () => {
    expect(
      formatReturnProductSubtitle({
        product_name: "Dress",
        original_size: "2-3 yrs",
        original_color: "Pink",
        variant_sku: "DRS-PINK",
        replacement_size: "3-4 yrs",
        replacement_color: "Blue",
      }),
    ).toBe("Dress (2-3 yrs · Pink · DRS-PINK) → 3-4 yrs · Blue");
  });
});
