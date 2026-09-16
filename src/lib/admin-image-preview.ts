export const ADMIN_IMAGE_PREVIEW_WIDTH = 240;

/**
 * Same-origin admin thumbnail URL. Catalog images on the public R2 host are
 * resized by GET /api/v1/media/preview; blob/data/site-relative paths pass through.
 */
export function adminImagePreviewSrc(url: string | null | undefined): string {
  const trimmed = (url ?? "").trim();
  if (!trimmed) return "";
  if (trimmed.startsWith("data:") || trimmed.startsWith("blob:")) return trimmed;
  if (trimmed.startsWith("/") && !trimmed.startsWith("//")) return trimmed;
  if (!/^https?:\/\//i.test(trimmed)) return trimmed;
  return `/api/v1/media/preview?u=${encodeURIComponent(trimmed)}&w=${ADMIN_IMAGE_PREVIEW_WIDTH}`;
}
