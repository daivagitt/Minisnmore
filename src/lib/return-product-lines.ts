export type ReturnProductLine = {
  product_name?: string;
  product_image?: string | null;
};

/** Normalize API return rows to a list of catalog lines for thumbnails. */
export function resolveReturnProductLines(input: {
  items?: ReturnProductLine[] | null;
  product_name?: string | null;
  product_image?: string | null;
}): ReturnProductLine[] {
  if (input.items && input.items.length > 0) {
    return input.items;
  }
  if (input.product_name || input.product_image) {
    return [{ product_name: input.product_name ?? undefined, product_image: input.product_image }];
  }
  return [];
}
