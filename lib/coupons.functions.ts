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

export function couponValidationData(code: string, subtotal: number, cart: CouponCartLine[]) {
  return {
    code,
    subtotal,
    items: cart.map((item) => ({ qty: item.qty, price: item.unitPrice })),
  };
}

export async function validateCoupon(input: { data: unknown }) {
  const parsed = Input.parse(input.data);
  return api.post<{ ok: boolean; code?: string; discount?: number; error?: string }>(
    "/api/v1/coupons/validate",
    parsed,
    { auth: true },
  );
}

export type ActiveCoupon = {
  code: string;
  discount_type: "percent" | "flat" | "bogo";
  discount_value: number;
  min_order: number;
  max_discount: number | null;
  bogo_buy_qty?: number | null;
  bogo_get_qty?: number | null;
};

const BOGO_QTY_MIN = 1;
const BOGO_QTY_MAX = 10;

export function resolveBogoQuantities(c: Pick<ActiveCoupon, "bogo_buy_qty" | "bogo_get_qty">) {
  const buyRaw = c.bogo_buy_qty ?? BOGO_QTY_MIN;
  const getRaw = c.bogo_get_qty ?? BOGO_QTY_MIN;
  const buy = Math.min(BOGO_QTY_MAX, Math.max(BOGO_QTY_MIN, buyRaw));
  const get = Math.min(BOGO_QTY_MAX, Math.max(BOGO_QTY_MIN, getRaw));
  return { buy, get, groupSize: buy + get };
}

export function formatCouponOffer(c: ActiveCoupon) {
  if (c.discount_type === "bogo") {
    const { buy, get } = resolveBogoQuantities(c);
    const off = c.discount_value >= 100 ? "Free" : `at ${c.discount_value}% off`;
    return `Buy ${buy} Get ${get} ${off}`;
  }
  if (c.discount_type === "percent") {
    const cap = c.max_discount ? ` up to ₹${c.max_discount.toLocaleString("en-IN")}` : "";
    return `${c.discount_value}% off${cap}`;
  }
  return `₹${c.discount_value.toLocaleString("en-IN")} off`;
}

export function couponEligibilityHint(c: ActiveCoupon, subtotal: number, itemCount: number): string | null {
  if (c.discount_type === "bogo") {
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
