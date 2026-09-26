import { api } from "@/lib/api";
import { z } from "zod";

const Line = z.object({
  qty: z.number().int().min(1).max(50),
  price: z.number().min(0).max(10_000_000),
});

const Input = z.object({
  code: z.string().trim().min(1).max(64).regex(/^[A-Za-z0-9_-]+$/),
  subtotal: z.number().min(0).max(10_000_000),
  items: z.array(Line).max(50).optional(),
});

export type CouponCartLine = { qty: number; unitPrice: number };

export type CouponValidationResponse = {
  ok: boolean;
  code?: string;
  discount?: number;
  nudge?: string;
  bogo_details?: string;
  error?: string;
};

export function couponValidationData(code: string, subtotal: number, cart: CouponCartLine[]) {
  return {
    code,
    subtotal,
    items: cart.map((item) => ({ qty: item.qty, price: item.unitPrice })),
  };
}

export async function validateCoupon(input: { data: unknown }) {
  const parsed = Input.parse(input.data);
  return api.post<CouponValidationResponse>(
    "/api/v1/coupons/validate",
    parsed,
    { auth: true },
  );
}

export type ActiveCoupon = {
  code: string;
  discount_type: "percent" | "flat" | "bogo" | string;
  discount_value: number;
  min_order: number;
  max_discount: number | null;
  bogo_buy_qty?: number | null;
  bogo_get_qty?: number | null;
};

const BOGO_QTY_MIN = 1;
const BOGO_QTY_MAX = 10;

export function resolveBogoQuantities(c: { bogo_buy_qty?: number | null; bogo_get_qty?: number | null }) {
  const buyRaw = c.bogo_buy_qty ?? BOGO_QTY_MIN;
  const getRaw = c.bogo_get_qty ?? BOGO_QTY_MIN;
  const buy = Math.min(BOGO_QTY_MAX, Math.max(BOGO_QTY_MIN, Number(buyRaw) || BOGO_QTY_MIN));
  const get = Math.min(BOGO_QTY_MAX, Math.max(BOGO_QTY_MIN, Number(getRaw) || BOGO_QTY_MIN));
  return { buy, get, groupSize: buy + get };
}

export type BogoCalculationResult = {
  discount: number;
  missingItems: number;
  appliedGroups: number;
  nextTierMissing: number;
  eligible: boolean;
};

/**
 * Standard e-commerce greedy descending pairing algorithm matching backend BogoDiscountCalculator.
 */
export function calculateBogoDiscount(
  buyQty: number | null | undefined,
  getQty: number | null | undefined,
  percentOff: number | null | undefined,
  maxDiscount: number | null | undefined,
  subtotal: number | null | undefined,
  lines: CouponCartLine[]
): BogoCalculationResult {
  const buy = Math.min(BOGO_QTY_MAX, Math.max(BOGO_QTY_MIN, Number(buyQty) || BOGO_QTY_MIN));
  const get = Math.min(BOGO_QTY_MAX, Math.max(BOGO_QTY_MIN, Number(getQty) || BOGO_QTY_MIN));
  const groupSize = buy + get;

  const MAX_TOTAL_UNITS = 2000;
  const units: number[] = [];
  for (const line of lines || []) {
    if (!line || line.qty <= 0 || line.unitPrice == null) continue;
    const price = Math.max(0, Number(line.unitPrice) || 0);
    const qty = Math.min(line.qty, 500);
    for (let i = 0; i < qty; i++) {
      if (units.length >= MAX_TOTAL_UNITS) break;
      units.push(price);
    }
    if (units.length >= MAX_TOTAL_UNITS) break;
  }

  const missing = Math.max(0, groupSize - units.length);
  if (missing > 0) {
    return {
      discount: 0,
      missingItems: missing,
      appliedGroups: 0,
      nextTierMissing: missing,
      eligible: false,
    };
  }

  // Sort descending: highest value items are paired first
  units.sort((a, b) => b - a);

  const numGroups = Math.floor(units.length / groupSize);
  const remainder = units.length % groupSize;
  const nextTierMissing = remainder === 0 ? 0 : groupSize - remainder;

  const rawPercent = percentOff == null ? 100 : Number(percentOff);
  const percent = Math.min(100, Math.max(0, rawPercent));
  let discount = 0;

  for (let g = 0; g < numGroups; g++) {
    const groupStart = g * groupSize;
    for (let i = 0; i < get; i++) {
      const price = units[groupStart + buy + i];
      discount += Math.round(((price * percent) / 100) * 100) / 100;
    }
  }

  if (maxDiscount != null && Number.isFinite(maxDiscount)) {
    discount = Math.min(discount, Math.max(0, Number(maxDiscount)));
  }
  const cap = subtotal == null ? discount : Math.max(0, Number(subtotal));
  discount = Math.min(discount, cap);
  discount = Math.round(discount * 100) / 100;

  return {
    discount,
    missingItems: 0,
    appliedGroups: numGroups,
    nextTierMissing,
    eligible: true,
  };
}

export function formatCouponOffer(c: ActiveCoupon) {
  const type = String(c.discount_type ?? "").toLowerCase();
  if (type === "bogo") {
    const { buy, get } = resolveBogoQuantities(c);
    const off = c.discount_value >= 100 ? "Free" : `at ${c.discount_value}% off`;
    return `Buy ${buy} Get ${get} ${off}`;
  }
  if (type === "percent") {
    const cap = c.max_discount ? ` up to ₹${c.max_discount.toLocaleString("en-IN")}` : "";
    return `${c.discount_value}% off${cap}`;
  }
  return `₹${c.discount_value.toLocaleString("en-IN")} off`;
}

export function couponEligibilityHint(c: ActiveCoupon, subtotal: number, itemCount: number): string | null {
  const type = String(c.discount_type ?? "").toLowerCase();
  if (type === "bogo") {
    const { groupSize } = resolveBogoQuantities(c);
    if (itemCount < groupSize) {
      const missing = groupSize - itemCount;
      return missing === 1 ? "Add 1 more item to unlock" : `Add ${missing} more items to unlock`;
    }
  }
  if (subtotal < c.min_order) {
    return `Add ₹${(c.min_order - subtotal).toLocaleString("en-IN")} more to unlock`;
  }
  return null;
}

export async function listActiveCoupons() {
  return api.get<{ coupons: ActiveCoupon[] }>("/api/v1/coupons/active", { auth: true });
}
