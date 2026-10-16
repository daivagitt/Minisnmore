export type ReturnVariantFields = {
  size?: string | null;
  color?: string | null;
  sku?: string | null;
  variant_sku?: string | null;
  original_size?: string | null;
  original_color?: string | null;
  replacement_size?: string | null;
  replacement_color?: string | null;
  product_name?: string | null;
};

export function formatReturnVariant(line: ReturnVariantFields): string {
  return [line.size ?? line.original_size, line.color ?? line.original_color, line.sku || line.variant_sku]
    .filter(Boolean)
    .join(" · ");
}

export function formatReturnProductSubtitle(item: ReturnVariantFields): string {
  const original = formatReturnVariant(item);
  const replacement = [item.replacement_size, item.replacement_color].filter(Boolean).join(" · ");
  const product = item.product_name ?? "";
  if (product && original && replacement) return `${product} (${original}) → ${replacement}`;
  if (product && original) return `${product} (${original})`;
  if (product && replacement) return `${product} → ${replacement}`;
  return product;
}
