import { describe, expect, it } from "vitest";
import {
  FIT_LIMITS,
  ageMonthsFromBirthDate,
  checkSizeFit,
  clampQuantity,
  measurementLimitsForProduct,
  measurementLimitsForSizes,
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
import { catalogSizeChartTemplate } from "./product-size-charts";

const vestChart = catalogSizeChartTemplate({ sku: "FRONT-OPEN-VEST-SLEVELESS" });
const jablaChart = catalogSizeChartTemplate({ sku: "BOOK-STYLE-TYING-JABLA" });
const shortsChart = catalogSizeChartTemplate({ sku: "BASIC-SHORTS" });
const vestInput = {
  sku: "FRONT-OPEN-VEST-SLEVELESS",
  sizes: ["0-3 M", "3-6 M", "6-12 M", "12-18 M", "18-24 M"],
  sizeChart: vestChart,
};
const vestSizesShort = {
  sku: "FRONT-OPEN-VEST-SLEVELESS",
  sizes: ["0-3 M", "3-6 M", "6-12 M"],
  sizeChart: vestChart,
};
const jablaInput = {
  sku: "BOOK-STYLE-TYING-JABLA",
  sizes: ["0-3M", "3-6M", "6-12M", "12-18M", "18-24M"],
  sizeChart: jablaChart,
};
const shortsInput = {
  sku: "BASIC-SHORTS",
  sizes: ["0-3M", "3-6M", "6-12M", "12-18M", "18-24M"],
  sizeChart: shortsChart,
};
const now = new Date("2026-05-29T12:00:00");

const noChartInput = {
  sku: "UNKNOWN-SKU-NO-MAP",
  sizes: ["0-3M", "3-6M"],
};

const chestOnlyInput = {
  sku: "UNKNOWN-SKU-NO-MAP",
  sizes: ["0-3M", "3-6M"],
  sizeChart: {
    code: "CHEST-ONLY",
    title: "Bib",
    tolerance: "±1 cm",
    sections: [
      {
        title: "Piece",
        bands: ["0-3M", "3-6M"],
        rows: [{ label: "Chest width", values: { "0-3M": 20, "3-6M": 22 } }],
      },
    ],
  },
};

const shoulderOnlyInput = {
  sku: "UNKNOWN-SKU-NO-MAP",
  sizes: ["0-3M", "3-6M"],
  sizeChart: {
    code: "SHOULDER-ONLY",
    title: "Accessory",
    tolerance: "±1 cm",
    sections: [
      {
        title: "Piece",
        bands: ["0-3M", "3-6M"],
        rows: [{ label: "Shoulder width", values: { "0-3M": 18, "3-6M": 19 } }],
      },
    ],
  },
};

describe("product fit from MnM length/chest", () => {
  it("uses the SKU catalog for fit limits when no product size chart is saved", () => {
    expect(measurementLimitsForProduct({ sku: "FRONT-OPEN-VEST-SLEVELESS", sizes: vestInput.sizes })).not.toBeNull();
    expect(measurementLimitsForProduct({ sku: "BASIC-SHORTS", sizes: shortsInput.sizes })?.hasLength).toBe(true);
  });

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

  it("keeps full-chart boundaries when only some sizes are available", () => {
    const twoSizes = {
      sku: "FRONT-OPEN-VEST-SLEVELESS",
      sizes: ["0-3 M", "6-12 M"],
      sizeChart: vestChart,
    };
    const rows = sizeGuideRowsForProduct({ ...twoSizes, availableOnly: true });
    expect(rows.map((r) => r.size)).toEqual(["0-3 M", "6-12 M"]);
    // The hidden 3-6M chart column still defines the boundary of each visible size.
    expect(rows.map((r) => [r.size, r.length, r.chest])).toEqual([
      ["0-3 M", "27–28 cm", "23 cm"],
      ["6-12 M", "31–32 cm", "25 cm"],
    ]);
    expect(recommendSizeByLength(29, twoSizes)).toBeNull();
    expect(recommendSizeByLength(31, twoSizes)).toBe("6-12 M");
  });

  it("uses the preceding chart band for a non-adjacent available size", () => {
    const input = {
      sku: "UNKNOWN-SKU-NO-MAP",
      sizes: ["0-3M", "12-18M"],
      availableOnly: true,
      sizeChart: {
        code: "CUSTOM-GAPPED-SIZES",
        title: "Side button T-shirt",
        tolerance: "±1 cm",
        sections: [
          {
            title: "T-shirt",
            bands: ["0-3M", "3-6M", "6-12M", "12-18M", "18-24M"],
            rows: [
              {
                label: "Chest width",
                values: { "0-3M": "17-22", "3-6M": 24, "6-12M": 26, "12-18M": 28, "18-24M": 30 },
              },
              {
                label: "Body length",
                values: { "0-3M": "25-28", "3-6M": 31, "6-12M": 34, "12-18M": 37, "18-24M": 40 },
              },
            ],
          },
        ],
      },
    };

    expect(sizeGuideRowsForProduct(input).map((r) => [r.size, r.length, r.chest])).toEqual([
      ["0-3M", "25–28 cm", "17–22 cm"],
      ["12-18M", "35–37 cm", "27–28 cm"],
    ]);
    expect(recommendSizeByLength(29, input)).toBeNull();
    expect(recommendSizeByLength(35, input)).toBe("12-18M");
    expect(recommendSizeByChest(23, input)).toBeNull();
    expect(recommendSizeByChest(27, input)).toBe("12-18M");
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

  it("shorts chart exposes length and waistband width", () => {
    const limits = measurementLimitsForProduct(shortsInput)!;
    expect(limits.hasLength).toBe(true);
    expect(limits.hasChest).toBe(true);
    expect(limits.lengthLabel).toBe("Total length");
    expect(limits.chestLabel).toBe("Waistband width");
    const rows = sizeGuideRowsForProduct(shortsInput);
    expect(rows[0]?.lengthCm).toBe(17);
    expect(rows[0]?.chestCm).toBe(16);
    expect(rows[0]?.chestLabel).toBe("Waistband width");
  });

  it("recommends a size from custom waistband measurements", () => {
    const input = {
      sku: "UNKNOWN-SKU-NO-MAP",
      sizes: ["0-3M", "3-6M", "6-12M", "12-18M", "18-24M"],
      sizeChart: {
        code: "MnM-4",
        title: "Shorts Sets",
        tolerance: "±1 cm",
        sections: [
          {
            title: "Measurements",
            bands: ["0-3M", "3-6M", "6-12M", "12-18M", "18-24M"],
            rows: [
              {
                label: "Waistband width",
                values: { "0-3M": "12-16", "3-6M": 17, "6-12M": 18, "12-18M": 19, "18-24M": 20 },
              },
              {
                label: "Waistband Height",
                values: { "0-3M": "1.1-1.5", "3-6M": 1.5, "6-12M": 1.5, "12-18M": 1.5, "18-24M": 1.5 },
              },
            ],
          },
        ],
      },
    };
    const limits = measurementLimitsForProduct(input)!;
    expect(limits.hasLength).toBe(false);
    expect(limits.hasChest).toBe(true);
    expect(limits.chestLabel).toBe("Waistband width");
    expect(recommendSizeByChest(12, input)).toBe("0-3M");
    expect(recommendSizeByChest(16, input)).toBe("0-3M");
    expect(recommendSizeByChest(17, input)).toBe("3-6M");
    expect(checkSizeFit({ lengthCm: "", chestCm: "18", ...input })).toEqual({
      ok: true,
      size: "6-12M",
    });
    const rows = sizeGuideRowsForProduct(input);
    expect(rows.map((r) => [r.size, r.length, r.chest])).toEqual([
      ["0-3M", "—", "12–16 cm"],
      ["3-6M", "—", "17 cm"],
      ["6-12M", "—", "18 cm"],
      ["12-18M", "—", "19 cm"],
      ["18-24M", "—", "20 cm"],
    ]);
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
    expect(checkSizeFit({ lengthCm: "25", chestCm: "", ...input })).toEqual({
      ok: true,
      size: "0-3M",
    });
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
    expect(clampQuantity(5, 2)).toBe(2);
    expect(clampQuantity(1, 2)).toBe(1);
    expect(clampQuantity(3, 0)).toBe(0);
    expect(validateVirtualFitFile(null).ok).toBe(false);
    expect(validateVirtualFitFile(new File(["x"], "pic.txt", { type: "text/plain" })).ok).toBe(false);
    expect(validateVirtualFitFile(new File(["x"], "pic.jpg", { type: "image/jpeg" }))).toEqual({
      ok: true,
    });
    expect(
      validateVirtualFitFile(new File([new Uint8Array(6 * 1024 * 1024)], "big.jpg", { type: "image/jpeg" })).ok,
    ).toBe(false);
  });

  it("rejects invalid birth dates and counts months before the birthday", () => {
    expect(validateBirthDate("", now)).toEqual({ ok: false, error: "Enter baby's birth date" });
    expect(validateBirthDate("   ", now)).toEqual({ ok: false, error: "Enter baby's birth date" });
    expect(validateBirthDate("29-11-2025", now)).toEqual({ ok: false, error: "Use a valid date" });
    expect(validateBirthDate("2026-13-01", now)).toEqual({ ok: false, error: "Use a valid date" });
    expect(validateBirthDate("2026-12-01", now)).toEqual({
      ok: false,
      error: "Birth date cannot be in the future",
    });
    expect(validateBirthDate("2022-05-29", now)).toEqual({
      ok: false,
      error: "Age calculator supports babies up to 36 months",
    });
    expect(ageMonthsFromBirthDate("not-a-date", now)).toBeNull();
    // 30 Nov 2025 → 29 May 2026 is 5 months because the 30th has not occurred yet.
    expect(ageMonthsFromBirthDate("2025-11-30", now)).toBe(5);
  });

  it("rejects quantity and virtual-fit inputs outside the allowed rules", () => {
    expect(validateQuantity(1.5)).toEqual({ ok: false, error: "Quantity must be a whole number" });
    expect(validateQuantity(0).ok).toBe(false);
    expect(validateQuantity(11).ok).toBe(false);
    expect(clampQuantity(0)).toBe(FIT_LIMITS.qtyMin);
    expect(validateVirtualFitFile(null)).toEqual({ ok: false, error: "Choose a photo to upload" });
    expect(validateVirtualFitFile(undefined)).toEqual({
      ok: false,
      error: "Choose a photo to upload",
    });
    expect(validateVirtualFitFile(new File(["x"], "notes.txt", { type: "text/plain" }))).toEqual({
      ok: false,
      error: "Upload a JPG or PNG photo",
    });
    expect(
      validateVirtualFitFile(
        new File([new Uint8Array(FIT_LIMITS.virtualFitMaxBytes + 1)], "pic.jpg", {
          type: "image/jpeg",
        }),
      ),
    ).toEqual({ ok: false, error: "Photo must be 5 MB or smaller" });
  });

  it("uses default length and chest windows when no product limits are given", () => {
    expect(validateLengthCm("20")).toEqual({ ok: true, value: 20 });
    expect(validateLengthCm("10").ok).toBe(false);
    expect(validateLengthCm("60").ok).toBe(false);
    expect(validateChestCm("20")).toEqual({ ok: true, value: 20 });
    expect(validateChestCm("10").ok).toBe(false);
    expect(validateChestCm("40").ok).toBe(false);
  });

  it("treats a missing or non-measurement chart as unavailable for fit", () => {
    expect(measurementLimitsForProduct(noChartInput)).toBeNull();
    expect(measurementLimitsForSizes(["0-3M", "3-6M"])).toBeNull();
    expect(sizeGuideRowsForProduct(noChartInput)).toEqual([
      { size: "0-3M", length: "—", chest: "—", lengthCm: null, chestCm: null, lengthLabel: null, chestLabel: null },
      { size: "3-6M", length: "—", chest: "—", lengthCm: null, chestCm: null, lengthLabel: null, chestLabel: null },
    ]);
    expect(recommendSizeByLength(28, noChartInput)).toBeNull();
    expect(recommendSizeByChest(22, noChartInput)).toBeNull();
    expect(checkSizeFit({ lengthCm: "28", ...noChartInput })).toEqual({
      ok: false,
      error: "Size calculator is not available for this item",
    });
    expect(measurementLimitsForProduct(shoulderOnlyInput)).toBeNull();
    expect(sizeGuideRowsForProduct(shoulderOnlyInput).map((r) => [r.size, r.length, r.chest])).toEqual(
      [
        ["0-3M", "—", "—"],
        ["3-6M", "—", "—"],
      ],
    );
  });

  it("uses a ±1 cm window around a single sheet point", () => {
    const input = {
      sku: "UNKNOWN-SKU-NO-MAP",
      sizes: ["0-3M"],
      sizeChart: {
        code: "ONE-SIZE",
        title: "One size",
        tolerance: "±1 cm",
        sections: [
          {
            title: "Piece",
            bands: ["0-3M"],
            rows: [
              { label: "Chest width", values: { "0-3M": 22 } },
              { label: "Body length", values: { "0-3M": 30 } },
            ],
          },
        ],
      },
    };
    expect(sizeGuideRowsForProduct(input).map((r) => [r.size, r.length, r.chest])).toEqual([
      ["0-3M", "29–30 cm", "21–22 cm"],
    ]);
    expect(recommendSizeByLength(29, input)).toBe("0-3M");
    expect(recommendSizeByLength(28, input)).toBeNull();
  });

  it("clamps inverted sheet points so a band min is never above its max", () => {
    const input = {
      sku: "UNKNOWN-SKU-NO-MAP",
      sizes: ["0-3M", "3-6M"],
      sizeChart: {
        code: "INVERTED",
        title: "Inverted",
        tolerance: "±1 cm",
        sections: [
          {
            title: "Piece",
            bands: ["0-3M", "3-6M"],
            rows: [
              { label: "Chest width", values: { "0-3M": 30, "3-6M": 20 } },
              { label: "Body length", values: { "0-3M": 40, "3-6M": 25 } },
            ],
          },
        ],
      },
    };
    expect(sizeGuideRowsForProduct(input).map((r) => [r.size, r.length, r.chest])).toEqual([
      ["0-3M", "40 cm", "30 cm"],
      ["3-6M", "25 cm", "20 cm"],
    ]);
    expect(recommendSizeByLength(40, input)).toBe("0-3M");
    expect(recommendSizeByLength(25, input)).toBe("3-6M");
  });

  it("rejects the missing metric and unsold in-window values in checkSizeFit", () => {
    expect(checkSizeFit({ ...vestInput })).toEqual({
      ok: false,
      error: "Enter body length or chest width measurement",
    });
    expect(checkSizeFit({ lengthCm: "28", chestCm: "", ...chestOnlyInput })).toEqual({
      ok: false,
      error: "This item has no length guide on the measurement sheet",
    });
    expect(checkSizeFit({ lengthCm: "", chestCm: "20", ...shortsInput })).toEqual({
      ok: true,
      size: "18-24M",
    });
    expect(recommendSizeByLength(28, chestOnlyInput)).toBeNull();
    expect(checkSizeFit({ lengthCm: "", chestCm: "22", ...chestOnlyInput })).toEqual({
      ok: true,
      size: "3-6M",
    });
    const limits = measurementLimitsForProduct(chestOnlyInput)!;
    expect(limits.hasLength).toBe(false);
    expect(limits.hasChest).toBe(true);
    expect(limits.lengthMin).toBe(FIT_LIMITS.lengthMin);
    expect(limits.lengthMax).toBe(FIT_LIMITS.lengthMax);

    const twoSizes = {
      sku: "FRONT-OPEN-VEST-SLEVELESS",
      sizes: ["0-3 M", "6-12 M"],
    };
    expect(checkSizeFit({ lengthCm: "29", chestCm: "", ...twoSizes }).ok).toBe(false);
    expect(checkSizeFit({ lengthCm: "", chestCm: "24", ...twoSizes }).ok).toBe(false);
  });

  it("picks the roomier band when length ranges overlap", () => {
    const input = {
      sku: "UNKNOWN-SKU-NO-MAP",
      sizes: ["0-3M", "3-6M"],
      sizeChart: {
        code: "OVERLAP",
        title: "Overlap",
        tolerance: "±1 cm",
        sections: [
          {
            title: "Piece",
            bands: ["0-3M", "3-6M"],
            rows: [
              { label: "Chest width", values: { "0-3M": "20-22", "3-6M": "21-24" } },
              { label: "Body length", values: { "0-3M": "20-30", "3-6M": "25-35" } },
            ],
          },
        ],
      },
    };
    expect(recommendSizeByLength(27, input)).toBe("3-6M");
    expect(pickLargerSize("6-12 M", "3-6 M", vestInput.sizes)).toBe("6-12 M");
  });

  it("recommends by age band, nearest sold size, and non-age fallbacks", () => {
    expect(recommendSizeByAgeMonths(1, vestInput.sizes)).toBe("0-3 M");
    expect(recommendSizeByAgeMonths(4, vestInput.sizes)).toBe("3-6 M");
    expect(recommendSizeByAgeMonths(14, vestInput.sizes)).toBe("12-18 M");
    expect(recommendSizeByAgeMonths(20, vestInput.sizes)).toBe("18-24 M");
    expect(recommendSizeByAgeMonths(8, ["0-3M", "3-6M"])).toBe("3-6M");
    expect(recommendSizeByAgeMonths(8, ["3-6M", "18-24M"])).toBe("3-6M");
    expect(recommendSizeByAgeMonths(8, ["3-6M", "12-18M"])).toBe("12-18M");
    expect(recommendSizeByAgeMonths(8, ["S", "M"])).toBe("S");
    expect(recommendSizeByAgeMonths(8, [])).toBeNull();
  });

  it("falls through from unusable length to age, otherwise returns null", () => {
    expect(
      resolveRecommendedSize({ lengthCm: "abc", birthDate: "2025-11-29", ...vestInput, now }),
    ).toBe("6-12 M");
    expect(resolveRecommendedSize({ lengthCm: "  ", ...vestInput })).toBeNull();
    expect(resolveRecommendedSize({ birthDate: "not-a-date", ...vestInput, now })).toBeNull();
    expect(resolveRecommendedSize({ lengthCm: "32", ...noChartInput })).toBeNull();
  });
});
