import { Field } from "@/components/admin/AdminPage";
import { VariantSizeButtons } from "@/components/VariantPickers";
import { parseVariantsJson, sizesForDisplay, type VariantProductLike } from "@/lib/product-variants";
import type { ProductRow } from "@/lib/backend-data";

function toVariantProductLike(product: ProductRow): VariantProductLike {
  return {
    itemStructure: product.item_structure ?? "SIMPLE",
    sizes: product.sizes ?? [],
    colors: (product.colors ?? []).map((c) => ({
      name: c.name,
      hex: c.hex || "#d4d4d4",
      imageUrls: c.imageUrls,
    })),
    price: Number(product.price) || 0,
    compareAt: product.compare_at_price ?? undefined,
    sku: product.sku ?? undefined,
    stock: product.stock,
    variants: parseVariantsJson(product.variants_json),
  };
}

/** Mirrors storefront size pills — all catalog sizes, sold-out struck through. */
export function AdminSizeAvailabilityPreview({ product }: { product: ProductRow }) {
  const variantLike = toVariantProductLike(product);
  const labels = sizesForDisplay(variantLike);
  if (!labels.length) return null;

  return (
    <Field label="Size availability (storefront preview)">
      <p className="text-xs text-muted-foreground mb-2 leading-snug">
        All sizes from the list above are shown on the shop. Sizes with no in-stock variants appear struck through.
      </p>
      <VariantSizeButtons product={variantLike} readOnly />
    </Field>
  );
}
