/** Paths only — no http(s), mailto, javascript, or protocol-relative URLs. */
const UNSAFE_SCHEME_RE = /^[a-z][a-z0-9+.-]*:/i;

/**
 * True for empty or on-site app paths like /shop, /product/foo (not //evil.com or https://…).
 */
export function isInternalSitePath(value?: string | null): boolean {
  const v = (value ?? "").trim();
  if (!v) return true;
  if (UNSAFE_SCHEME_RE.test(v) || v.startsWith("//")) return false;
  return v.startsWith("/") && !v.startsWith("//");
}

export function internalSitePathError(value?: string | null): string | null {
  const v = (value ?? "").trim();
  if (!v) return null;
  if (isInternalSitePath(v)) return null;
  return "Link must be an on-site path starting with / (e.g. /shop, /gift). External URLs are not allowed.";
}

/** Use for hero/carousel CTAs — invalid or external values fall back to a safe default. */
export function resolveInternalSitePath(value?: string | null, fallback = "/shop"): string {
  const v = (value ?? "").trim();
  if (!v) return fallback;
  return isInternalSitePath(v) ? v : fallback;
}
