/** Default hero overlines when a banner has no eyebrow saved in admin. */
export const DEFAULT_BANNER_EYEBROW_FIRST = "Featured";
export const DEFAULT_BANNER_EYEBROW_REST = "Just in";

/**
 * @param slideIndex Position among visible hero slides (after image filter), not raw DB sort_order.
 */
export function resolveBannerEyebrow(eyebrow: string | null | undefined, slideIndex: number): string {
  const trimmed = (eyebrow ?? "").trim();
  if (trimmed) return trimmed;
  return slideIndex === 0 ? DEFAULT_BANNER_EYEBROW_FIRST : DEFAULT_BANNER_EYEBROW_REST;
}
