import { describe, expect, it } from "vitest";
import {
  ageMonthsFromBirthDate,
  checkSizeFit,
  clampQuantity,
  measurementLimitsForSizes,
  pickLargerSize,
  recommendSizeByAgeMonths,
  recommendSizeByChest,
  recommendSizeByHeight,
  resolveRecommendedSize,
  sizeGuideRowsForProduct,
  validateBirthDate,
  validateChestCm,
  validateHeightCm,
  validateQuantity,
  validateVirtualFitFile,
} from "./product-fit";

const sizes = ["0-3m", "3-6m", "6-12m", "12-18m", "18-24m"];
const vestSizes = ["0-3 M", "3-6 M", "6-12 M"];
const now = new Date("2026-05-29T12:00:00");

describe("product fit validators", () => {
  it("validates height and chest against shared measurement guide", () => {
    expect(validateHeightCm("68")).toEqual({ ok: true, value: 68 });
    expect(validateHeightCm("").ok).toBe(false);
    expect(validateHeightCm("20").ok).toBe(false);
    expect(validateHeightCm("150").ok).toBe(false);
    expect(validateHeightCm("abc").ok).toBe(false);
    expect(validateHeightCm("80", { heightMin: 45, heightMax: 74 }).ok).toBe(false);
    expect(validateHeightCm("70", { heightMin: 45, heightMax: 74 })).toEqual({ ok: true, value: 70 });
    expect(validateChestCm("")).toEqual({ ok: true, value: null });
    expect(validateChestCm("45")).toEqual({ ok: true, value: 45 });
    expect(validateChestCm("10").ok).toBe(false);
    expect(validateChestCm("bad").ok).toBe(false);
    expect(validateChestCm("50", { chestMin: 36, chestMax: 48 }).ok).toBe(false);
  });

  it("derives measurement limits from product SKU sizes", () => {
    expect(measurementLimitsForSizes(vestSizes)).toEqual({
      heightMin: 45,
      heightMax: 74,
      chestMin: 36,
      chestMax: 48,
    });
    expect(measurementLimitsForSizes(sizes)).toEqual({
      heightMin: 45,
      heightMax: 90,
      chestMin: 36,
      chestMax: 54,
    });
    expect(measurementLimitsForSizes(["One Size"])).toBeNull();
  });

  it("validates birth dates and computes age in months", () => {
    expect(validateBirthDate("", now).ok).toBe(false);
    expect(validateBirthDate("bad-date", now).ok).toBe(false);
    expect(validateBirthDate("2026-06-01", now).ok).toBe(false);
    expect(validateBirthDate("2020-01-01", now).ok).toBe(false);
    expect(validateBirthDate("2025-11-29", now)).toEqual({ ok: true, value: "2025-11-29" });
    expect(validateBirthDate("2025-05-30", now)).toEqual({ ok: true, value: "2025-05-30" });
    expect(validateBirthDate("2025-13-40", now)).toEqual({ ok: false, error: "Use a valid date" });
    expect(ageMonthsFromBirthDate("2025-11-29", now)).toBe(6);
    expect(ageMonthsFromBirthDate("invalid", now)).toBeNull();
    expect(ageMonthsFromBirthDate("2026-06-15", now)).toBeNull();
    expect(ageMonthsFromBirthDate("2025-05-30", new Date("2026-05-29T12:00:00"))).toBe(11);
  });

  it("recommends sizes from height across product bands only", () => {
    expect(recommendSizeByHeight(50, sizes)).toBe("0-3m");
    expect(recommendSizeByHeight(60, sizes)).toBe("3-6m");
    expect(recommendSizeByHeight(68, sizes)).toBe("6-12m");
    expect(recommendSizeByHeight(78, sizes)).toBe("12-18m");
    expect(recommendSizeByHeight(90, sizes)).toBe("18-24m");
    expect(recommendSizeByHeight(90, [])).toBeNull();
    expect(recommendSizeByHeight(68, ["One Size"])).toBe("One Size");
    expect(recommendSizeByHeight(70, ["0-3M", "3-6M", "6-12M", "12-18M", "18-24M"])).toBe("6-12M");
    // Boundary prefers the roomier overlapping band
    expect(recommendSizeByHeight(58, sizes)).toBe("3-6m");
    // Must not invent sizes this SKU does not carry
    expect(recommendSizeByHeight(80, vestSizes)).toBeNull();
    expect(recommendSizeByHeight(70, vestSizes)).toBe("6-12 M");
  });

  it("recommends sizes from age across available SKU bands", () => {
    expect(recommendSizeByAgeMonths(1, sizes)).toBe("0-3m");
    expect(recommendSizeByAgeMonths(4, sizes)).toBe("3-6m");
    expect(recommendSizeByAgeMonths(8, sizes)).toBe("6-12m");
    expect(recommendSizeByAgeMonths(15, sizes)).toBe("12-18m");
    expect(recommendSizeByAgeMonths(20, sizes)).toBe("18-24m");
    expect(recommendSizeByAgeMonths(20, ["18-24m"])).toBe("18-24m");
    expect(recommendSizeByAgeMonths(20, [])).toBeNull();
    expect(recommendSizeByAgeMonths(20, vestSizes)).toBe("6-12 M");
  });

  it("recommends from chest alone and combines with height", () => {
    expect(recommendSizeByChest(42, sizes)).toBe("0-3m");
    expect(recommendSizeByChest(46, sizes)).toBe("6-12m");
    expect(recommendSizeByChest(46, vestSizes)).toBe("6-12 M");
    expect(recommendSizeByChest(52, vestSizes)).toBeNull();
    expect(pickLargerSize("3-6m", "6-12m", sizes)).toBe("6-12m");
    expect(checkSizeFit({ heightCm: "68", chestCm: "", sizes })).toEqual({ ok: true, size: "6-12m" });
    expect(checkSizeFit({ heightCm: "60", chestCm: "49", sizes })).toEqual({ ok: true, size: "12-18m" });
    expect(checkSizeFit({ heightCm: "", chestCm: "45", sizes })).toEqual({ ok: true, size: "6-12m" });
    expect(checkSizeFit({ heightCm: "", chestCm: "46", sizes: vestSizes })).toEqual({
      ok: true,
      size: "6-12 M",
    });
    expect(checkSizeFit({ heightCm: "100", chestCm: "", sizes: vestSizes }).ok).toBe(false);
    expect(checkSizeFit({ heightCm: "68", chestCm: "bad", sizes }).ok).toBe(false);
    expect(checkSizeFit({ heightCm: "", chestCm: "", sizes }).ok).toBe(false);
  });

  it("resolves recommended size from height or birth date", () => {
    expect(resolveRecommendedSize({ heightCm: "68", sizes })).toBe("6-12m");
    expect(resolveRecommendedSize({ birthDate: "2025-11-29", sizes, now })).toBe("6-12m");
    expect(resolveRecommendedSize({ heightCm: "abc", birthDate: "2025-11-29", sizes, now })).toBe("6-12m");
    expect(resolveRecommendedSize({ heightCm: "150", birthDate: "2025-11-29", sizes, now })).toBe("6-12m");
    expect(resolveRecommendedSize({ heightCm: "abc", birthDate: "bad", sizes, now })).toBeNull();
    expect(resolveRecommendedSize({ sizes })).toBeNull();
    expect(resolveRecommendedSize({ birthDate: "2026-06-15", sizes, now })).toBeNull();
    expect(resolveRecommendedSize({ heightCm: "80", sizes: vestSizes })).toBeNull();
  });

  it("validates quantity and virtual fit uploads", () => {
    expect(validateQuantity(1)).toEqual({ ok: true, value: 1 });
    expect(validateQuantity(1.5).ok).toBe(false);
    expect(validateQuantity(11).ok).toBe(false);
    expect(clampQuantity(0)).toBe(1);
    expect(clampQuantity(99)).toBe(10);

    expect(validateVirtualFitFile(null).ok).toBe(false);
    expect(validateVirtualFitFile(new File(["x"], "pic.txt", { type: "text/plain" })).ok).toBe(false);
    expect(validateVirtualFitFile(new File(["x"], "pic.jpg", { type: "image/jpeg" }))).toEqual({ ok: true });
    expect(
      validateVirtualFitFile(new File([new Uint8Array(6 * 1024 * 1024)], "big.jpg", { type: "image/jpeg" })).ok,
    ).toBe(false);
  });

  it("builds size guide rows only for product sizes", () => {
    expect(sizeGuideRowsForProduct(["0-3 M", "3-6 M", "6-12 M"])).toEqual([
      { size: "0-3 M", height: "45–58 cm", chest: "36–43 cm" },
      { size: "3-6 M", height: "58–67 cm", chest: "43–45 cm" },
      { size: "6-12 M", height: "67–74 cm", chest: "45–48 cm" },
    ]);
    expect(sizeGuideRowsForProduct(["One Size"])).toEqual([
      { size: "One Size", height: "—", chest: "—" },
    ]);
    expect(sizeGuideRowsForProduct(["0-3", "3-6", "6-12"])).toEqual([
      { size: "0-3", height: "45–58 cm", chest: "36–43 cm" },
      { size: "3-6", height: "58–67 cm", chest: "43–45 cm" },
      { size: "6-12", height: "67–74 cm", chest: "45–48 cm" },
    ]);
  });
});
