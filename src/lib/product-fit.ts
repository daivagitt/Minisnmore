import type { AgeRange } from "@/data/products";
import { normalizeSizeKey, sizeToAgeBand } from "@/lib/product-ages";
import {
  filterSizeChartToProductSizes,
  fitPointsFromSizeChart,
  sizeChartForProduct,
  type ChartFitPoint,
  type ProductSizeChart,
} from "@/lib/product-size-charts";

export const FIT_LIMITS = {
  /** Fallback window when a product chart is missing (cm). */
  lengthMin: 15,
  lengthMax: 50,
  chestMin: 18,
  chestMax: 35,
  qtyMin: 1,
  qtyMax: 10,
  maxBabyAgeMonths: 36,
  virtualFitMaxBytes: 5 * 1024 * 1024,
  /** Sheet tolerance applied when building inclusive ranges around exact points. */
  measureToleranceCm: 1,
} as const;

export type FieldValidation =
  | { ok: true; value: number }
  | { ok: false; error: string };

export type BirthDateValidation =
  | { ok: true; value: string }
  | { ok: false; error: string };

export type FileValidation = { ok: true } | { ok: false; error: string };

export type MeasurementLimits = {
  lengthMin: number;
  lengthMax: number;
  chestMin: number;
  chestMax: number;
  hasLength: boolean;
  hasChest: boolean;
};

export type SizeGuideRow = {
  size: string;
  length: string;
  chest: string;
  lengthCm: number | null;
  chestCm: number | null;
};

type BandRange = {
  size: string;
  lengthMin: number | null;
  lengthMax: number | null;
  chestMin: number | null;
  chestMax: number | null;
};

function formatCm(value: number): string {
  return Number.isInteger(value) ? `${value} cm` : `${value} cm`;
}

function formatCmRange(min: number, max: number): string {
  if (min === max) return formatCm(min);
  return `${min}–${max} cm`;
}

/**
 * Build non-overlapping ranges from exact MnM sheet points.
 * Each band ends at the sheet value; the next starts at previous + 1.
 * Example MnM-4 length 27 / 30 / 33… → 25–27, 28–30, 31–33…
 */
function rangesFromPoints(
  points: Array<{ size: string; value: number }>,
): Array<{ size: string; min: number; max: number }> {
  if (points.length === 0) return [];
  const tol = FIT_LIMITS.measureToleranceCm;
  return points.map((p, i) => {
    const prev = points[i - 1];
    const next = points[i + 1];
    const max = p.value;
    let min: number;
    if (prev) {
      min = prev.value + 1;
    } else if (next) {
      // Same span as the step to the next size: e.g. 27→30 ⇒ first band 25–27
      min = p.value - (next.value - p.value) + 1;
    } else {
      min = Math.max(0, p.value - tol);
    }
    return { size: p.size, min, max };
  });
}

