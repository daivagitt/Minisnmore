/** Format string[] for comma-separated admin inputs. */
export function formatCommaList(values: string[]): string {
  return values.join(", ");
}

/** Parse comma-separated text into trimmed non-empty tokens. */
export function parseCommaList(raw: string): string[] {
  return raw
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);
}

/** Display age range as "min, max" for admin inputs. */
export function formatAgeRangeField(
  min: number | null | undefined,
  max: number | null | undefined,
): string {
  if (min == null && max == null) return "";
  if (min != null && max != null) return `${min}, ${max}`;
  if (min != null) return String(min);
  return String(max);
}

/** Parse "min, max" age range; blank parts stay null while typing. */
export function parseAgeRangeField(raw: string): { min: number | null; max: number | null } {
  const trimmed = raw.trim();
  if (!trimmed) return { min: null, max: null };

  const parts = trimmed.split(",").map((s) => s.trim());
  const parsePart = (part: string | undefined): number | null => {
    if (part == null || part === "") return null;
    const n = Number(part);
    return Number.isFinite(n) ? n : null;
  };

  const min = parsePart(parts[0]);
  const max = parts.length > 1 ? parsePart(parts[1]) : null;
  return { min, max };
}
