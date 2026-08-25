import {
  findVariant,
  isSimpleProductSellable,
  isVariantSellable,
  isVariantsProduct,
} from "@/lib/product-variants";
import { VariantColorButtons, VariantSizeButtons, variantColorsForDisplay } from "@/components/VariantPickers";
import type { Product } from "@/data/products";

/** Lets a customer pick a replacement size/colour for an exchange, reusing the storefront's variant pickers. */
export function ExchangeReplacementPicker({
  product,
  originalSize,
  originalColor,
  size,
  color,
  onSizeChange,
  onColorChange,
}: {
  product: Product | undefined;
  originalSize?: string | null;
  originalColor?: string | null;
  size: string | null;
  color: string | null;
  onSizeChange: (size: string) => void;
  onColorChange: (color: string) => void;
}) {
  if (!product) {
    return <p className="text-xs text-muted-foreground">Loading product options…</p>;
  }
  if (!isVariantsProduct(product)) {
    return (
      <p className="text-xs text-muted-foreground">
        This item doesn't have other sizes or colours available to exchange into.
      </p>
    );
  }

  const availableColors = variantColorsForDisplay(product, size);
  const selectedVariant = findVariant(product, { size, color });
  const sellable = isVariantsProduct(product) ? isVariantSellable(selectedVariant) : isSimpleProductSellable(product);

  return (
    <div className="space-y-3">
      {(originalSize || originalColor) && (
        <p className="text-[11px] text-muted-foreground">
          Currently: {[originalSize, originalColor].filter(Boolean).join(" · ")}
        </p>
      )}
      <div>
        <div className="text-xs font-medium text-muted-foreground mb-1.5">New size</div>
        <VariantSizeButtons product={product} size={size} onSizeChange={onSizeChange} />
      </div>
      <div>
        <div className="text-xs font-medium text-muted-foreground mb-1.5">New colour</div>
        <VariantColorButtons
          product={product}
          size={size}
          color={color}
          onColorChange={onColorChange}
          colors={availableColors}
        />
      </div>
      {size && color && !sellable ? (
        <p className="text-xs text-amber-700">This size and colour is currently unavailable for exchange.</p>
      ) : null}
    </div>
  );
}
