/** Storefront pricing & shipping — loaded from site settings with safe defaults. */
export type CommerceConfig = {
  gstRate: number;
  pricesIncludeGst: boolean;
  shippingStandardInr: number;
  shippingExpressInr: number;
  freeShippingMinInr: number;
};

export const DEFAULT_COMMERCE: CommerceConfig = {
  gstRate: 5,
  pricesIncludeGst: true,
  shippingStandardInr: 99,
  shippingExpressInr: 150,
  freeShippingMinInr: 2500,
};

const GST_RATE_MAX = 100;
const SHIPPING_INR_MAX = 10_000;
const FREE_SHIPPING_MIN_INR_MAX = 1_000_000;

function parseNum(v: unknown, fallback: number, max?: number): number {
  const n = typeof v === "number" ? v : Number(v);
  if (!Number.isFinite(n) || n < 0) return fallback;
  if (max !== undefined && n > max) return fallback;
  return n;
}

function parseBool(v: unknown, fallback: boolean): boolean {
  if (v === true || v === "true") return true;
  if (v === false || v === "false") return false;
  return fallback;
}

export function commerceFromRow(row: Record<string, unknown> | null | undefined): CommerceConfig {
  if (!row) return { ...DEFAULT_COMMERCE };
  return {
    gstRate: parseNum(row.gst_rate, DEFAULT_COMMERCE.gstRate, GST_RATE_MAX),
    pricesIncludeGst: parseBool(row.prices_include_gst, DEFAULT_COMMERCE.pricesIncludeGst),
    shippingStandardInr: parseNum(row.shipping_standard_inr, DEFAULT_COMMERCE.shippingStandardInr, SHIPPING_INR_MAX),
    shippingExpressInr: parseNum(row.shipping_express_inr, DEFAULT_COMMERCE.shippingExpressInr, SHIPPING_INR_MAX),
    freeShippingMinInr: parseNum(row.free_shipping_min_inr, DEFAULT_COMMERCE.freeShippingMinInr, FREE_SHIPPING_MIN_INR_MAX),
  };
}

export type ShipMode = "std" | "exp";

export type CommerceSiteFields = {
  gst_rate: number;
  prices_include_gst: boolean;
  shipping_standard_inr: number;
  shipping_express_inr: number;
  free_shipping_min_inr: number;
};

export function commerceFromSiteSettings(s: CommerceSiteFields): CommerceConfig {
  return {
    gstRate: s.gst_rate,
    pricesIncludeGst: s.prices_include_gst,
    shippingStandardInr: s.shipping_standard_inr,
    shippingExpressInr: s.shipping_express_inr,
    freeShippingMinInr: s.free_shipping_min_inr,
  };
}
