import { describe, expect, it } from "vitest";
import {
  ageMonthsFromBirthDate,
  checkSizeFit,
  clampQuantity,
  measurementLimitsForProduct,
  pickLargerSize,
  recommendSizeByAgeMonths,
  recommendSizeByChest,
  recommendSizeByLength,
  resolveRecommendedSize,
  sizeGuideRowsForProduct,
  validateBirthDate,
  validateChestCm,
  validateLengthCm,
  validateQuantity,
  validateVirtualFitFile,
} from "./product-fit";

const vestInput = {
  sku: "FRONT-OPEN-VEST-SLEVELESS",
  sizes: ["0-3 M", "3-6 M", "6-12 M", "12-18 M", "18-24 M"],
};
const vestSizesShort = {
  sku: "FRONT-OPEN-VEST-SLEVELESS",
  sizes: ["0-3 M", "3-6 M", "6-12 M"],
};
const jablaInput = {
  sku: "BOOK-STYLE-TYING-JABLA",
  sizes: ["0-3M", "3-6M", "6-12M", "12-18M", "18-24M"],
};
const shortsInput = {
  sku: "BASIC-SHORTS",
  sizes: ["0-3M", "3-6M", "6-12M", "12-18M", "18-24M"],
};
const now = new Date("2026-05-29T12:00:00");

