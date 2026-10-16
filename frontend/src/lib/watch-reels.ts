import type { Product } from "@/data/products";
import { HOME_SECTION_CARD_LIMIT } from "@/lib/home-section-limit";
import { resolveAssetUrl } from "@/lib/asset-url";
import { requiredPublicUrlFieldError, sanitizePublicUrl } from "@/lib/utils";

export const HOME_WATCH_SHOP_LIMIT = HOME_SECTION_CARD_LIMIT;

export type WatchReel = { video: string; slug: string; sku: string };

export type WatchReelDraft = {
  video: string;
  sku: string;
  product_slug: string;
  sort_order: number;
};

export function emptyWatchReel(sort_order = 0): WatchReelDraft {
  return { video: "", sku: "", product_slug: "", sort_order };
}

function reelVideoRaw(row: Record<string, unknown>): string {
  if (typeof row.video === "string") return row.video;
  if (typeof row.video_url === "string") return row.video_url;
  return "";
}

function reelSkuRaw(row: Record<string, unknown>): string {
  if (typeof row.sku === "string") return row.sku;
  if (typeof row.product_sku === "string") return row.product_sku;
  return "";
}

function reelSlugRaw(row: Record<string, unknown>): string {
  if (typeof row.product_slug === "string") return row.product_slug;
  if (typeof row.slug === "string") return row.slug;
  return "";
}

function draftFromRaw(raw: unknown, fallbackOrder: number): WatchReelDraft {
  if (!raw || typeof raw !== "object") return emptyWatchReel(fallbackOrder);
  const row = raw as Record<string, unknown>;
  const sortOrder = Number(row.sort_order);
  return {
    video: reelVideoRaw(row).trim(),
    sku: reelSkuRaw(row).trim(),
    product_slug: reelSlugRaw(row).trim().toLowerCase(),
    sort_order: Number.isFinite(sortOrder) ? Math.max(0, Math.floor(sortOrder)) : fallbackOrder,
  };
}

export function isBlankWatchReel(draft: WatchReelDraft): boolean {
  return !draft.video.trim() && !draft.sku.trim() && !draft.product_slug.trim();
}

function sortByReelOrder<T extends { sort_order: number }>(rows: T[]): T[] {
  return [...rows].sort((a, b) => a.sort_order - b.sort_order || 0);
}

export function watchReelDraftsFromPayload(
  payload: Record<string, unknown> | undefined,
): WatchReelDraft[] {
  const reels = payload?.reels;
  if (!Array.isArray(reels)) return [];
  return sortByReelOrder(reels.map((row, index) => draftFromRaw(row, index)));
}

export function getWatchReelValidationErrors(draft: WatchReelDraft, index?: number): string[] {
  const prefix = index != null ? `Reel ${index + 1}: ` : "";
  const errors: string[] = [];
  const videoError = requiredPublicUrlFieldError(draft.video, "Video link");
  if (videoError) errors.push(`${prefix}${videoError}`);
  if (!draft.sku.trim() && !draft.product_slug.trim()) {
    errors.push(`${prefix}Pick a product SKU.`);
  }
  return errors;
}

export function getWatchReelCountErrors(drafts: WatchReelDraft[]): string[] {
  const filled = drafts.filter((d) => !isBlankWatchReel(d)).length;
  if (filled > HOME_WATCH_SHOP_LIMIT) {
    return [`Add at most ${HOME_WATCH_SHOP_LIMIT} reels — the homepage shows five per section.`];
  }
  return [];
}

export function validateWatchReelDrafts(drafts: WatchReelDraft[]): string[] {
  const errors = [...getWatchReelCountErrors(drafts)];
  drafts.forEach((draft, index) => {
    if (isBlankWatchReel(draft)) return;
    errors.push(...getWatchReelValidationErrors(draft, index));
  });
  return errors;
}

export function normalizeWatchReelDrafts(drafts: WatchReelDraft[]): WatchReelDraft[] {
  const seen = new Set<string>();
  const out: WatchReelDraft[] = [];
  for (const draft of sortByReelOrder(drafts)) {
    if (isBlankWatchReel(draft)) continue;
    const sku = draft.sku.trim();
    const product_slug = draft.product_slug.trim().toLowerCase();
    const video = draft.video.trim();
    const key = (sku || product_slug).toLowerCase();
    if (!key || seen.has(key)) continue;
    seen.add(key);
    out.push({
      video,
      sku,
      product_slug,
      sort_order: Number.isFinite(draft.sort_order) ? Math.max(0, Math.floor(draft.sort_order)) : out.length,
    });
    if (out.length >= HOME_WATCH_SHOP_LIMIT) break;
  }
  return out;
}

export function watchReelsToPayload(drafts: WatchReelDraft[]): Record<string, unknown>[] {
  return normalizeWatchReelDrafts(drafts).map((reel) => ({
    video: reel.video,
    sku: reel.sku,
    product_slug: reel.product_slug,
    sort_order: reel.sort_order,
  }));
}

function resolvedVideo(raw: string): string {
  const allowed = resolveAssetUrl(raw);
  if (!allowed) return "";
  return sanitizePublicUrl(allowed) || (allowed.startsWith("/") ? allowed : "");
}

/** Parse Admin → Home → Watch & Shop payload: { "reels": [{ "video": "…", "sku": "…", "product_slug": "…" }] } */
export function parseWatchReels(payload: Record<string, unknown>): WatchReel[] {
  return watchReelDraftsFromPayload(payload).flatMap((draft) => {
    const video = resolvedVideo(draft.video);
    if (!video || (!draft.sku && !draft.product_slug)) return [];
    return [{ video, slug: draft.product_slug, sku: draft.sku }];
  });
}

function skuKey(value: string | null | undefined): string {
  return value?.trim().toLowerCase() ?? "";
}

function productHasSku(product: Product, sku: string): boolean {
  const want = skuKey(sku);
  if (!want) return false;
  if (skuKey(product.sku) === want) return true;
  return product.variants?.some((variant) => skuKey(variant.sku) === want) ?? false;
}

export function matchReelsToProducts(
  reels: WatchReel[],
  products: Product[],
): { video: string; slug: string; sku: string; product: Product }[] {
  return reels.flatMap((r) => {
    const bySku = r.sku ? products.find((p) => productHasSku(p, r.sku)) : undefined;
    const product = bySku ?? products.find((p) => r.slug && p.slug === r.slug);
    return product ? [{ ...r, slug: product.slug, sku: r.sku || product.sku || "", product }] : [];
  });
}

/** CMS reels paired with live catalog by SKU, then product slug. */
export function resolveWatchShopReels(
  reels: WatchReel[],
  products: Product[],
  max = HOME_WATCH_SHOP_LIMIT,
): { video: string; slug: string; sku: string; product: Product }[] {
  if (reels.length === 0 || products.length === 0) return [];
  return matchReelsToProducts(reels, products).slice(0, max);
}
