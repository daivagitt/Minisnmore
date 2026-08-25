import type { AgeRange } from "@/data/products";
import { normalizeSizeKey, sizeToAgeBand } from "@/lib/product-ages";

export const FIT_LIMITS = {
  heightMin: 45,
  heightMax: 90,
  chestMin: 36,
  chestMax: 54,
  qtyMin: 1,
  qtyMax: 10,
  maxBabyAgeMonths: 36,
  virtualFitMaxBytes: 5 * 1024 * 1024,
} as const;

export type FieldValidation =
  | { ok: true; value: number }
  | { ok: false; error: string };

export type BirthDateValidation =
  | { ok: true; value: string }
  | { ok: false; error: string };

export type FileValidation = { ok: true } | { ok: false; error: string };

export type MeasurementLimits = {
  heightMin: number;
  heightMax: number;
  chestMin: number;
  chestMax: number;
};

type BandMeasurements = MeasurementLimits;

/** Shared size-guide specs (cm) — source of truth for chart + calculator. */
const BAND_MEASUREMENTS: Record<AgeRange, BandMeasurements> = {
  "0-3m": { heightMin: 45, heightMax: 58, chestMin: 36, chestMax: 43 },
  "3-6m": { heightMin: 58, heightMax: 67, chestMin: 43, chestMax: 45 },
  "6-12m": { heightMin: 67, heightMax: 74, chestMin: 45, chestMax: 48 },
  "12-18m": { heightMin: 74, heightMax: 82, chestMin: 48, chestMax: 51 },
  "18-24m": { heightMin: 82, heightMax: 90, chestMin: 51, chestMax: 54 },
};

function formatCmRange(min: number, max: number): string {
  return `${min}–${max} cm`;
}

export function ageMonthsFromBirthDate(dob: string, now = new Date()): number | null {
  const parsed = validateBirthDate(dob, now);
  if (!parsed.ok) return null;
  const birth = new Date(`${parsed.value}T00:00:00`);
  let months = (now.getFullYear() - birth.getFullYear()) * 12 + (now.getMonth() - birth.getMonth());
  if (now.getDate() < birth.getDate()) months -= 1;
  return months >= 0 ? months : null;
}

export function validateBirthDate(dob: string, now = new Date()): BirthDateValidation {
  const trimmed = dob.trim();
  if (!trimmed) return { ok: false, error: "Enter baby's birth date" };
  if (!/^\d{4}-\d{2}-\d{2}$/.test(trimmed)) return { ok: false, error: "Use a valid date" };
  const birth = new Date(`${trimmed}T00:00:00`);
  if (Number.isNaN(birth.getTime())) return { ok: false, error: "Use a valid date" };
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  if (birth.getTime() > today.getTime()) return { ok: false, error: "Birth date cannot be in the future" };
  let months = (now.getFullYear() - birth.getFullYear()) * 12 + (now.getMonth() - birth.getMonth());
  if (now.getDate() < birth.getDate()) months -= 1;
  if (months < 0) return { ok: false, error: "Use a valid date" };
  if (months > FIT_LIMITS.maxBabyAgeMonths) {
    return { ok: false, error: "Age calculator supports babies up to 36 months" };
  }
  return { ok: true, value: trimmed };
}

export function validateHeightCm(
  raw: string,
  limits?: Pick<MeasurementLimits, "heightMin" | "heightMax">,
): FieldValidation {
  const trimmed = raw.trim();
  if (!trimmed) return { ok: false, error: "Enter height in cm" };
  const value = Number(trimmed);
  if (!Number.isFinite(value)) return { ok: false, error: "Enter a valid height" };
  const min = limits?.heightMin ?? FIT_LIMITS.heightMin;
  const max = limits?.heightMax ?? FIT_LIMITS.heightMax;
  if (value < min || value > max) {
    return { ok: false, error: `Height should be ${min}–${max} cm for this item` };
  }
  return { ok: true, value };
}

export function validateChestCm(
  raw: string,
  limits?: Pick<MeasurementLimits, "chestMin" | "chestMax">,
): FieldValidation | { ok: true; value: number | null } {
  const trimmed = raw.trim();
  if (!trimmed) return { ok: true, value: null };
  const value = Number(trimmed);
  if (!Number.isFinite(value)) return { ok: false, error: "Enter a valid chest measurement" };
  const min = limits?.chestMin ?? FIT_LIMITS.chestMin;
  const max = limits?.chestMax ?? FIT_LIMITS.chestMax;
  if (value < min || value > max) {
    return { ok: false, error: `Chest should be ${min}–${max} cm for this item` };
  }
  return { ok: true, value };
}

