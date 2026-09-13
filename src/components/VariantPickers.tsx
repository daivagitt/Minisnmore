import {
  colorsForSize,
  findVariant,
  isVariantSellable,
  isVariantsProduct,
  sizeHasSellableVariants,
  sizesForDisplay,
  type VariantProductLike,
} from "@/lib/product-variants";
import { LazyImage } from "@/components/LazyImage";
import { swatchImageForColor } from "@/lib/product-colors";
import { cn } from "@/lib/utils";

/** Out-of-stock sizes: strikethrough only (not blurred). */
const UNAVAILABLE_SIZE_CLASS =
  "opacity-60 cursor-not-allowed pointer-events-none line-through decoration-foreground/55 decoration-2";

/** Out-of-stock colour swatches: muted only (not blurred). */
const UNAVAILABLE_COLOR_CLASS = "opacity-60 cursor-not-allowed pointer-events-none";

const SIZE_PILL_CLASS = "rounded-full px-3 py-1.5 text-xs transition-all duration-300 ease-out";

export function VariantSizeButtons({
  product,
  size,
  onSizeChange,
  gapClass = "gap-1.5",
  readOnly = false,
}: {
  product: VariantProductLike;
  size?: string | null;
  onSizeChange?: (size: string) => void;
  gapClass?: string;
  /** Admin preview — show all sizes with sold-out strikethrough, no selection. */
  readOnly?: boolean;
}) {
  const sizes = sizesForDisplay(product);
  const isVariants = isVariantsProduct(product);

  return (
    <div className={cn("flex flex-wrap", gapClass)}>
      {sizes.map((s) => {
        const unavailable = isVariants && !sizeHasSellableVariants(product, s);
        const selected = !readOnly && size === s;
        const className = cn(
          SIZE_PILL_CLASS,
          selected ? "bg-primary text-primary-foreground" : "glass",
          !readOnly && !selected && "hover:bg-white/60",
          unavailable && UNAVAILABLE_SIZE_CLASS,
        );
        if (readOnly) {
          return (
            <span key={s} className={className} title={unavailable ? `${s} — sold out` : s}>
              {s}
            </span>
          );
        }
        return (
          <button
            key={s}
            type="button"
            disabled={unavailable}
            onClick={() => onSizeChange?.(s)}
            className={className}
            title={unavailable ? `${s} — sold out` : s}
          >
            {s}
          </button>
        );
      })}
    </div>
  );
}

export function VariantColorButtons({
  product,
  size,
  color,
  onColorChange,
  colors,
  gapClass = "gap-2.5",
  swatchClass = "h-12 w-12 sm:h-14 sm:w-14",
}: {
  product: VariantProductLike;
  size: string | null;
  color: string | null;
  onColorChange: (color: string) => void;
  colors: VariantProductLike["colors"];
  gapClass?: string;
  swatchClass?: string;
}) {
  const isVariants = isVariantsProduct(product);

  return (
    <div className={cn("flex flex-wrap", gapClass)}>
      {colors.map((c) => {
        const row = findVariant(product, { size, color: c.name });
        const unavailable = isVariants && !isVariantSellable(row);
        const selected = !unavailable && color?.trim().toLowerCase() === c.name.trim().toLowerCase();
        const swatchImage = swatchImageForColor(c);
        return (
          <button
            key={c.name}
            type="button"
            disabled={unavailable}
            onClick={() => onColorChange(c.name)}
            className={cn(
              "rounded-xl border-2 overflow-hidden transition-all duration-300 ease-out",
              swatchClass,
              selected ? "border-primary ring-2 ring-primary/30" : "border-white/70 hover:border-primary/50",
              unavailable && UNAVAILABLE_COLOR_CLASS,
            )}
            style={swatchImage ? undefined : { background: c.hex }}
            aria-label={c.name}
            aria-pressed={selected}
            title={unavailable ? `${c.name} — out of stock in this size` : c.name}
          >
            {swatchImage ? (
              <LazyImage src={swatchImage} alt={c.name} className="h-full w-full object-cover" priority="auto" />
            ) : null}
          </button>
        );
      })}
    </div>
  );
}

/** All active colours for the size (including out-of-stock for muted display). */
export function variantColorsForDisplay(product: VariantProductLike, size: string | null): VariantProductLike["colors"] {
  if (!isVariantsProduct(product)) return product.colors;
  const names = colorsForSize(product, size);
  return product.colors.filter((c) => names.some((n) => n.trim().toLowerCase() === c.name.trim().toLowerCase()));
}
