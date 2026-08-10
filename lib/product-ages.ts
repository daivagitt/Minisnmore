import { ages as ALL_AGE_BANDS, type AgeRange } from "@/data/products";

const SIZE_TO_AGE: Record<string, AgeRange> = {
  "0-3m": "0-3m",
  "3-6m": "3-6m",
  "6-12m": "6-12m",
  "12-18m": "12-18m",
  "18-24m": "18-24m",
};

const UNIVERSAL_SIZES = new Set(["freesize", "onesize", "one size", "os", "free", "universal"]);

const LETTER_SIZE_ORDER = ["xxs", "xs", "s", "m", "l", "xl", "xxl", "xxxl"];

export function normalizeSizeKey(size: string): string {
  return size.trim().toLowerCase().replace(/\s+/g, "");
}

/** Ascending sort index for size labels (age bands first, then letter sizes, universal last). */
export function sizeBandOrderIndex(size: string): number {
  const key = normalizeSizeKey(size);
  if (!key) return 9999;
  if (UNIVERSAL_SIZES.has(key)) return 9000;
  const band = SIZE_TO_AGE[key];
  if (band) return ALL_AGE_BANDS.indexOf(band);
  const letterIdx = LETTER_SIZE_ORDER.indexOf(key);
  if (letterIdx >= 0) return 100 + letterIdx;
  const numeric = Number(key.replace(/[^\d.]/g, ""));
  if (Number.isFinite(numeric) && numeric > 0) return 200 + numeric;
  return 500;
}

/** Return sizes in ascending order (youngest age band → oldest; universal sizes last). */
export function sortSizesAsc(sizes: string[]): string[] {
  return [...sizes].sort((a, b) => {
    const orderDiff = sizeBandOrderIndex(a) - sizeBandOrderIndex(b);
    if (orderDiff !== 0) return orderDiff;
    return a.localeCompare(b, undefined, { numeric: true, sensitivity: "base" });
  });
}

/** Sort canonical age bands in ascending order. */
export function sortAgeBands(bands: AgeRange[]): AgeRange[] {
  return [...bands].sort((a, b) => ALL_AGE_BANDS.indexOf(a) - ALL_AGE_BANDS.indexOf(b));
}

/** Map a product size label (e.g. `0-3M`, `6-12m`) to a shop age band. */
export function sizeToAgeBand(size: string): AgeRange | null {
  const key = normalizeSizeKey(size);
  if (!key || UNIVERSAL_SIZES.has(key)) return null;
  return SIZE_TO_AGE[key] ?? null;
}

/**
 * Derive shop age filters from product sizes. Falls back to month range when sizes
 * are missing or only universal (FreeSize).
 */
export function agesFromSizes(
  sizes: string[],
  fallback?: { min: number | null | undefined; max: number | null | undefined },
): AgeRange[] {
  const hits = new Set<AgeRange>();
  let hasUniversal = false;

  for (const size of sizes) {
    const key = normalizeSizeKey(size);
    if (!key) continue;
    if (UNIVERSAL_SIZES.has(key)) {
      hasUniversal = true;
      continue;
    }
    const band = SIZE_TO_AGE[key];
    if (band) hits.add(band);
  }

  if (hits.size > 0) return sortAgeBands([...hits]);
  if (hasUniversal) return [...ALL_AGE_BANDS];

  return bandsFromMonths(fallback?.min, fallback?.max);
}

function bandsFromMonths(min: number | null | undefined, max: number | null | undefined): AgeRange[] {
  const lo = typeof min === "number" ? min : 0;
  const hi = typeof max === "number" ? max : 24;
  const all: { range: AgeRange; lo: number; hi: number }[] = [
    { range: "0-3m", lo: 0, hi: 3 },
    { range: "3-6m", lo: 3, hi: 6 },
    { range: "6-12m", lo: 6, hi: 12 },
    { range: "12-18m", lo: 12, hi: 18 },
    { range: "18-24m", lo: 18, hi: 24 },
  ];
  const matched = all.filter((b) => b.lo < hi && b.hi > lo).map((b) => b.range);
  return matched.length ? matched : ["3-6m"];
}