export function clampQuantity(qty: number): number {
  return Math.max(FIT_LIMITS.qtyMin, Math.min(FIT_LIMITS.qtyMax, Math.trunc(qty)));
}

export function validateQuantity(qty: number): FieldValidation {
  if (!Number.isInteger(qty)) return { ok: false, error: "Quantity must be a whole number" };
  if (qty < FIT_LIMITS.qtyMin || qty > FIT_LIMITS.qtyMax) {
    return { ok: false, error: `Quantity must be between ${FIT_LIMITS.qtyMin} and ${FIT_LIMITS.qtyMax}` };
  }
  return { ok: true, value: qty };
}

export function validateVirtualFitFile(file: File | null | undefined): FileValidation {
  if (!file) return { ok: false, error: "Choose a photo to upload" };
  if (!file.type.startsWith("image/")) return { ok: false, error: "Upload a JPG or PNG photo" };
  if (file.size > FIT_LIMITS.virtualFitMaxBytes) return { ok: false, error: "Photo must be 5 MB or smaller" };
  return { ok: true };
}

export function normalizeSizeLabel(size: string): string {
  return normalizeSizeKey(size);
}

export type SizeGuideRow = { size: string; height: string; chest: string };

/** Height/chest guide rows for the sizes this product actually carries. */
export function sizeGuideRowsForProduct(sizes: string[]): SizeGuideRow[] {
  return sizes.map((size) => {
    const band = sizeToAgeBand(size);
    if (band) {
      const m = BAND_MEASUREMENTS[band];
      return {
        size,
        height: formatCmRange(m.heightMin, m.heightMax),
        chest: formatCmRange(m.chestMin, m.chestMax),
      };
    }
    return { size, height: "—", chest: "—" };
  });
}

/** Allowed height/chest window from the product's SKU age bands. */
export function measurementLimitsForSizes(sizes: string[]): MeasurementLimits | null {
  let heightMin = Number.POSITIVE_INFINITY;
  let heightMax = Number.NEGATIVE_INFINITY;
  let chestMin = Number.POSITIVE_INFINITY;
  let chestMax = Number.NEGATIVE_INFINITY;
  let found = false;

  for (const size of sizes) {
    const band = sizeToAgeBand(size);
    if (!band) continue;
    found = true;
    const m = BAND_MEASUREMENTS[band];
    heightMin = Math.min(heightMin, m.heightMin);
    heightMax = Math.max(heightMax, m.heightMax);
    chestMin = Math.min(chestMin, m.chestMin);
    chestMax = Math.max(chestMax, m.chestMax);
  }

  if (!found) return null;
  return { heightMin, heightMax, chestMin, chestMax };
}

/** Match a canonical band (e.g. 0-3m) to the product's size string (e.g. 0-3M). */
export function matchProductSize(label: string, sizes: string[]): string | null {
  const norm = normalizeSizeLabel(label);
  return sizes.find((s) => normalizeSizeLabel(s) === norm) ?? null;
}

function sizesWithAgeBands(sizes: string[]): string[] {
  return sizes.filter((s) => sizeToAgeBand(s) != null);
}

function recommendAmongMatchingBands(
  sizes: string[],
  matches: (band: AgeRange) => boolean,
): string | null {
  const bandSizes = sizesWithAgeBands(sizes);
  if (bandSizes.length === 0) return sizes[0] ?? null;

  const matched = bandSizes.filter((size) => {
    const band = sizeToAgeBand(size);
    return band != null && matches(band);
  });
  if (matched.length === 0) return null;

  return matched.reduce((best, size) => pickLargerSize(best, size, sizes));
}

/** Recommend from this item's sizes only, using the shared height guide ranges. */
export function recommendSizeByHeight(height: number, sizes: string[]): string | null {
  return recommendAmongMatchingBands(sizes, (band) => {
    const m = BAND_MEASUREMENTS[band];
    return height >= m.heightMin && height <= m.heightMax;
  });
}

/** Recommend from this item's sizes only, using the shared chest guide ranges. */
export function recommendSizeByChest(chest: number, sizes: string[]): string | null {
  return recommendAmongMatchingBands(sizes, (band) => {
    const m = BAND_MEASUREMENTS[band];
    return chest >= m.chestMin && chest <= m.chestMax;
  });
}

