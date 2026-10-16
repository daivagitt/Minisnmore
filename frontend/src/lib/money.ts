import { type CommerceConfig, DEFAULT_COMMERCE, type ShipMode } from "@/lib/commerce";

/** INR rounded to the nearest paise. */
export function roundInr(amount: number): number {
  const n = Number(amount);
  if (!Number.isFinite(n)) return 0;
  return Math.round((n + Number.EPSILON) * 100) / 100;
}

export function formatInr(amount: number): string {
  return roundInr(amount).toLocaleString("en-IN", { maximumFractionDigits: 2 });
}

/** @deprecated use DEFAULT_COMMERCE.freeShippingMinInr */
export const FREE_SHIPPING_MIN_INR = DEFAULT_COMMERCE.freeShippingMinInr;
/** @deprecated use DEFAULT_COMMERCE.shippingStandardInr */
export const SHIPPING_FLAT_INR = DEFAULT_COMMERCE.shippingStandardInr;

export function gstAmount(taxableTotal: number, commerce: CommerceConfig = DEFAULT_COMMERCE): number {
  const base = Math.max(0, roundInr(taxableTotal));
  const rate = commerce.gstRate;
  if (commerce.pricesIncludeGst) {
    return roundInr((base * rate) / (100 + rate));
  }
  return roundInr((base * rate) / 100);
}

/** GST component included in a GST-inclusive amount (configurable rate). */
export function gstIncludedInPrice(
  taxableTotal: number,
  gstRate: number = DEFAULT_COMMERCE.gstRate,
): number {
  const base = Math.max(0, roundInr(taxableTotal));
  const rate = Number(gstRate);
  if (!Number.isFinite(rate) || rate <= 0) return 0;
  return roundInr((base * rate) / (100 + rate));
}

export function shippingForOrder(
  amountAfterDiscount: number,
  shipMode: ShipMode = "std",
  commerce: CommerceConfig = DEFAULT_COMMERCE,
): number {
  const base = roundInr(amountAfterDiscount);
  if (base <= 0) return 0;
  if (shipMode === "exp") {
    return roundInr(commerce.shippingExpressInr);
  }
  if (base >= commerce.freeShippingMinInr) return 0;
  return roundInr(commerce.shippingStandardInr);
}

/** @deprecated use DEFAULT_COMMERCE.codFeeInr */
export const COD_FEE_INR = DEFAULT_COMMERCE.codFeeInr;

export function orderTotals({
  subtotal,
  discount = 0,
  donation = 0,
  codFee = 0,
  shipMode = "std",
  commerce = DEFAULT_COMMERCE,
}: {
  subtotal: number;
  discount?: number;
  donation?: number;
  codFee?: number;
  shipMode?: ShipMode;
  commerce?: CommerceConfig;
}) {
  const afterDiscount = Math.max(0, roundInr(subtotal - discount));
  const shipping = shippingForOrder(afterDiscount, shipMode, commerce);
  // The catalogue price is GST-inclusive by default. Extract tax only after all product
  // discounts, exactly as the invoice workbook does. COD is also GST-inclusive; shipping
  // and donations do not carry GST in these scenarios.
  const lineGst = gstAmount(afterDiscount, commerce);
  const roundedCodFee = roundInr(codFee);
  const codGst = gstIncludedInPrice(roundedCodFee, commerce.gstRate);
  const shippingGst = 0;
  const gst = roundInr(lineGst + codGst);
  const grand = roundInr(
    afterDiscount +
    shipping +
    roundedCodFee +
    roundInr(donation) +
    (commerce.pricesIncludeGst ? 0 : lineGst),
  );
  const taxableTotal = roundInr(
    afterDiscount - (commerce.pricesIncludeGst ? lineGst : 0) + shipping + roundedCodFee - codGst,
  );
  const roundOff = roundInr(grand - roundInr(taxableTotal + gst + roundInr(donation)));
  return {
    afterDiscount,
    shipping,
    codFee: roundedCodFee,
    gstIncluded: roundInr((commerce.pricesIncludeGst ? lineGst : 0) + codGst),
    gstAdded: commerce.pricesIncludeGst ? 0 : lineGst,
    gst,
    lineGst,
    codGst,
    shippingGst,
    taxableTotal,
    roundOff,
    grand,
    pricesIncludeGst: commerce.pricesIncludeGst,
  };
}
