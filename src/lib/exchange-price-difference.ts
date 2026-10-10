import { formatInr, roundInr } from "@/lib/money";
import {
  findVariant,
  isVariantSellable,
  lineUnitPrice,
  sellableColorsForSize,
  type VariantProductLike,
} from "@/lib/product-variants";

export type ExchangePriceKind = "collect" | "refund" | "same";

export function exchangeLineAmounts({
  originalUnitPrice,
  replacementUnitPrice,
  qty,
}: {
  originalUnitPrice: number;
  replacementUnitPrice: number;
  qty?: number | null;
}): { originalAmount: number; replacementAmount: number; difference: number; qty: number } {
  const units = Math.max(1, Math.round(Number(qty) || 1));
  const originalAmount = roundInr(Math.max(0, Number(originalUnitPrice) || 0) * units);
  const replacementAmount = roundInr(Math.max(0, Number(replacementUnitPrice) || 0) * units);
  return {
    originalAmount,
    replacementAmount,
    difference: roundInr(replacementAmount - originalAmount),
    qty: units,
  };
}

export function formatSignedInrDifference(difference: number): string | null {
  const amount = roundInr(difference);
  if (amount === 0) return null;
  const rupees = `₹${formatInr(Math.abs(amount))}`;
  return amount > 0 ? `+${rupees}` : `−${rupees}`;
}

export function describeExchangePriceDifference(difference: number): {
  kind: ExchangePriceKind;
  label: string;
  signedAmount: string;
} {
  const amount = roundInr(difference);
  if (amount > 0) {
    return {
      kind: "collect",
      label: `You'll pay ₹${formatInr(amount)} extra`,
      signedAmount: `+₹${formatInr(amount)}`,
    };
  }
  if (amount < 0) {
    const refund = Math.abs(amount);
    return {
      kind: "refund",
      label: `You'll get ₹${formatInr(refund)} back`,
      signedAmount: `−₹${formatInr(refund)}`,
    };
  }
  return {
    kind: "same",
    label: "Same price as your original item",
    signedAmount: "₹0",
  };
}

/** Catalog unit price for a size, preferring the currently selected colour when that combo is sellable. */
export function representativeReplacementUnitPrice(
  product: VariantProductLike,
  size: string,
  preferredColor?: string | null,
): number | null {
  if (preferredColor) {
    const preferred = findVariant(product, { size, color: preferredColor });
    if (isVariantSellable(preferred) && preferred.price > 0) return preferred.price;
  }
  const colors = sellableColorsForSize(product, size);
  for (const color of colors) {
    const variant = findVariant(product, { size, color });
    if (isVariantSellable(variant) && variant.price > 0) return variant.price;
  }
  const fallback = lineUnitPrice(product, { size, color: preferredColor });
  return fallback > 0 ? fallback : null;
}

export function selectedReplacementUnitPrice(
  product: VariantProductLike,
  size: string | null,
  color: string | null,
): number | null {
  if (!size || !color) return null;
  const variant = findVariant(product, { size, color });
  if (variant && variant.price > 0) return variant.price;
  const fallback = lineUnitPrice(product, { size, color });
  return fallback > 0 ? fallback : null;
}
