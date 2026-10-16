/** Derive storefront flags from admin badge text without false positives (e.g. "newborn" ≠ new). */

export function isBestsellerFromBadge(badge: string): boolean {
  const b = badge.trim().toLowerCase();
  if (!b) return false;
  return b === "best" || b === "bestseller" || /\bbestseller\b/.test(b) || /\bbest[\s-]seller\b/.test(b);
}

export function isNewFromBadge(badge: string): boolean {
  const b = badge.trim().toLowerCase();
  if (!b) return false;
  if (b.includes("newborn")) return false;
  return b === "new" || /\bnew arrival\b/.test(b) || /\bnew-arrival\b/.test(b);
}

export function isTrendingFromBadge(badge: string): boolean {
  const b = badge.trim().toLowerCase();
  if (!b) return false;
  return b === "trending" || /\btrending\b/.test(b) || /\btrend\b/.test(b);
}
