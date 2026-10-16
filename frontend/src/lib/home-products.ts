import type { Product } from "@/data/products";
import { HOME_SECTION_CARD_LIMIT } from "@/lib/home-section-limit";
import { sortBySortOrder } from "@/lib/product-sort";

/** Homepage product grids show five items; Explore links to the full shop. */
export const HOME_SECTION_PRODUCT_LIMIT = HOME_SECTION_CARD_LIMIT;

export const HOME_SECTION_PRODUCT_KEYS = [
  "best_sellers",
  "trending",
  "new_arrivals",
  "featured",
  "gifts",
] as const;

/** Shop mega-menu “Loved Right Now” merchandising slot. */
export const LOVED_RIGHT_NOW_KEY = "loved_right_now";
export const LOVED_RIGHT_NOW_LIMIT = 3;

/** Product-page “Pieces that pair beautifully” merchandising slot. */
export const YOU_MAY_ALSO_LOVE_KEY = "you_may_also_love";
export const YOU_MAY_ALSO_LOVE_LIMIT = 4;

export const MERCHANDISE_PRODUCT_KEYS = [
  ...HOME_SECTION_PRODUCT_KEYS,
  LOVED_RIGHT_NOW_KEY,
  YOU_MAY_ALSO_LOVE_KEY,
] as const;

export function productLimitForSection(key: string): number {
  if (key === LOVED_RIGHT_NOW_KEY) return LOVED_RIGHT_NOW_LIMIT;
  if (key === YOU_MAY_ALSO_LOVE_KEY) return YOU_MAY_ALSO_LOVE_LIMIT;
  return HOME_SECTION_PRODUCT_LIMIT;
}

export function isMerchandiseProductKey(key: string): boolean {
  return (MERCHANDISE_PRODUCT_KEYS as readonly string[]).includes(key);
}

export function sectionProductSlugs(
  payload: Record<string, unknown> | undefined,
  limit = HOME_SECTION_PRODUCT_LIMIT,
): string[] {
  const slugs = payload?.product_slugs;
  if (!Array.isArray(slugs)) return [];
  return normalizeHomeSectionProductSlugs(
    slugs.filter((s): s is string => typeof s === "string" && s.trim().length > 0),
    limit,
  );
}

export function normalizeHomeSectionProductSlugs(
  slugs: string[],
  limit = HOME_SECTION_PRODUCT_LIMIT,
): string[] {
  const seen = new Set<string>();
  const out: string[] = [];
  for (const raw of slugs) {
    const slug = raw.trim();
    if (!slug || seen.has(slug)) continue;
    seen.add(slug);
    out.push(slug);
    if (out.length >= limit) break;
  }
  return out;
}

/** Drop slugs that no longer exist in the catalog so stale picks do not block new selections. */
export function pruneHomeSectionProductSlugs(
  slugs: string[],
  knownSlugs: Iterable<string>,
  limit = HOME_SECTION_PRODUCT_LIMIT,
): string[] {
  const known = new Set(knownSlugs);
  return normalizeHomeSectionProductSlugs(
    slugs.filter((slug) => known.has(slug.trim())),
    limit,
  );
}

export function getHomeSectionProductSlugErrors(
  slugs: string[],
  limit = HOME_SECTION_PRODUCT_LIMIT,
): string[] {
  const unique = new Set(slugs.map((s) => s.trim()).filter(Boolean));
  if (unique.size > limit) {
    return [`Pick at most ${limit} products for this section.`];
  }
  return [];
}

/** Admin-picked slugs first; otherwise auto-select by the section's product flags. */
export function resolveHomeSectionProducts(
  all: Product[],
  slugs: string[],
  fallback: (products: Product[]) => Product[],
  limit = HOME_SECTION_PRODUCT_LIMIT,
): Product[] {
  if (slugs.length > 0) {
    const bySlug = new Map(all.map((p) => [p.slug, p]));
    return normalizeHomeSectionProductSlugs(slugs, limit)
      .map((slug) => bySlug.get(slug))
      .filter((p): p is Product => !!p)
      .slice(0, limit);
  }
  return sortBySortOrder(fallback(all)).slice(0, limit);
}

export function autoLovedRightNow(products: Product[]): Product[] {
  const flagged = products.filter((p) => p.isBestseller || p.isTrending || p.isNew);
  return flagged.length > 0 ? flagged : products;
}

export function resolveLovedRightNow(all: Product[], slugs: string[]): Product[] {
  return resolveHomeSectionProducts(all, slugs, autoLovedRightNow, LOVED_RIGHT_NOW_LIMIT);
}

const PRODUCT_LABEL_MAX = 50;

/** Optional uppercase line above each Loved Right Now product (replaces “Newborn”). */
export function merchandisingProductLabel(
  payload: Record<string, unknown> | undefined,
  product: Pick<Product, "category">,
): string {
  const override = typeof payload?.product_label === "string" ? payload.product_label.trim() : "";
  if (override) return override.slice(0, PRODUCT_LABEL_MAX);
  return (product.category || "").trim();
}

export function normalizeMerchandisingProductLabel(raw: unknown): string {
  if (typeof raw !== "string") return "";
  return raw.trim().slice(0, PRODUCT_LABEL_MAX);
}

export function autoYouMayAlsoLove(products: Product[], current: Product): Product[] {
  return products
    .filter((p) => p.id !== current.id && (p.category === current.category || p.fabric === current.fabric))
    .concat(products.filter((p) => p.id !== current.id))
    .filter((p, i, arr) => arr.findIndex((x) => x.id === p.id) === i);
}

/** Admin picks first (skipping the product being viewed); remaining slots fill from similar pieces. */
export function resolveYouMayAlsoLove(all: Product[], current: Product, slugs: string[]): Product[] {
  const limit = YOU_MAY_ALSO_LOVE_LIMIT;
  const isSelf = (p: Product) => p.id === current.id || p.slug === current.slug;
  if (slugs.length > 0) {
    const bySlug = new Map(all.map((p) => [p.slug, p]));
    const picked = normalizeHomeSectionProductSlugs(slugs, limit)
      .map((slug) => bySlug.get(slug))
      .filter((p): p is Product => !!p && !isSelf(p));
    if (picked.length >= limit) return picked.slice(0, limit);
    const seen = new Set(picked.map((p) => p.id));
    const fill = autoYouMayAlsoLove(all, current).filter((p) => !seen.has(p.id));
    return [...picked, ...fill].slice(0, limit);
  }
  return autoYouMayAlsoLove(all, current).slice(0, limit);
}