function sizeBandIndex(label: string, sizes: string[]): number {
  const norm = normalizeSizeLabel(label);
  const idx = sizes.findIndex((s) => normalizeSizeLabel(s) === norm);
  return idx >= 0 ? idx : sizes.length - 1;
}

/** Prefer the roomier band when height and chest suggest different sizes. */
export function pickLargerSize(a: string, b: string, sizes: string[]): string {
  return sizeBandIndex(a, sizes) >= sizeBandIndex(b, sizes) ? a : b;
}

export type SizeFitResult =
  | { ok: true; size: string }
  | { ok: false; error: string };

export function checkSizeFit(input: {
  heightCm?: string;
  chestCm?: string;
  sizes: string[];
}): SizeFitResult {
  const heightRaw = input.heightCm?.trim() ?? "";
  const chestRaw = input.chestCm?.trim() ?? "";
  if (!heightRaw && !chestRaw) {
    return { ok: false, error: "Enter height or chest measurement" };
  }

  const limits = measurementLimitsForSizes(input.sizes) ?? {
    heightMin: FIT_LIMITS.heightMin,
    heightMax: FIT_LIMITS.heightMax,
    chestMin: FIT_LIMITS.chestMin,
    chestMax: FIT_LIMITS.chestMax,
  };

  let fromHeight: string | null = null;
  let fromChest: string | null = null;

  if (heightRaw) {
    const height = validateHeightCm(heightRaw, limits);
    if (!height.ok) return { ok: false, error: height.error };
    fromHeight = recommendSizeByHeight(height.value, input.sizes);
    if (!fromHeight) {
      return {
        ok: false,
        error: `No size on this item for height ${height.value} cm (${limits.heightMin}–${limits.heightMax} cm)`,
      };
    }
  }

  if (chestRaw) {
    const chest = validateChestCm(chestRaw, limits);
    if (!chest.ok) return { ok: false, error: chest.error };
    if (chest.value === null) {
      return { ok: false, error: "Enter a valid chest measurement" };
    }
    fromChest = recommendSizeByChest(chest.value, input.sizes);
    if (!fromChest) {
      return {
        ok: false,
        error: `No size on this item for chest ${chest.value} cm (${limits.chestMin}–${limits.chestMax} cm)`,
      };
    }
  }

  if (fromHeight && fromChest) {
    return { ok: true, size: pickLargerSize(fromHeight, fromChest, input.sizes) };
  }
  return { ok: true, size: (fromHeight ?? fromChest)! };
}

export function recommendSizeByAgeMonths(months: number, sizes: string[]): string | null {
  let label: AgeRange;
  if (months < 3) label = "0-3m";
  else if (months < 6) label = "3-6m";
  else if (months < 12) label = "6-12m";
  else if (months < 18) label = "12-18m";
  else label = "18-24m";

  const matched = matchProductSize(label, sizes);
  if (matched) return matched;

  // Prefer nearest roomier available SKU size; otherwise nearest smaller.
  const bandSizes = sizesWithAgeBands(sizes);
  if (bandSizes.length === 0) return sizes[0] ?? null;

  const targetIdx = sizeBandIndex(label, ["0-3m", "3-6m", "6-12m", "12-18m", "18-24m"]);
  let best: string | null = null;
  let bestDist = Number.POSITIVE_INFINITY;
  for (const size of bandSizes) {
    const band = sizeToAgeBand(size)!;
    const idx = sizeBandIndex(band, ["0-3m", "3-6m", "6-12m", "12-18m", "18-24m"]);
    const dist = Math.abs(idx - targetIdx);
    if (dist < bestDist || (dist === bestDist && best != null && pickLargerSize(best, size, sizes) === size)) {
      best = size;
      bestDist = dist;
    }
  }
  return best;
}

export function resolveRecommendedSize(input: {
  heightCm?: string;
  birthDate?: string;
  sizes: string[];
  now?: Date;
}): string | null {
  const limits = measurementLimitsForSizes(input.sizes) ?? undefined;
  const heightRaw = input.heightCm?.trim() ?? "";
  if (heightRaw) {
    const height = validateHeightCm(heightRaw, limits);
    if (height.ok) return recommendSizeByHeight(height.value, input.sizes);
  }
  const dob = input.birthDate?.trim() ?? "";
  if (dob) {
    const birth = validateBirthDate(dob, input.now);
    if (birth.ok) {
      const months = ageMonthsFromBirthDate(birth.value, input.now);
      if (months !== null) return recommendSizeByAgeMonths(months, input.sizes);
    }
  }
  return null;
}
