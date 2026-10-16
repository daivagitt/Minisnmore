/** Persist coupon between cart and checkout for the current browser session. */

export type AppliedCoupon = { code: string; off: number };

const STORAGE_KEY = "mnm_applied_coupon";

export function readStoredCoupon(): AppliedCoupon | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as AppliedCoupon;
    if (parsed?.code && Number.isFinite(parsed.off)) return parsed;
  } catch {
    /* ignore */
  }
  return null;
}

export function storeCoupon(coupon: AppliedCoupon | null) {
  if (typeof window === "undefined") return;
  if (!coupon) sessionStorage.removeItem(STORAGE_KEY);
  else sessionStorage.setItem(STORAGE_KEY, JSON.stringify(coupon));
}
