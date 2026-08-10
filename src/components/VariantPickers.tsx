import {
  colorsForSize,
  findVariant,
  isVariantSellable,
  isVariantsProduct,
  sizeHasSellableVariants,
  sizesForDisplay,
  type VariantProductLike,
} from "@/lib/product-variants";
import { swatchImageForColor } from "@/lib/product-colors";
import { cn } from "@/lib/utils";

/** Out-of-stock sizes: strikethrough + diagonal cross (not blurred). */
const UNAVAILABLE_SIZE_CLASS =
  "opacity-60 cursor-not-allowed pointer-events-none relative line-through decoration-foreground/55 decoration-2";

/** Out-of-stock colour swatches: diagonal cross only (not blurred). */
const UNAVAILABLE_COLOR_CLASS = "opacity-60 cursor-not-allowed pointer-events-none relative";

function OutOfStockCross() {
  return (
    <span className="pointer-events-none absolute inset-0 z-10 overflow-hidden rounded-[inherit]" aria-hidden>
      <span className="absolute left-1/2 top-[-12%] h-[124%] w-[1.5px] -translate-x-1/2 rotate-45 bg-foreground/70" />
      <span className="absolute left-1/2 top-[-12%] h-[124%] w-[1.5px] -translate-x-1/2 -rotate-45 bg-foreground/70" />
    </span>
  );
}

export function VariantSizeButtons({
  product,
  size,
  onSizeChange,
  gapClass = "gap-1.5",
}: {
  product: VariantProductLike;
  size: string | null;
  onSizeChange: (size: string) => void;
  gapClass?: string;
}) {
  const sizes = sizesForDisplay(product);
  const isVariants = isVariantsProduct(product);

  return (
    <div className={cn("flex flex-wrap", gapClass)}>
      {sizes.map((s) => {
        const unavailable = isVariants && !sizeHasSellableVariants(product, s);
        const selected = !unavailable && size === s;
        return (
          <button
            key={s}
            type="button"
            disabled={unavailable}
            onClick={() => onSizeChange(s)}
            className={cn(
              "rounded-full px-3 py-1.5 text-xs transition-all duration-300 ease-out",
              selected ? "bg-primary text-primary-foreground" : "glass hover:bg-white/60",
              unavailable && UNAVAILABLE_SIZE_CLASS,
            )}
            title={unavailable ? `${s} — sold out` : s}
          >
            {s}
            {unavailable ? <OutOfStockCross /> : null}
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
              <img src={swatchImage} alt={c.name} className="h-full w-full object-cover" loading="lazy" />
            ) : null}
            {unavailable ? <OutOfStockCross /> : null}
          </button>
        );
      })}
    </div>
  );
}

/** All active colours for the size (including out-of-stock for crossed-out display). */
export function variantColorsForDisplay(product: VariantProductLike, size: string | null): VariantProductLike["colors"] {
  if (!isVariantsProduct(product)) return product.colors;
  const names = colorsForSize(product, size);
  return product.colors.filter((c) => names.some((n) => n.trim().toLowerCase() === c.name.trim().toLowerCase()));
}
