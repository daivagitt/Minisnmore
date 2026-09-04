import type { Product } from "@/data/products";

/** Lower values surface first in grids (matches admin “Sort order”). */
export function sortOrderValue(product: Product): number {
  const n = product.sortOrder;
  if (typeof n === "number" && Number.isFinite(n)) return n;
  return 999_999;
}

export function compareSortOrder(a: Product, b: Product): number {
  const byOrder = sortOrderValue(a) - sortOrderValue(b);
  if (byOrder !== 0) return byOrder;
  return a.name.localeCompare(b.name);
}

export function compareFlagFirst(
  a: Product,
  b: Product,
  isMatch: (product: Product) => boolean,
): number {
  const byFlag = Number(isMatch(b)) - Number(isMatch(a));
  if (byFlag !== 0) return byFlag;
  return compareSortOrder(a, b);
}

export function compareBestsellersFirst(a: Product, b: Product): number {
  return compareFlagFirst(a, b, (p) => !!p.isBestseller);
}

export function compareNewArrivalsFirst(a: Product, b: Product): number {
  return compareFlagFirst(a, b, (p) => !!p.isNew);
}

/** Within a collection, flagged products first, each group by sort order. */
export function sortProductsByFlag(
  products: Product[],
  isMatch: (product: Product) => boolean,
): Product[] {
  const flagged: Product[] = [];
  const rest: Product[] = [];
  for (const product of products) {
    (isMatch(product) ? flagged : rest).push(product);
  }
  return [...flagged.sort(compareSortOrder), ...rest.sort(compareSortOrder)];
}

export function sortBestsellersFirst(products: Product[]): Product[] {
  return [...products].sort(compareBestsellersFirst);
}

export function sortNewArrivalsFirst(products: Product[]): Product[] {
  return [...products].sort(compareNewArrivalsFirst);
}

export function sortTrendingFirst(products: Product[]): Product[] {
  return sortProductsByFlag(products, (p) => !!p.isTrending);
}

export function sortBySortOrder(products: Product[]): Product[] {
  return [...products].sort(compareSortOrder);
}
