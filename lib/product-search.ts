import type { Product } from "@/data/products";
import type { ProductRow } from "@/lib/backend-data";
import { parseVariantsJson } from "@/lib/product-variants";

const MAX_QUERY_LENGTH = 100;

/** Normalize a query for case-insensitive matching (trim + lower + length cap). */
export function normalizeSearchQuery(query: string): string {
  return query.trim().toLowerCase().slice(0, MAX_QUERY_LENGTH);
}

/** Split into non-empty tokens; every token must match (AND). */
export function searchTokens(rawQuery: string): string[] {
  const q = normalizeSearchQuery(rawQuery);
  if (!q) return [];
  return q.split(/\s+/).filter(Boolean);
}

function pushField(parts: string[], value: unknown) {
  if (value == null) return;
  const text = String(value).trim();
  if (text) parts.push(text);
}

/** Build a single lowercase haystack for a storefront product. */
export function productSearchHaystack(product: Product): string {
  const parts: string[] = [];
  pushField(parts, product.name);
  pushField(parts, product.slug);
  pushField(parts, product.tagline);
  pushField(parts, product.description);
  pushField(parts, product.fabric);
  pushField(parts, product.category);
  pushField(parts, product.categoryName);
  pushField(parts, product.categorySlug);
  pushField(parts, product.sku);
  pushField(parts, product.detailsKicker);
  for (const age of product.ages ?? []) pushField(parts, age);
  for (const size of product.sizes ?? []) pushField(parts, size);
  for (const color of product.colors ?? []) pushField(parts, color?.name);
  for (const variant of product.variants ?? []) {
    pushField(parts, variant.sku);
    pushField(parts, variant.size);
    pushField(parts, variant.color);
  }
  return parts.join(" ").toLowerCase();
}

/** Build a haystack for an admin product row (includes variant SKUs from JSON). */
export function adminProductSearchHaystack(row: ProductRow): string {
  const parts: string[] = [];
  pushField(parts, row.name);
  pushField(parts, row.slug);
  pushField(parts, row.sku);
  pushField(parts, row.description);
  pushField(parts, row.story);
  pushField(parts, row.fabric_type);
  pushField(parts, row.category_name);
  pushField(parts, row.category_slug);
  pushField(parts, row.badge);
  pushField(parts, row.details_kicker);
  pushField(parts, row.hsn);
  pushField(parts, row.ledgerly_item_id);
  for (const size of row.sizes ?? []) pushField(parts, size);
  for (const color of row.colors ?? []) pushField(parts, color?.name);
  for (const variant of parseVariantsJson(row.variants_json)) {
    pushField(parts, variant.sku);
    pushField(parts, variant.size);
    pushField(parts, variant.color);
  }
  return parts.join(" ").toLowerCase();
}

function haystackMatchesTokens(haystack: string, tokens: string[]): boolean {
  if (!tokens.length) return true;
  return tokens.every((token) => haystack.includes(token));
}

/** Match storefront products against a free-text query (all tokens required). */
export function productMatchesQuery(product: Product, rawQuery: string): boolean {
  const tokens = searchTokens(rawQuery);
  if (!tokens.length) return true;
  return haystackMatchesTokens(productSearchHaystack(product), tokens);
}

/** Match admin product rows against a free-text query. */
export function adminProductMatchesQuery(row: ProductRow, rawQuery: string): boolean {
  const tokens = searchTokens(rawQuery);
  if (!tokens.length) return true;
  return haystackMatchesTokens(adminProductSearchHaystack(row), tokens);
}

/**
 * Rank for typeahead: exact name/sku boosts, then prefix, then substring.
 * Higher is better.
 */
export function rankProductMatch(product: Product, rawQuery: string): number {
  const q = normalizeSearchQuery(rawQuery);
  if (!q) return 0;
  if (!productMatchesQuery(product, q)) return -1;

  const name = (product.name ?? "").toLowerCase();
  const slug = (product.slug ?? "").toLowerCase();
  const sku = (product.sku ?? "").toLowerCase();
  let score = 10;

  if (name === q || sku === q || slug === q) score += 100;
  else if (name.startsWith(q) || sku.startsWith(q) || slug.startsWith(q)) score += 60;
  else if (name.includes(q) || sku.includes(q)) score += 30;

  for (const variant of product.variants ?? []) {
    const vSku = variant.sku.toLowerCase();
    if (vSku === q) score += 90;
    else if (vSku.startsWith(q)) score += 50;
    else if (vSku.includes(q)) score += 20;
  }

  if (product.isBestseller) score += 3;
  if (product.isTrending) score += 2;
  if (product.isNew) score += 1;
  return score;
}

export function filterProductsByQuery(
  products: Product[],
  rawQuery: string,
  opts?: { limit?: number; ranked?: boolean },
): Product[] {
  const tokens = searchTokens(rawQuery);
  if (!tokens.length) {
    return opts?.limit != null ? products.slice(0, opts.limit) : products;
  }

  let matched = products.filter((p) => haystackMatchesTokens(productSearchHaystack(p), tokens));
  if (opts?.ranked !== false) {
    matched = [...matched].sort((a, b) => {
      const diff = rankProductMatch(b, rawQuery) - rankProductMatch(a, rawQuery);
      if (diff !== 0) return diff;
      return a.name.localeCompare(b.name);
    });
  }
  if (opts?.limit != null) return matched.slice(0, opts.limit);
  return matched;
}

export function filterAdminProductsByQuery(rows: ProductRow[], rawQuery: string): ProductRow[] {
  const tokens = searchTokens(rawQuery);
  if (!tokens.length) return rows;
  return rows.filter((r) => haystackMatchesTokens(adminProductSearchHaystack(r), tokens));
}
