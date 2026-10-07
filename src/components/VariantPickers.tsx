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
import { STOREFRONT_SWATCH_SIZES, storefrontImageUrl } from "@/lib/storefront-image";
import { cn } from "@/lib/utils";

const SWATCH_EAGER_COUNT = 8;

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
  sizeAnnotation,
}: {
  product: VariantProductLike;
  size?: string | null;
  onSizeChange?: (size: string) => void;
  gapClass?: string;
  /** Admin preview — show all sizes with sold-out strikethrough, no selection. */
  readOnly?: boolean;
  /** Optional second-line label, e.g. exchange price difference. */
  sizeAnnotation?: (size: string) => string | null;
}) {
  const sizes = sizesForDisplay(product);
  const isVariants = isVariantsProduct(product);

  return (
    <div className={cn("flex flex-wrap", gapClass)}>
      {sizes.map((s) => {
        const unavailable = isVariants && !sizeHasSellableVariants(product, s);
        const selected = !readOnly && size === s;
        const annotation = sizeAnnotation?.(s) ?? null;
        const className = cn(
          SIZE_PILL_CLASS,
          annotation && "inline-flex flex-col items-center leading-tight",
          selected ? "bg-primary text-primary-foreground" : "glass",
          !readOnly && !selected && "hover:bg-white/60",
          unavailable && UNAVAILABLE_SIZE_CLASS,
        );
        const title = unavailable ? `${s} — sold out` : annotation ? `${s} · ${annotation}` : s;
        if (readOnly) {
          return (
            <span key={s} className={className} title={title}>
              {s}
              {annotation ? <span className="text-[10px] font-medium tabular-nums opacity-80">{annotation}</span> : null}
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
            title={title}
          >
            {s}
            {annotation ? <span className="text-[10px] font-medium tabular-nums opacity-80">{annotation}</span> : null}
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
  onColorIntent,
  colors,
  gapClass = "gap-2.5",
  swatchClass = "h-12 w-12 sm:h-14 sm:w-14",
  colorAnnotation,
}: {
  product: VariantProductLike;
  size: string | null;
  color: string | null;
  onColorChange: (color: string) => void;
  /** Hover/focus — prefetch that print's gallery before click. */
  onColorIntent?: (color: string) => void;
  colors: VariantProductLike["colors"];
  gapClass?: string;
  swatchClass?: string;
  /** Optional caption under a swatch, e.g. exchange price difference. */
  colorAnnotation?: (color: string) => string | null;
}) {
  const isVariants = isVariantsProduct(product);

  return (
    <div className={cn("flex flex-wrap", gapClass)}>
      {colors.map((c, index) => {
        const row = findVariant(product, { size, color: c.name });
        const unavailable = isVariants && !isVariantSellable(row);
        const selected = !unavailable && color?.trim().toLowerCase() === c.name.trim().toLowerCase();
        const swatchImage = swatchImageForColor(c);
        const annotation = colorAnnotation?.(c.name) ?? null;
        const intent = () => onColorIntent?.(c.name);
        const title = unavailable
          ? `${c.name} — out of stock in this size`
          : annotation
            ? `${c.name} · ${annotation}`
            : c.name;
        const swatch = (
          <button
            type="button"
            disabled={unavailable}
            onClick={() => onColorChange(c.name)}
            onPointerEnter={intent}
            onFocus={intent}
            className={cn(
              "rounded-xl border-2 overflow-hidden transition-all duration-300 ease-out",
              swatchClass,
              selected ? "border-primary ring-2 ring-primary/30" : "border-white/70 hover:border-primary/50",
              unavailable && UNAVAILABLE_COLOR_CLASS,
            )}
            style={swatchImage ? undefined : { background: c.hex }}
            aria-label={annotation ? `${c.name}, ${annotation}` : c.name}
            aria-pressed={selected}
            title={title}
          >
            {swatchImage ? (
              <LazyImage
                src={storefrontImageUrl(swatchImage, "thumb")}
                fallbackSrc={swatchImage}
                alt={c.name}
                sizes={STOREFRONT_SWATCH_SIZES}
                decoding="async"
                className="h-full w-full object-cover"
                priority={index < SWATCH_EAGER_COUNT ? "eager" : "low"}
                fetchPriority="low"
              />
            ) : null}
          </button>
        );
        if (!annotation) {
          return (
            <span key={c.name} className="contents">
              {swatch}
            </span>
          );
        }
        return (
          <div key={c.name} className="flex flex-col items-center gap-0.5">
            {swatch}
            <span className="text-[10px] font-medium tabular-nums text-muted-foreground">{annotation}</span>
          </div>
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
