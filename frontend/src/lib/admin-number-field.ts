/** Display value for admin number inputs — empty when unset. */
export function numberFieldDisplay(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return "";
  return String(value);
}

/** Parse JSON/import values; blank and invalid become null. Preserves 0. */
export function optionalNumberFromUnknown(value: unknown): number | null {
  if (value == null || value === "") return null;
  if (typeof value === "number") return Number.isFinite(value) ? value : null;
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

/** Bulk import: missing/invalid → fallback, then floor and clamp to ≥ 0. */
export function coerceNonNegativeIntFromUnknown(value: unknown, fallback = 0): number {
  const parsed = optionalNumberFromUnknown(value);
  const n = parsed ?? fallback;
  return Math.max(0, Math.floor(n));
}

/** Parse admin number input; blank stays unset (null). */
export function parseNumberField(raw: string): number | null {
  const trimmed = raw.trim();
  if (!trimmed) return null;
  const n = Number(trimmed);
  return Number.isFinite(n) ? n : null;
}

/** Coalesce to a number on save when empty should map to a backend default. */
export function numberFieldOr(value: number | null | undefined, fallback: number): number {
  if (value == null || Number.isNaN(value)) return fallback;
  return value;
}

type RequireNumberOptions = {
  /** When true, zero is accepted (e.g. stock, sort order). Default false. */
  allowZero?: boolean;
  min?: number;
  max?: number;
};

/** Returns an error message when a required admin number field is blank or invalid. */
export function requireNumberField(
  value: number | null | undefined,
  label: string,
  options: RequireNumberOptions = {},
): string | null {
  const { allowZero = false, min = allowZero ? 0 : undefined, max } = options;
  if (value == null || Number.isNaN(value)) return `${label} is required.`;
  if (min != null && value < min) {
    return `${label} must be ${min} or greater.`;
  }
  if (max != null && value > max) {
    return `${label} must be ${max} or less.`;
  }
  if (!allowZero && value <= 0) return `${label} must be greater than 0.`;
  return null;
}
