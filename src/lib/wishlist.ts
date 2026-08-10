/** Drop blanks and duplicates from persisted wishlist ids. */
export function normalizeWishlistIds(ids: string[]): string[] {
  return [...new Set(ids.map(String).filter(Boolean))];
}

/** Count wishlist entries that still match a live product id. */
export function wishlistCountForProducts(wishlist: string[], productIds: Iterable<string>): number {
  const valid = new Set(productIds);
  return normalizeWishlistIds(wishlist).filter((id) => valid.has(id)).length;
}
