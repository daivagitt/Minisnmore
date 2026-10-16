export const STOREFRONT_THUMB_SUFFIX = ".thumb.jpg";
export const STOREFRONT_DISPLAY_SUFFIX = ".display.jpg";
export const STOREFRONT_IMAGE_THUMB_WIDTH = 240;
export const STOREFRONT_IMAGE_DISPLAY_WIDTH = 800;
export const STOREFRONT_IMAGE_ORIGINAL_WIDTH = 1250;
export const STOREFRONT_HERO_SIZES = "(min-width: 1024px) min(1250px, 50vw), 92vw";
export const STOREFRONT_CARD_SIZES = "(min-width: 1024px) 280px, 45vw";
/** Side-rail thumbs max out at 4.75rem; swatches at 3.5rem. */
export const STOREFRONT_THUMB_SIZES = "76px";
export const STOREFRONT_SWATCH_SIZES = "(min-width: 640px) 56px, 48px";

export type StorefrontImageSize = "thumb" | "display" | "original";

const DERIVATIVE_SUFFIXES = [STOREFRONT_THUMB_SUFFIX, STOREFRONT_DISPLAY_SUFFIX] as const;
const prefetchedHrefs = new Set<string>();

export function storefrontImageUrl(
  url: string | null | undefined,
  size: StorefrontImageSize = "original",
): string {
  const trimmed = (url ?? "").trim();
  if (!trimmed) return "";
  if (!isRewritableCatalogUrl(trimmed)) return trimmed;

  const parts = splitUrl(trimmed);
  const originalPath = stripDerivativeSuffix(parts.path);
  if (size === "original") return originalPath + parts.query + parts.hash;
  const suffix = size === "thumb" ? STOREFRONT_THUMB_SUFFIX : STOREFRONT_DISPLAY_SUFFIX;
  return originalPath + suffix + parts.query + parts.hash;
}

export function storefrontImageSrcSet(url: string | null | undefined): string {
  const original = storefrontImageUrl(url, "original");
  if (!isRewritableCatalogUrl(original)) return "";
  const display = storefrontImageUrl(original, "display");
  return `${display} ${STOREFRONT_IMAGE_DISPLAY_WIDTH}w, ${original} ${STOREFRONT_IMAGE_ORIGINAL_WIDTH}w`;
}

/** Warm the browser cache for a catalog derivative. Safe to call repeatedly. */
export function prefetchStorefrontImage(
  url: string | null | undefined,
  size: StorefrontImageSize = "display",
): void {
  const href = storefrontImageUrl(url, size);
  if (!href || typeof Image === "undefined") return;
  if (prefetchedHrefs.has(href)) return;
  prefetchedHrefs.add(href);
  const img = new Image();
  img.decoding = "async";
  img.src = href;
}

export function prefetchStorefrontImages(
  urls: Array<string | null | undefined>,
  size: StorefrontImageSize = "display",
  options?: { idle?: boolean },
): () => void {
  if (typeof window === "undefined") return () => {};
  const unique = uniqueUrls(urls);
  if (!unique.length) return () => {};

  const run = () => {
    for (const url of unique) prefetchStorefrontImage(url, size);
  };

  if (!options?.idle) {
    run();
    return () => {};
  }

  if (typeof window.requestIdleCallback === "function") {
    const id = window.requestIdleCallback(run, { timeout: 1800 });
    return () => window.cancelIdleCallback(id);
  }
  const timer = window.setTimeout(run, 200);
  return () => window.clearTimeout(timer);
}

export function resetStorefrontImagePrefetch(): void {
  prefetchedHrefs.clear();
}

function uniqueUrls(urls: Array<string | null | undefined>): string[] {
  const seen = new Set<string>();
  const out: string[] = [];
  for (const url of urls) {
    const trimmed = url?.trim();
    if (!trimmed || seen.has(trimmed)) continue;
    seen.add(trimmed);
    out.push(trimmed);
  }
  return out;
}

function isRewritableCatalogUrl(url: string): boolean {
  if (url.startsWith("data:") || url.startsWith("blob:")) return false;
  if (url.startsWith("/") && !url.startsWith("//")) return false;
  return /^https:\/\//i.test(url);
}

function stripDerivativeSuffix(path: string): string {
  for (const suffix of DERIVATIVE_SUFFIXES) {
    if (path.endsWith(suffix)) return path.slice(0, -suffix.length);
  }
  return path;
}

function splitUrl(url: string): { path: string; query: string; hash: string } {
  const hashIndex = url.indexOf("#");
  const hash = hashIndex >= 0 ? url.slice(hashIndex) : "";
  const withoutHash = hashIndex >= 0 ? url.slice(0, hashIndex) : url;
  const queryIndex = withoutHash.indexOf("?");
  const query = queryIndex >= 0 ? withoutHash.slice(queryIndex) : "";
  const path = queryIndex >= 0 ? withoutHash.slice(0, queryIndex) : withoutHash;
  return { path, query, hash };
}
