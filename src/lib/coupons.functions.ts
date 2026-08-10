import { api } from "@/lib/api";
import { z } from "zod";

const Input = z.object({
  code: z.string().trim().min(1).max(64).regex(/^[A-Za-z0-9_-]+$/),
  subtotal: z.number().min(0).max(10_000_000),
});

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
  discount_type: "percent" | "flat";
  discount_value: number;
  min_order: number;
  max_discount: number | null;
};

export async function listActiveCoupons() {
  return api.get<{ coupons: ActiveCoupon[] }>("/api/v1/coupons/active", { auth: true });
}
