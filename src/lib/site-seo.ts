/** Production origin for canonical URLs, OG images, and JSON-LD (even on UAT). */
export const SITE_ORIGIN =
  import.meta.env.VITE_SITE_ORIGIN?.trim() || "https://minisnmore.com";

/** True on UAT builds — emits noindex meta so crawlers skip indexing. */
export const IS_UAT = import.meta.env.VITE_SITE_ENV === "uat";

export const SOCIAL_SHARE_IMAGE = `${SITE_ORIGIN}/og-share.jpg`;

export function canonicalUrl(path = "/"): string {
  const normalized = path.startsWith("/") ? path : `/${path}`;
  return normalized === "/" ? `${SITE_ORIGIN}/` : `${SITE_ORIGIN}${normalized}`;
}

/** Global robots meta for UAT; omitted on production builds. */
export function uatNoindexMeta(): { name: "robots"; content: string } | null {
  return IS_UAT ? { name: "robots", content: "noindex, nofollow" } : null;
}
