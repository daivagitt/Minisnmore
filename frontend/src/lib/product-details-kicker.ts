export const DETAILS_KICKER_MAX_LEN = 80;

export function normalizeDetailsKicker(raw: unknown): string | null {
  if (typeof raw !== "string") return null;
  const text = raw.replace(/\s+/g, " ").trim();
  return text ? text.slice(0, DETAILS_KICKER_MAX_LEN) : null;
}

export function detailsKickerValidationErrors(raw: unknown): string[] {
  if (raw == null) return [];
  if (typeof raw !== "string") return ["Details card label must be text."];
  if (raw.trim().length > DETAILS_KICKER_MAX_LEN) {
    return [`Details card label must be ${DETAILS_KICKER_MAX_LEN} characters or fewer.`];
  }
  return [];
}

export function productDetailsKicker(product: {
  detailsKicker?: string | null;
  fabric?: string | null;
  category?: string | null;
}): string {
  const custom = normalizeDetailsKicker(product.detailsKicker);
  if (custom) return custom;
  return [product.fabric, product.category]
    .map((part) => (typeof part === "string" ? part.trim() : ""))
    .filter(Boolean)
    .join(" · ");
}
