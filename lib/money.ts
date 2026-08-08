import { type CommerceConfig, DEFAULT_COMMERCE, type ShipMode } from "@/lib/commerce";

/** Whole rupees (INR). */
export function roundInr(amount: number): number {
  const n = Number(amount);
  if (!Number.isFinite(n)) return 0;
  return Math.round(n);
}

export function formatInr(amount: number): string {
  return roundInr(amount).toLocaleString("en-IN", { maximumFractionDigits: 0 });
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

/** Flat COD handling fee — matches backend InternalAccountOrderResource#COD_FEE and the ₹49 figure in the shipping policy. */
export const COD_FEE_INR = 49;

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
  const lineGst = gstAmount(afterDiscount, commerce);
  const shippingGst = shipping > 0 ? gstAmount(shipping, commerce) : 0;
  const gst = lineGst + shippingGst;
  const grand =
    afterDiscount +
    shipping +
    roundInr(codFee) +
    roundInr(donation) +
    (commerce.pricesIncludeGst ? 0 : gst);
  return {
    afterDiscount,
    shipping,
    codFee: roundInr(codFee),
    gstIncluded: commerce.pricesIncludeGst ? gst : 0,
    gstAdded: commerce.pricesIncludeGst ? 0 : gst,
    gst,
    lineGst,
    shippingGst,
    grand,
    pricesIncludeGst: commerce.pricesIncludeGst,
  };
}
