import type { Product } from "@/data/products";

/** Normalize a query for case-insensitive substring matching. */
export function normalizeSearchQuery(query: string): string {
  return query.trim().toLowerCase();
}

/** Match storefront products against a free-text query. */
export function productMatchesQuery(product: Product, rawQuery: string): boolean {
  const q = normalizeSearchQuery(rawQuery);
  if (!q) return true;

  const haystack = [
    product.name,
    product.slug,
    product.tagline,
    product.description,
    product.fabric,
    product.category,
    product.categoryName,
    product.categorySlug,
    product.sku,
  ]
    .filter(Boolean)
    .join(" ")
    .toLowerCase();

  return haystack.includes(q);
}

export function filterProductsByQuery(products: Product[], rawQuery: string): Product[] {
  const q = normalizeSearchQuery(rawQuery);
  if (!q) return products;
  return products.filter((p) => productMatchesQuery(p, q));
}
