import { roundInr } from "@/lib/money";

export type InvoiceOrderItem = {
  id?: number | string;
  product_name: string;
  size?: string | null;
  color?: string | null;
  sku?: string | null;
  hsn?: string | null;
  qty: number;
  price: number;
  mrp?: number | null;
  tax_pct?: number | null;
};

export type InvoiceOrder = {
  order_number: string;
  invoice_number?: string | null;
  status?: string | null;
  created_at?: string | null;
  subtotal: number;
  discount: number;
  shipping: number;
  cod_fee: number;
  tax: number;
  donation?: number;
  total: number;
  coupon_code?: string | null;
  payment_method?: string | null;
  payment_status?: string | null;
  tracking_number?: string | null;
  shipping_address?: { state?: string | null } | null;
  order_items: InvoiceOrderItem[];
};

export type InvoiceTaxKind = "local" | "interstate";
export type InvoiceLineKind = "product" | "shipping" | "cod" | "donation";

export type InvoiceBreakdownLine = {
  key: string;
  kind: InvoiceLineKind;
  itemName: string;
  details: string[];
  qty: number;
  mrp: number;
  totalDiscount: number;
  taxableAmount: number;
  taxType: string;
  taxRates: number[];
  taxAmounts: number[];
  taxAmountRaw: number;
  subTotal: number;
};

export type InvoiceBreakdown = {
  taxKind: InvoiceTaxKind;
  lines: InvoiceBreakdownLine[];
  taxableTotal: number;
  cgst: number;
  sgst: number;
  igst: number;
  totalAmountIncludingTax: number;
  roundOff: number;
  grandTotal: number;
  mrpTotal: number;
  productDiscount: number;
  sellingPrice: number;
  couponDiscount: number;
  amountAfterCoupon: number;
  shipping: number;
  codFee: number;
  donation: number;
};

type InvoiceBreakdownOptions = {
  gstRate?: number;
  pricesIncludeGst?: boolean;
  sellerState?: string;
};

const KARNATAKA_ALIASES = new Set(["karnataka", "ka", "29"]);

function amount(value: unknown): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function normalizeState(value: string | null | undefined): string {
  return String(value ?? "")
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]/g, "");
}

export function invoiceTaxKind(
  customerState: string | null | undefined,
  sellerState = "Karnataka",
): InvoiceTaxKind {
  const customer = normalizeState(customerState);
  const seller = normalizeState(sellerState);
  if (!customer) return "local";
  if (KARNATAKA_ALIASES.has(customer) && KARNATAKA_ALIASES.has(seller)) return "local";
  return customer === seller ? "local" : "interstate";
}

function validRate(value: unknown, fallback: number): number {
  const parsed = amount(value);
  return parsed > 0 && parsed <= 100 ? parsed : fallback;
}

function taxParts(taxKind: InvoiceTaxKind, rawTax: number) {
  if (taxKind === "interstate") {
    return { type: "IGST", rates: [1], amounts: [roundInr(rawTax)] };
  }
  return {
    type: "CGST\nSGST",
    rates: [0.5, 0.5],
    amounts: [roundInr(rawTax / 2), roundInr(rawTax / 2)],
  };
}

function lineDetails(item: InvoiceOrderItem): string[] {
  return [
    item.hsn ? `HSN Code: ${item.hsn}` : "",
    item.sku ? `Code: ${item.sku}` : "",
    item.size ? `Age/Size: ${item.size}` : "",
    item.color ? `Color: ${item.color}` : "",
  ].filter(Boolean);
}

/**
 * Reproduces the formulas in Roundoff Calculation.xlsx. Product prices, shipping, and COD
 * are GST-inclusive; donation is tax-free; local tax is rounded as two independent
 * CGST/SGST components. The explicit round-off line reconciles those displayed components to
 * the authoritative amount charged by the backend.
 */