function inInclusiveRange(value: number, min: number, max: number): boolean {
  return value >= min && value <= max;
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

export function validateLengthCm(
  raw: string,
  limits?: Pick<MeasurementLimits, "lengthMin" | "lengthMax">,
): FieldValidation | { ok: true; value: number | null } {
  const trimmed = raw.trim();
  if (!trimmed) return { ok: true, value: null };
  const value = Number(trimmed);
  if (!Number.isFinite(value)) return { ok: false, error: "Enter a valid length" };
  const min = limits?.lengthMin ?? FIT_LIMITS.lengthMin;
  const max = limits?.lengthMax ?? FIT_LIMITS.lengthMax;
  if (value < min || value > max) {
    return { ok: false, error: `Length should be ${min}–${max} cm for this item` };
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

function matchBandToProductSize(band: string, sizes: string[]): string | null {
  const norm = normalizeSizeLabel(band);
  return sizes.find((s) => normalizeSizeLabel(s) === norm) ?? null;
}

function chartPointsForProduct(input: {
  sku?: string | null;
  name?: string | null;
  slug?: string | null;
  sizes: string[];
  sizeChart?: ProductSizeChart | null;
}): ChartFitPoint[] {
  const chart = sizeChartForProduct(input);
  if (!chart) return [];
  const filtered = filterSizeChartToProductSizes(chart, input.sizes);
  return fitPointsFromSizeChart(filtered)
    .map((p) => {
      const size = matchBandToProductSize(p.band, input.sizes);
      if (!size) return null;
      return { ...p, band: size };
    })
    .filter((p): p is ChartFitPoint => p != null);
}

function buildBandRanges(points: ChartFitPoint[]): BandRange[] {
  const lengthPts = points
    .filter((p) => p.lengthCm != null)
    .map((p) => ({ size: p.band, value: p.lengthCm! }));
  const chestPts = points
    .filter((p) => p.chestCm != null)
    .map((p) => ({ size: p.band, value: p.chestCm! }));
  const lengthRanges = rangesFromPoints(lengthPts);
  const chestRanges = rangesFromPoints(chestPts);

  return points.map((p) => {
    const lr = lengthRanges.find((r) => r.size === p.band);
    const cr = chestRanges.find((r) => r.size === p.band);
    return {
      size: p.band,
      lengthMin: lr?.min ?? null,
      lengthMax: lr?.max ?? null,
      chestMin: cr?.min ?? null,
      chestMax: cr?.max ?? null,
    };
  });
}

/** Size guide rows (Length + Chest) from the approved MnM sheet for this product. */
export function sizeGuideRowsForProduct(input: {
  sku?: string | null;
  name?: string | null;
  slug?: string | null;
  sizes: string[];
  sizeChart?: ProductSizeChart | null;
}): SizeGuideRow[] {
  const points = chartPointsForProduct(input);
  if (points.length === 0) {
    return input.sizes.map((size) => ({
      size,
      length: "—",
      chest: "—",
      lengthCm: null,
      chestCm: null,
    }));
  }

  const ranges = buildBandRanges(points);
  return points.map((p) => {
    const r = ranges.find((x) => x.size === p.band);
    return {
      size: p.band,
      length:
        r?.lengthMin != null && r.lengthMax != null
          ? formatCmRange(roundDisplay(r.lengthMin), roundDisplay(r.lengthMax))
          : p.lengthCm != null
            ? formatCm(p.lengthCm)
            : "—",
      chest:
        r?.chestMin != null && r.chestMax != null
          ? formatCmRange(roundDisplay(r.chestMin), roundDisplay(r.chestMax))
          : p.chestCm != null
            ? formatCm(p.chestCm)
            : "—",
      lengthCm: p.lengthCm,
      chestCm: p.chestCm,
    };
  });
}

function roundDisplay(n: number): number {
  return Math.round(n * 10) / 10;
}

/** Allowed length/chest window for the calculator on this product. */
export function measurementLimitsForProduct(input: {
  sku?: string | null;
  name?: string | null;
  slug?: string | null;
  sizes: string[];
  sizeChart?: ProductSizeChart | null;
}): MeasurementLimits | null {
  const points = chartPointsForProduct(input);
  if (points.length === 0) return null;

  const lengths = points.map((p) => p.lengthCm).filter((n): n is number => n != null);
  const chests = points.map((p) => p.chestCm).filter((n): n is number => n != null);
  if (lengths.length === 0 && chests.length === 0) return null;

  const ranges = buildBandRanges(points);
  const lengthMins = ranges.map((r) => r.lengthMin).filter((n): n is number => n != null);
  const lengthMaxs = ranges.map((r) => r.lengthMax).filter((n): n is number => n != null);
  const chestMins = ranges.map((r) => r.chestMin).filter((n): n is number => n != null);
  const chestMaxs = ranges.map((r) => r.chestMax).filter((n): n is number => n != null);

  return {
    lengthMin: lengthMins.length ? roundDisplay(Math.min(...lengthMins)) : FIT_LIMITS.lengthMin,
    lengthMax: lengthMaxs.length ? roundDisplay(Math.max(...lengthMaxs)) : FIT_LIMITS.lengthMax,
    chestMin: chestMins.length ? roundDisplay(Math.min(...chestMins)) : FIT_LIMITS.chestMin,
    chestMax: chestMaxs.length ? roundDisplay(Math.max(...chestMaxs)) : FIT_LIMITS.chestMax,
    hasLength: lengths.length > 0,
    hasChest: chests.length > 0,
  };
}

/** @deprecated Prefer measurementLimitsForProduct — kept for callers that only have sizes. */
export function measurementLimitsForSizes(sizes: string[]): MeasurementLimits | null {
  return measurementLimitsForProduct({ sizes });
}

export function matchProductSize(label: string, sizes: string[]): string | null {
  const norm = normalizeSizeLabel(label);
  return sizes.find((s) => normalizeSizeLabel(s) === norm) ?? null;
}

function sizesWithAgeBands(sizes: string[]): string[] {
  return sizes.filter((s) => sizeToAgeBand(s) != null);
}

function sizeBandIndex(label: string, sizes: string[]): number {
  const norm = normalizeSizeLabel(label);
  const idx = sizes.findIndex((s) => normalizeSizeLabel(s) === norm);
  return idx >= 0 ? idx : sizes.length - 1;
}

/** Prefer the roomier band when length and chest suggest different sizes. */
export function pickLargerSize(a: string, b: string, sizes: string[]): string {
  return sizeBandIndex(a, sizes) >= sizeBandIndex(b, sizes) ? a : b;
}

function recommendByMetric(
  value: number,
  ranges: BandRange[],
  sizes: string[],
  kind: "length" | "chest",
): string | null {
  const matchedSizes: string[] = [];
  for (const r of ranges) {
    const min = kind === "length" ? r.lengthMin : r.chestMin;
    const max = kind === "length" ? r.lengthMax : r.chestMax;
    if (min == null || max == null) continue;
    if (inInclusiveRange(value, min, max)) matchedSizes.push(r.size);
  }
  if (matchedSizes.length === 0) return null;
  return matchedSizes.reduce((best, size) => pickLargerSize(best, size, sizes));
}

export function recommendSizeByLength(
  length: number,
  input: {
    sku?: string | null;
    name?: string | null;
    slug?: string | null;
    sizes: string[];
    sizeChart?: ProductSizeChart | null;
  },
): string | null {
  const points = chartPointsForProduct(input);
  if (points.length === 0) return null;
  return recommendByMetric(length, buildBandRanges(points), input.sizes, "length");
}

export function recommendSizeByChest(
  chest: number,
  input: {
    sku?: string | null;
    name?: string | null;
    slug?: string | null;
    sizes: string[];
    sizeChart?: ProductSizeChart | null;
  },
): string | null {
  const points = chartPointsForProduct(input);
  if (points.length === 0) return null;
  return recommendByMetric(chest, buildBandRanges(points), input.sizes, "chest");
}

export type SizeFitResult =
  | { ok: true; size: string }
  | { ok: false; error: string };

export function checkSizeFit(input: {
  lengthCm?: string;
  chestCm?: string;
  sku?: string | null;
  name?: string | null;
  slug?: string | null;
  sizes: string[];
  sizeChart?: ProductSizeChart | null;
}): SizeFitResult {
  const lengthRaw = input.lengthCm?.trim() ?? "";
  const chestRaw = input.chestCm?.trim() ?? "";
  if (!lengthRaw && !chestRaw) {
    return { ok: false, error: "Enter length or chest measurement" };
  }

  const productInput = {
    sku: input.sku,
    name: input.name,
    slug: input.slug,
    sizes: input.sizes,
    sizeChart: input.sizeChart,
  };
  const limits = measurementLimitsForProduct(productInput);
  if (!limits) {
    return { ok: false, error: "Size calculator is not available for this item" };
  }

  let fromLength: string | null = null;
  let fromChest: string | null = null;

  if (lengthRaw) {
    if (!limits.hasLength) {
      return { ok: false, error: "This item has no length guide on the measurement sheet" };
    }
    const length = validateLengthCm(lengthRaw, limits);
    if (!length.ok) return { ok: false, error: length.error };
    if (length.value === null) return { ok: false, error: "Enter a valid length" };
    fromLength = recommendSizeByLength(length.value, productInput);
    if (!fromLength) {
      return {
        ok: false,
        error: `No size on this item for length ${length.value} cm (${roundDisplay(limits.lengthMin)}–${roundDisplay(limits.lengthMax)} cm)`,
      };
    }
  }

  if (chestRaw) {
    if (!limits.hasChest) {
      return { ok: false, error: "This item has no chest guide on the measurement sheet" };
    }
    const chest = validateChestCm(chestRaw, limits);
    if (!chest.ok) return { ok: false, error: chest.error };
    if (chest.value === null) return { ok: false, error: "Enter a valid chest measurement" };
    fromChest = recommendSizeByChest(chest.value, productInput);
    if (!fromChest) {
      return {
        ok: false,
        error: `No size on this item for chest ${chest.value} cm (${roundDisplay(limits.chestMin)}–${roundDisplay(limits.chestMax)} cm)`,
      };
    }
  }

  if (fromLength && fromChest) {
    return { ok: true, size: pickLargerSize(fromLength, fromChest, input.sizes) };
  }
  return { ok: true, size: (fromLength ?? fromChest)! };
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
  lengthCm?: string;
  birthDate?: string;
  sku?: string | null;
  name?: string | null;
  slug?: string | null;
  sizes: string[];
  sizeChart?: ProductSizeChart | null;
  now?: Date;
}): string | null {
  const productInput = {
    sku: input.sku,
    name: input.name,
    slug: input.slug,
    sizes: input.sizes,
    sizeChart: input.sizeChart,
  };
  const lengthRaw = input.lengthCm?.trim() ?? "";
  if (lengthRaw) {
    const limits = measurementLimitsForProduct(productInput) ?? undefined;
    const length = validateLengthCm(lengthRaw, limits);
    if (length.ok && length.value != null) {
      return recommendSizeByLength(length.value, productInput);
    }
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
