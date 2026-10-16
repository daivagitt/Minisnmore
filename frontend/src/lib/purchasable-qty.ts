import { FIT_LIMITS } from "@/lib/product-fit";
import { findVariant, isVariantsProduct, type VariantProductLike } from "@/lib/product-variants";

/** Max units a shopper may buy for this product/selection (stock + FIT qty ceiling). */
export function maxPurchasableQty(
  product: VariantProductLike,
  opts: { size?: string | null; color?: string | null; sku?: string | null } = {},
): number {
  const variant = findVariant(product, opts);
  if (isVariantsProduct(product)) {
    if (!variant) return 0;
    return Math.min(FIT_LIMITS.qtyMax, Math.max(0, variant.stock ?? 0));
  }
  if (product.stock != null) {
    return Math.min(FIT_LIMITS.qtyMax, Math.max(0, product.stock ?? 0));
  }
  return FIT_LIMITS.qtyMax;
}
