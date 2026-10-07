import {
  findVariant,
  isSimpleProductSellable,
  isVariantSellable,
  isVariantsProduct,
} from "@/lib/product-variants";
import { VariantColorButtons, VariantSizeButtons, variantColorsForDisplay } from "@/components/VariantPickers";
import type { Product } from "@/data/products";
import { formatInr } from "@/lib/money";
import { cn } from "@/lib/utils";
import {
  describeExchangePriceDifference,
  exchangeLineAmounts,
  formatSignedInrDifference,
  representativeReplacementUnitPrice,
  selectedReplacementUnitPrice,
} from "@/lib/exchange-price-difference";

/** Lets a customer pick a replacement size/colour for an exchange, reusing the storefront's variant pickers. */
export function ExchangeReplacementPicker({
  product,
  originalSize,
  originalColor,
  originalUnitPrice,
  qty,
  size,
  color,
  onSizeChange,
  onColorChange,
}: {
  product: Product | undefined;
  originalSize?: string | null;
  originalColor?: string | null;
  originalUnitPrice?: number | null;
  qty?: number | null;
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
  const paidUnit =
    originalUnitPrice != null && Number.isFinite(Number(originalUnitPrice)) && Number(originalUnitPrice) > 0
      ? Number(originalUnitPrice)
      : representativeReplacementUnitPrice(product, originalSize ?? "", originalColor) ?? product.price;

  const selectedUnit = selectedReplacementUnitPrice(product, size, color);
  const amounts =
    selectedUnit != null
      ? exchangeLineAmounts({ originalUnitPrice: paidUnit, replacementUnitPrice: selectedUnit, qty })
      : null;
  const differenceCopy = amounts ? describeExchangePriceDifference(amounts.difference) : null;

  const sizeAnnotation = (candidateSize: string) => {
    const unit = representativeReplacementUnitPrice(product, candidateSize, color);
    if (unit == null) return null;
    return formatSignedInrDifference(
      exchangeLineAmounts({ originalUnitPrice: paidUnit, replacementUnitPrice: unit, qty }).difference,
    );
  };

  const colorAnnotation = (candidateColor: string) => {
    const unit = selectedReplacementUnitPrice(product, size, candidateColor);
    if (unit == null) return null;
    return formatSignedInrDifference(
      exchangeLineAmounts({ originalUnitPrice: paidUnit, replacementUnitPrice: unit, qty }).difference,
    );
  };

  return (
    <div className="space-y-3">
      {(originalSize || originalColor) && (
        <p className="text-[11px] text-muted-foreground">
          Currently: {[originalSize, originalColor].filter(Boolean).join(" · ")}
          {paidUnit > 0 ? ` · ₹${formatInr(paidUnit)} each` : ""}
        </p>
      )}
      <div>
        <div className="text-xs font-medium text-muted-foreground mb-1.5">New size</div>
        <VariantSizeButtons
          product={product}
          size={size}
          onSizeChange={onSizeChange}
          sizeAnnotation={sizeAnnotation}
        />
      </div>
      <div>
        <div className="text-xs font-medium text-muted-foreground mb-1.5">New colour</div>
        <VariantColorButtons
          product={product}
          size={size}
          color={color}
          onColorChange={onColorChange}
          colors={availableColors}
          colorAnnotation={colorAnnotation}
        />
      </div>
      {amounts && differenceCopy ? (
        <ExchangePriceDifferenceCard
          originalLabel={[originalSize, originalColor].filter(Boolean).join(" · ") || "Original item"}
          replacementLabel={[size, color].filter(Boolean).join(" · ") || "Replacement"}
          originalAmount={amounts.originalAmount}
          replacementAmount={amounts.replacementAmount}
          qty={amounts.qty}
          kind={differenceCopy.kind}
          label={differenceCopy.label}
        />
      ) : null}
      {size && color && !sellable ? (
        <p className="text-xs text-amber-700">This size and colour is currently unavailable for exchange.</p>
      ) : null}
    </div>
  );
}

function ExchangePriceDifferenceCard({
  originalLabel,
  replacementLabel,
  originalAmount,
  replacementAmount,
  qty,
  kind,
  label,
}: {
  originalLabel: string;
  replacementLabel: string;
  originalAmount: number;
  replacementAmount: number;
  qty: number;
  kind: "collect" | "refund" | "same";
  label: string;
}) {
  return (
    <div
      role="status"
      className={cn(
        "rounded-xl border px-3 py-2.5 space-y-1.5",
        kind === "collect" && "border-amber-200/80 bg-amber-50/90 text-amber-950",
        kind === "refund" && "border-emerald-200/80 bg-emerald-50/90 text-emerald-950",
        kind === "same" && "border-border/70 bg-muted/50 text-foreground",
      )}
    >
      <p className="text-[11px] font-medium uppercase tracking-wide opacity-80">Price difference</p>
      <div className="flex justify-between gap-3 text-xs">
        <span className="min-w-0 truncate">{originalLabel}</span>
        <span className="tabular-nums shrink-0">₹{formatInr(originalAmount)}</span>
      </div>
      <div className="flex justify-between gap-3 text-xs">
        <span className="min-w-0 truncate">
          {replacementLabel}
          {qty > 1 ? ` · qty ${qty}` : ""}
        </span>
        <span className="tabular-nums shrink-0">₹{formatInr(replacementAmount)}</span>
      </div>
      <p className="text-sm font-semibold">{label}</p>
      <p className="text-[11px] leading-relaxed opacity-80">
        Confirmed after approval, on item price after discounts. Delivery and COD fees are not included.
      </p>
    </div>
  );
}