describe("product fit from MnM length/chest", () => {
  it("validates length and chest against product chart windows", () => {
    const limits = measurementLimitsForProduct(vestInput)!;
    expect(limits.hasLength).toBe(true);
    expect(limits.hasChest).toBe(true);
    expect(validateLengthCm("32", limits)).toEqual({ ok: true, value: 32 });
    expect(validateLengthCm("").ok).toBe(true);
    expect(validateLengthCm("10", limits).ok).toBe(false);
    expect(validateLengthCm("abc").ok).toBe(false);
    expect(validateChestCm("25", limits)).toEqual({ ok: true, value: 25 });
    expect(validateChestCm("")).toEqual({ ok: true, value: null });
    expect(validateChestCm("10", limits).ok).toBe(false);
  });

  it("builds MnM-4 jabla ranges ending at sheet points (25–27, 28–30…)", () => {
    // Total length sheet: 27, 30, 33, 36, 39
    const rows = sizeGuideRowsForProduct(jablaInput);
    expect(rows.map((r) => [r.size, r.length, r.chest])).toEqual([
      ["0-3M", "25–27 cm", "21–22 cm"],
      ["3-6M", "28–30 cm", "23–24 cm"],
      ["6-12M", "31–33 cm", "25–26 cm"],
      ["12-18M", "34–36 cm", "27–28 cm"],
      ["18-24M", "37–39 cm", "29–30 cm"],
    ]);
  });

  it("shows every chart size even when the product sells a subset", () => {
    const rows = sizeGuideRowsForProduct(vestSizesShort);
    expect(rows.map((r) => r.size)).toEqual(["0-3 M", "3-6 M", "6-12 M", "12-18M", "18-24M"]);
    expect(rows[3]?.length).toBe("33–34 cm");
    expect(rows[4]?.length).toBe("35–36 cm");
  });

  it("recommends only sizes the product sells", () => {
    expect(recommendSizeByLength(32, vestSizesShort)).toBe("6-12 M");
    expect(recommendSizeByLength(34, vestSizesShort)).toBeNull();
    expect(recommendSizeByLength(36, vestSizesShort)).toBeNull();
    expect(checkSizeFit({ lengthCm: "34", chestCm: "", ...vestSizesShort }).ok).toBe(false);
  });

  it("does not use missing sizes when ranging the last available size", () => {
    const twoSizes = {
      sku: "FRONT-OPEN-VEST-SLEVELESS",
      sizes: ["0-3 M", "6-12 M"],
    };
    const rows = sizeGuideRowsForProduct({ ...twoSizes, availableOnly: true });
    expect(rows.map((r) => r.size)).toEqual(["0-3 M", "6-12 M"]);
    // Sheet: 28 then 32 (3-6M=30 is not sold). Last size is 29–32, not 31–32 as if 3-6 existed.
    expect(rows.map((r) => [r.size, r.length, r.chest])).toEqual([
      ["0-3 M", "25–28 cm", "22–23 cm"],
      ["6-12 M", "29–32 cm", "24–25 cm"],
    ]);
    expect(recommendSizeByLength(29, twoSizes)).toBe("6-12 M");
    expect(recommendSizeByLength(31, twoSizes)).toBe("6-12 M");
  });

  it("uses an explicit 0-3M range from the size chart on the storefront", () => {
    const input = {
      sku: "UNKNOWN-SKU-NO-MAP",
      sizes: ["0-3M", "3-6M"],
      sizeChart: {
        code: "CUSTOM-RANGE",
        title: "Custom",
        tolerance: "±1 cm",
        sections: [
          {
            title: "Piece",
            bands: ["0-3M", "3-6M"],
            rows: [
              { label: "Chest width", values: { "0-3M": "20-22", "3-6M": 24 } },
              { label: "Body length", values: { "0-3M": "22-27", "3-6M": 30 } },
            ],
          },
        ],
      },
    };
    const rows = sizeGuideRowsForProduct(input);
    expect(rows.map((r) => [r.size, r.length, r.chest])).toEqual([
      ["0-3M", "22–27 cm", "20–22 cm"],
      ["3-6M", "28–30 cm", "23–24 cm"],
    ]);
    expect(recommendSizeByLength(22, input)).toBe("0-3M");
    expect(recommendSizeByLength(21, input)).toBeNull();
    expect(recommendSizeByChest(20, input)).toBe("0-3M");
    expect(checkSizeFit({ lengthCm: "22", chestCm: "", ...input })).toEqual({
      ok: true,
      size: "0-3M",
    });
  });

  it("shorts chart exposes length only", () => {
    const limits = measurementLimitsForProduct(shortsInput)!;
    expect(limits.hasLength).toBe(true);
    expect(limits.hasChest).toBe(false);
    const rows = sizeGuideRowsForProduct(shortsInput);
    expect(rows[0]?.lengthCm).toBe(17);
    expect(rows[0]?.chest).toBe("—");
  });

  it("uses product sizeChart for fit when provided", () => {
    const customChart = {
      code: "CUSTOM-FIT",
      title: "Custom",
      tolerance: "±1 cm",
      sections: [
        {
          title: "Piece",
          bands: ["0-3M", "3-6M"],
          rows: [
            { label: "Chest width", values: { "0-3M": 20, "3-6M": 22 } },
            { label: "Body length", values: { "0-3M": 25, "3-6M": 28 } },
          ],
        },
      ],
    };
    const input = {
      sku: "UNKNOWN-SKU-NO-MAP",
      sizes: ["0-3M", "3-6M"],
      sizeChart: customChart,
    };
    const limits = measurementLimitsForProduct(input)!;
    expect(limits.hasLength).toBe(true);
    expect(limits.hasChest).toBe(true);
    expect(recommendSizeByLength(25, input)).toBe("0-3M");
    expect(recommendSizeByChest(22, input)).toBe("3-6M");
    expect(
      checkSizeFit({ lengthCm: "25", chestCm: "", ...input }),
    ).toEqual({ ok: true, size: "0-3M" });
  });

  it("recommends MnM-4 length bands the way the sheet is read", () => {
    expect(recommendSizeByLength(25, jablaInput)).toBe("0-3M");
    expect(recommendSizeByLength(27, jablaInput)).toBe("0-3M");
    expect(recommendSizeByLength(28, jablaInput)).toBe("3-6M");
    expect(recommendSizeByLength(30, jablaInput)).toBe("3-6M");
    expect(recommendSizeByLength(31, jablaInput)).toBe("6-12M");
    expect(recommendSizeByLength(39, jablaInput)).toBe("18-24M");
    expect(recommendSizeByLength(24, jablaInput)).toBeNull();
    expect(recommendSizeByLength(40, jablaInput)).toBeNull();
  });

  it("recommends vest length/chest with sheet-upper-bound ranges", () => {
    // Body length: 28, 30, 32, 34, 36 → 26–28, 29–30, 31–32…
    expect(recommendSizeByLength(28, vestInput)).toBe("0-3 M");
    expect(recommendSizeByLength(29, vestInput)).toBe("3-6 M");
    expect(recommendSizeByLength(30, vestInput)).toBe("3-6 M");
    expect(recommendSizeByLength(32, vestInput)).toBe("6-12 M");
    // Chest width: 23, 24, 25… → 23, 24, 25…
    expect(recommendSizeByChest(23, vestInput)).toBe("0-3 M");
    expect(recommendSizeByChest(24, vestInput)).toBe("3-6 M");
    expect(recommendSizeByChest(25, vestInput)).toBe("6-12 M");
    expect(recommendSizeByLength(40, vestSizesShort)).toBeNull();
  });

  it("auto-picks from length or chest and prefers roomier when both differ", () => {
    expect(pickLargerSize("3-6 M", "6-12 M", vestInput.sizes)).toBe("6-12 M");
    expect(checkSizeFit({ lengthCm: "32", chestCm: "", ...vestInput })).toEqual({
      ok: true,
      size: "6-12 M",
    });
    expect(checkSizeFit({ lengthCm: "", chestCm: "25", ...vestInput })).toEqual({
      ok: true,
      size: "6-12 M",
    });
    // length → 3-6M (30), chest → 12-18M (26) → roomier 12-18M
    expect(checkSizeFit({ lengthCm: "30", chestCm: "26", ...vestInput })).toEqual({
      ok: true,
      size: "12-18 M",
    });
    expect(checkSizeFit({ lengthCm: "27", chestCm: "", ...jablaInput })).toEqual({
      ok: true,
      size: "0-3M",
    });
    expect(checkSizeFit({ lengthCm: "28", chestCm: "", ...jablaInput })).toEqual({
      ok: true,
      size: "3-6M",
    });
    expect(checkSizeFit({ lengthCm: "", chestCm: "", ...vestInput }).ok).toBe(false);
    expect(checkSizeFit({ lengthCm: "100", chestCm: "", ...vestInput }).ok).toBe(false);
    expect(checkSizeFit({ lengthCm: "30", chestCm: "bad", ...vestInput }).ok).toBe(false);
  });

  it("validates birth dates and age recommendation", () => {
    expect(validateBirthDate("2025-11-29", now)).toEqual({ ok: true, value: "2025-11-29" });
    expect(ageMonthsFromBirthDate("2025-11-29", now)).toBe(6);
    expect(recommendSizeByAgeMonths(8, vestInput.sizes)).toBe("6-12 M");
    expect(resolveRecommendedSize({ lengthCm: "32", ...vestInput })).toBe("6-12 M");
    expect(resolveRecommendedSize({ birthDate: "2025-11-29", ...vestInput, now })).toBe("6-12 M");
  });

  it("validates quantity and virtual fit uploads", () => {
    expect(validateQuantity(1)).toEqual({ ok: true, value: 1 });
    expect(clampQuantity(99)).toBe(10);
    expect(validateVirtualFitFile(new File(["x"], "pic.jpg", { type: "image/jpeg" }))).toEqual({
      ok: true,
    });
  });
});
