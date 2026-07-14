import { api } from "@/lib/api";
import { z } from "zod";

const AddressSchema = z.object({
  full_name: z.string().min(1).max(120),
  email: z.string().email().max(160),
  phone: z.string().min(6).max(20),
  line1: z.string().min(1).max(200),
  line2: z.string().max(200).optional().default(""),
  city: z.string().min(1).max(80),
  state: z.string().min(1).max(80),
  pincode: z.string().regex(/^\d{6}$/),
  baby_name: z.string().max(80).optional().default(""),
});

const ItemSchema = z.object({
  product_id: z.string().min(1).max(120),
  product_slug: z.string().max(160).optional().default(""),
  product_name: z.string().min(1).max(200),
  product_image: z.string().max(600).optional().default(""),
  size: z.string().max(40).optional().default(""),
  color: z.string().max(40).optional().default(""),
  sku: z.string().max(120).optional().default(""),
  qty: z.number().int().min(1).max(50),
  price: z.number().min(0).max(1_000_000),
});

const CreateOrderInput = z.object({
  items: z.array(ItemSchema).min(1).max(50),
  shipping_address: AddressSchema,
  shipping_mode: z.enum(["std", "exp"]).default("std"),
  coupon_code: z.string().max(40).optional().default(""),
  donation: z.number().min(0).max(100000).default(0),
  notes: z.string().max(500).optional().default(""),
});

export async function createOrder(input: { data: z.infer<typeof CreateOrderInput> }) {
  const parsed = CreateOrderInput.parse(input.data);
  return api.post("/api/v1/orders", parsed, { auth: true });
}

const VerifyInput = z.object({
  orderId: z.coerce.number().int().positive(),
  razorpay_order_id: z.string().min(1).max(120),
  razorpay_payment_id: z.string().min(1).max(120),
  razorpay_signature: z.string().min(1).max(500),
});

export async function verifyPayment(input: { data: z.infer<typeof VerifyInput> }) {
  const parsed = VerifyInput.parse(input.data);
  return api.post("/api/v1/orders/verify-payment", parsed, { auth: true });
}

const SyncPaymentInput = z.object({
  orderId: z.coerce.number().int().positive(),
});

export async function syncPaymentStatus(input: { data: z.infer<typeof SyncPaymentInput> }) {
  const parsed = SyncPaymentInput.parse(input.data);
  return api.post("/api/v1/orders/sync-payment", parsed, { auth: true });
}

export async function listMyOrders(): Promise<{ orders: any[] }> {
  return api.get("/api/v1/orders", { auth: true });
}

const OrderNumberInput = z.object({ orderNumber: z.string().min(3).max(40) });

export async function getOrder(input: { data: z.infer<typeof OrderNumberInput> }): Promise<{ order: any }> {
  const parsed = OrderNumberInput.parse(input.data);
  return api.get(`/api/v1/orders/${encodeURIComponent(parsed.orderNumber)}`, { auth: true });
}

export async function trackOrderPublic(input: { data: z.infer<typeof OrderNumberInput> }): Promise<{ order: any }> {
  const parsed = OrderNumberInput.parse(input.data);
  return api.get(`/api/v1/orders/track/${encodeURIComponent(parsed.orderNumber)}`);
}

export async function listMyPayments(): Promise<{ payments: any[] }> {
  return api.get("/api/v1/payments", { auth: true });
}

export async function getMyProfile(): Promise<{ profile: { full_name: string; phone: string } }> {
  const profile = await api.get("/api/v1/account/profile", { auth: true });
  const full_name = (profile as any)?.full_name ?? "";
  const phone = (profile as any)?.phone ?? "";
  return { profile: { full_name, phone } };
}

const ProfileUpdate = z.object({
  full_name: z.string().trim().max(120).default(""),
  phone: z.string().trim().max(20).default(""),
});

