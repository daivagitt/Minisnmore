import type { Product } from "@/data/products";
import { resolveAssetUrl } from "@/lib/asset-url";
import { sanitizePublicUrl } from "@/lib/utils";

export type WatchReel = { video: string; slug: string };

/** Parse Admin → Home → Watch & Shop payload: { "reels": [{ "video": "…", "product_slug": "…" }] } */
export function parseWatchReels(payload: Record<string, unknown>): WatchReel[] {
  const reels = payload.reels;
  if (!Array.isArray(reels)) return [];
  return reels.flatMap((entry) => {
    if (!entry || typeof entry !== "object") return [];
    const row = entry as Record<string, unknown>;
    const slug = typeof row.product_slug === "string" ? row.product_slug.trim() : "";
    const videoRaw =
      typeof row.video === "string"
        ? row.video
        : typeof row.video_url === "string"
          ? row.video_url
          : "";
    const video = resolveAssetUrl(videoRaw) || sanitizePublicUrl(videoRaw) || "";
    if (!slug || !video) return [];
    return [{ video, slug }];
  });
}

export function matchReelsToProducts(
  reels: WatchReel[],
  products: Product[],
): { video: string; slug: string; product: Product }[] {
  return reels.flatMap((r) => {
    const product = products.find((p) => p.slug === r.slug);
    return product ? [{ ...r, product }] : [];
  });
}

/** CMS/static reels paired with live catalog; falls back by index when slugs differ. */
export function resolveWatchShopReels(
  reels: WatchReel[],
  products: Product[],
  max = 5,
): { video: string; slug: string; product: Product }[] {
  if (reels.length === 0 || products.length === 0) return [];
  const matched = matchReelsToProducts(reels, products);
  if (matched.length > 0) return matched.slice(0, max);
  return reels.slice(0, max).map((r, i) => ({
    ...r,
    product: products[i % products.length]!,
  }));
}