export function buildInvoiceBreakdown(
  order: InvoiceOrder,
  options: InvoiceBreakdownOptions = {},
): InvoiceBreakdown {
  const gstRate = validRate(options.gstRate, 5);
  const pricesIncludeGst = options.pricesIncludeGst ?? true;
  const taxKind = invoiceTaxKind(order.shipping_address?.state, options.sellerState);
  const items = Array.isArray(order.order_items) ? order.order_items : [];
  const subtotal = amount(order.subtotal);
  const couponDiscount = Math.min(Math.max(0, amount(order.discount)), subtotal);
  let remainingCoupon = couponDiscount;

  const productLines = items.map((item, index): InvoiceBreakdownLine => {
    const qty = Math.max(1, Math.trunc(amount(item.qty)) || 1);
    const unitPrice = Math.max(0, amount(item.price));
    const sellingGross = roundInr(unitPrice * qty);
    const couponShare =
      index === items.length - 1
        ? roundInr(remainingCoupon)
        : roundInr(subtotal > 0 ? (couponDiscount * sellingGross) / subtotal : 0);
    remainingCoupon = roundInr(remainingCoupon - couponShare);
    const netSelling = roundInr(Math.max(0, sellingGross - couponShare));
    const unitMrp = Math.max(unitPrice, amount(item.mrp));
    const mrp = roundInr(unitMrp * qty);
    const rate = validRate(item.tax_pct, gstRate);
    const taxableAmount = pricesIncludeGst ? netSelling / (1 + rate / 100) : netSelling;
    const taxAmountRaw = pricesIncludeGst ? netSelling - taxableAmount : (netSelling * rate) / 100;
    const tax = taxParts(taxKind, taxAmountRaw);
    return {
      key: String(item.id ?? `${item.product_name}-${index}`),
      kind: "product",
      itemName: item.product_name,
      details: lineDetails(item),
      qty,
      mrp,
      totalDiscount: roundInr(Math.max(0, mrp - netSelling)),
      taxableAmount,
      taxType: tax.type,
      taxRates: tax.rates.map((part) => rate * part),
      taxAmounts: tax.amounts,
      taxAmountRaw,
      subTotal: pricesIncludeGst ? netSelling : roundInr(netSelling + taxAmountRaw),
    };
  });

  const extraLines: InvoiceBreakdownLine[] = [];
  const pushInclusiveGstLine = (
    kind: "shipping" | "cod",
    itemName: string,
    value: number,
  ) => {
    if (value <= 0) return;
    const taxable = pricesIncludeGst ? roundInr(value / (1 + gstRate / 100)) : value;
    const taxRaw = pricesIncludeGst ? value - taxable : (value * gstRate) / 100;
    const tax = taxParts(taxKind, taxRaw);
    extraLines.push({
      key: kind,
      kind,
      itemName,
      details: [],
      qty: 0,
      mrp: value,
      totalDiscount: 0,
      taxableAmount: taxable,
      taxType: tax.type,
      taxRates: tax.rates.map((part) => gstRate * part),
      taxAmounts: tax.amounts,
      taxAmountRaw: taxRaw,
      subTotal: pricesIncludeGst ? value : roundInr(value + taxRaw),
    });
  };
  const pushNoTaxLine = (kind: "donation", itemName: string, value: number) => {
    if (value <= 0) return;
    extraLines.push({
      key: kind,
      kind,
      itemName,
      details: [],
      qty: 0,
      mrp: value,
      totalDiscount: 0,
      taxableAmount: value,
      taxType: "—",
      taxRates: [0],
      taxAmounts: [0],
      taxAmountRaw: 0,
      subTotal: value,
    });
  };

  const shipping = roundInr(Math.max(0, amount(order.shipping)));
  const codFee = roundInr(Math.max(0, amount(order.cod_fee)));
  const donation = roundInr(Math.max(0, amount(order.donation)));
  pushInclusiveGstLine("shipping", "Shipping", shipping);
  pushInclusiveGstLine("cod", "Convenience Charges (COD)", codFee);
  pushNoTaxLine("donation", "Donation", donation);

  const lines = [...productLines, ...extraLines];
  const taxableTotalRaw = lines.reduce((sum, line) => sum + line.taxableAmount, 0);
  const taxRaw = lines.reduce((sum, line) => sum + line.taxAmountRaw, 0);
  const cgst = taxKind === "local" ? roundInr(taxRaw / 2) : 0;
  const sgst = taxKind === "local" ? roundInr(taxRaw / 2) : 0;
  const igst = taxKind === "interstate" ? roundInr(taxRaw) : 0;
  const totalAmountIncludingTax = roundInr(taxableTotalRaw + cgst + sgst + igst);
  const grandTotal = roundInr(amount(order.total));
  const mrpTotal = roundInr(productLines.reduce((sum, line) => sum + line.mrp, 0));

  return {
    taxKind,
    lines,
    taxableTotal: roundInr(taxableTotalRaw),
    cgst,
    sgst,
    igst,
    totalAmountIncludingTax,
    roundOff: roundInr(grandTotal - totalAmountIncludingTax),
    grandTotal,
    mrpTotal,
    productDiscount: roundInr(Math.max(0, mrpTotal - subtotal)),
    sellingPrice: roundInr(subtotal),
    couponDiscount: roundInr(couponDiscount),
    amountAfterCoupon: roundInr(Math.max(0, subtotal - couponDiscount)),
    shipping,
    codFee,
    donation,
  };
}