export async function updateMyProfile(input: { data: z.infer<typeof ProfileUpdate> }): Promise<{ ok: boolean }> {
  const parsed = ProfileUpdate.parse(input.data);
  return api.put("/api/v1/account/profile", { full_name: parsed.full_name, phone: parsed.phone }, { auth: true });
}

// Returns whether the signed-in user qualifies for the first-order discount.
// A user qualifies if they have no prior paid orders.
export async function getFirstOrderEligibility(): Promise<{ eligible: boolean; discount_pct: number }> {
  const res = await listMyOrders();
  const hasPaid = (res.orders ?? []).some((o) => o?.payment_status === "paid");
  return { eligible: !hasPaid, discount_pct: 10 };
}

export type OrderReturnRow = {
  id: number;
  return_number: string;
  status: string;
  reason: string;
  rejection_reason?: string | null;
  refund_amount?: number | null;
  requested_at: string;
  resolved_at?: string | null;
  order_number?: string;
  order_total?: number;
};

export async function listMyOrderReturns(): Promise<{ returns: OrderReturnRow[] }> {
  const returns = await api.get<OrderReturnRow[]>("/api/v1/order-returns", { auth: true });
  return { returns: returns ?? [] };
}

export async function checkReturnEligibility(
  orderNumber: string,
): Promise<{ eligible: boolean; message?: string; order_number: string }> {
  return api.get(`/api/v1/order-returns/eligibility/${encodeURIComponent(orderNumber)}`, { auth: true });
}

const CreateReturnInput = z.object({
  order_number: z.string().min(3).max(40),
  reason: z.string().min(10).max(4000),
});

export async function requestOrderReturn(input: {
  data: z.infer<typeof CreateReturnInput>;
}): Promise<OrderReturnRow> {
  const parsed = CreateReturnInput.parse(input.data);
  return api.post("/api/v1/order-returns", parsed, { auth: true });
}

export async function cancelOrderReturn(returnNumber: string): Promise<OrderReturnRow> {
  return api.post(
    `/api/v1/order-returns/${encodeURIComponent(returnNumber)}/cancel`,
    {},
    { auth: true },
  );
}

export type AdminOrderReturnRow = OrderReturnRow & {
  admin_notes?: string | null;
  razorpay_refund_id?: string | null;
  ledgerly_credit_note_id?: string | null;
  customer_login?: string | null;
  order_id?: number;
};

export async function listAdminOrderReturns(status?: string): Promise<{ returns: AdminOrderReturnRow[] }> {
  const query = status ? `?status=${encodeURIComponent(status)}` : "";
  const returns = await api.get<AdminOrderReturnRow[]>(`/api/v1/admin/order-returns${query}`, { auth: true });
  return { returns: returns ?? [] };
}

export async function approveOrderReturn(returnNumber: string, adminNotes?: string) {
  return api.post(
    `/api/v1/admin/order-returns/${encodeURIComponent(returnNumber)}/approve`,
    { admin_notes: adminNotes ?? "" },
    { auth: true },
  );
}

export async function rejectOrderReturn(
  returnNumber: string,
  rejectionReason: string,
  adminNotes?: string,
) {
  return api.post(
    `/api/v1/admin/order-returns/${encodeURIComponent(returnNumber)}/reject`,
    { rejection_reason: rejectionReason, admin_notes: adminNotes ?? "" },
    { auth: true },
  );
}

export async function markOrderReturnReceived(returnNumber: string, adminNotes?: string) {
  return api.post(
    `/api/v1/admin/order-returns/${encodeURIComponent(returnNumber)}/mark-received`,
    { admin_notes: adminNotes ?? "" },
    { auth: true },
  );
}

export async function refundOrderReturn(returnNumber: string, adminNotes?: string) {
  return api.post(
    `/api/v1/admin/order-returns/${encodeURIComponent(returnNumber)}/refund`,
    { admin_notes: adminNotes ?? "" },
    { auth: true },
  );
}