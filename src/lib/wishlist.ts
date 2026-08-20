/** Drop blanks and duplicates from persisted wishlist ids. */
export function normalizeWishlistIds(ids: string[]): string[] {
  return [...new Set(ids.map(String).filter(Boolean))];
}

/** User-facing copy when a wishlist add is attempted for an item that is already saved. */
export function wishlistAlreadyExistsMessage(productName?: string): string {
  return productName
    ? `"${productName}" is already in your wishlist.`
    : "This item is already in your wishlist.";
}

/** Count wishlist entries that still match a live product id. */
export function wishlistCountForProducts(wishlist: string[], productIds: Iterable<string>): number {
  const valid = new Set(productIds);
  return normalizeWishlistIds(wishlist).filter((id) => valid.has(id)).length;
}
