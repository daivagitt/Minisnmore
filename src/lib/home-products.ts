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

export function sectionProductSlugs(payload: Record<string, unknown> | undefined): string[] {
  const slugs = payload?.product_slugs;
  if (!Array.isArray(slugs)) return [];
  return normalizeHomeSectionProductSlugs(
    slugs.filter((s): s is string => typeof s === "string" && s.trim().length > 0),
  );
}

export function normalizeHomeSectionProductSlugs(slugs: string[]): string[] {
  const seen = new Set<string>();
  const out: string[] = [];
  for (const raw of slugs) {
    const slug = raw.trim();
    if (!slug || seen.has(slug)) continue;
    seen.add(slug);
    out.push(slug);
    if (out.length >= HOME_SECTION_PRODUCT_LIMIT) break;
  }
  return out;
}

/** Drop slugs that no longer exist in the catalog so stale picks do not block new selections. */
export function pruneHomeSectionProductSlugs(slugs: string[], knownSlugs: Iterable<string>): string[] {
  const known = new Set(knownSlugs);
  return normalizeHomeSectionProductSlugs(slugs.filter((slug) => known.has(slug.trim())));
}

export function getHomeSectionProductSlugErrors(slugs: string[]): string[] {
  const unique = new Set(
    slugs.map((s) => s.trim()).filter(Boolean),
  );
  if (unique.size > HOME_SECTION_PRODUCT_LIMIT) {
    return [`Pick at most ${HOME_SECTION_PRODUCT_LIMIT} products — the homepage shows five per section.`];
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
    return normalizeHomeSectionProductSlugs(slugs)
      .map((slug) => bySlug.get(slug))
      .filter((p): p is Product => !!p)
      .slice(0, limit);
  }
  return sortBySortOrder(fallback(all)).slice(0, limit);
}
