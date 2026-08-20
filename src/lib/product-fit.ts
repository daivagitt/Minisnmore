import type { AgeRange } from "@/data/products";
import { sizeToAgeBand } from "@/lib/product-ages";

export const FIT_LIMITS = {
  heightMin: 30,
  heightMax: 120,
  chestMin: 20,
  chestMax: 70,
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

export function validateHeightCm(raw: string): FieldValidation {
  const trimmed = raw.trim();
  if (!trimmed) return { ok: false, error: "Enter height in cm" };
  const value = Number(trimmed);
  if (!Number.isFinite(value)) return { ok: false, error: "Enter a valid height" };
  if (value < FIT_LIMITS.heightMin || value > FIT_LIMITS.heightMax) {
    return { ok: false, error: `Height should be ${FIT_LIMITS.heightMin}–${FIT_LIMITS.heightMax} cm` };
  }
  return { ok: true, value };
}

export function validateChestCm(raw: string): FieldValidation | { ok: true; value: number | null } {
  const trimmed = raw.trim();
  if (!trimmed) return { ok: true, value: null };
  const value = Number(trimmed);
  if (!Number.isFinite(value)) return { ok: false, error: "Enter a valid chest measurement" };
  if (value < FIT_LIMITS.chestMin || value > FIT_LIMITS.chestMax) {
    return { ok: false, error: `Chest should be ${FIT_LIMITS.chestMin}–${FIT_LIMITS.chestMax} cm` };
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
  return size.trim().toLowerCase();
}

export type SizeGuideRow = { size: string; weight: string; height: string };

const SIZE_GUIDE_BY_BAND: Record<AgeRange, { weight: string; height: string }> = {
  "0-3m": { weight: "2.5–5 kg", height: "45–58 cm" },
  "3-6m": { weight: "5–7 kg", height: "58–67 cm" },
  "6-12m": { weight: "7–9 kg", height: "67–74 cm" },
  "12-18m": { weight: "9–11 kg", height: "74–82 cm" },
  "18-24m": { weight: "11–13 kg", height: "82–90 cm" },
};

/** Weight/height guide rows for the sizes this product actually carries. */
export function sizeGuideRowsForProduct(sizes: string[]): SizeGuideRow[] {
  return sizes.map((size) => {
    const band = sizeToAgeBand(size);
    if (band) {
      const guide = SIZE_GUIDE_BY_BAND[band];
      return { size, weight: guide.weight, height: guide.height };
    }
    return { size, weight: "—", height: "—" };
  });
}

/** Match a canonical band (e.g. 0-3m) to the product's size string (e.g. 0-3M). */
export function matchProductSize(label: string, sizes: string[]): string | null {
  const norm = normalizeSizeLabel(label);
  return sizes.find((s) => normalizeSizeLabel(s) === norm) ?? null;
}

export function recommendSizeByHeight(height: number, sizes: string[]): string {
  let label: string;
  if (height < 58) label = "0-3m";
  else if (height < 67) label = "3-6m";
  else if (height < 74) label = "6-12m";
  else if (height < 82) label = "12-18m";
  else label = "18-24m";
  return matchProductSize(label, sizes) ?? sizes[sizes.length - 1] ?? label;
}

export function recommendSizeByChest(chest: number, sizes: string[]): string {
  let label: string;
  if (chest < 43) label = "0-3m";
  else if (chest < 45) label = "3-6m";
  else if (chest < 48) label = "6-12m";
  else if (chest < 51) label = "12-18m";
  else label = "18-24m";
  return matchProductSize(label, sizes) ?? sizes[sizes.length - 1] ?? label;
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
  heightCm: string;
  chestCm?: string;
  sizes: string[];
}): SizeFitResult {
  const height = validateHeightCm(input.heightCm);
  if (!height.ok) return { ok: false, error: height.error };
  const chest = validateChestCm(input.chestCm ?? "");
  if (!chest.ok) return { ok: false, error: chest.error };

  let size = recommendSizeByHeight(height.value, input.sizes);
  if (chest.value !== null) {
    size = pickLargerSize(size, recommendSizeByChest(chest.value, input.sizes), input.sizes);
  }
  return { ok: true, size };
}

export function recommendSizeByAgeMonths(months: number, sizes: string[]): string {
  let label: string;
  if (months < 3) label = "0-3m";
  else if (months < 6) label = "3-6m";
  else if (months < 12) label = "6-12m";
  else if (months < 18) label = "12-18m";
  else label = "18-24m";
  return matchProductSize(label, sizes) ?? sizes[sizes.length - 1] ?? label;
}

export function resolveRecommendedSize(input: {
  heightCm?: string;
  birthDate?: string;
  sizes: string[];
  now?: Date;
}): string | null {
  const heightRaw = input.heightCm?.trim() ?? "";
  if (heightRaw) {
    const height = validateHeightCm(heightRaw);
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
