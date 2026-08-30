import { api } from "@/lib/api";
import { LIST_PAGE_SIZE } from "@/lib/pagination";
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
  payment_method: z.enum(["online", "cod"]).optional().default("online"),
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

const RetryPaymentInput = z.object({
  orderId: z.coerce.number().int().positive(),
});

export type RetryPaymentInit = {
  orderId: number;
  orderNumber: string;
  razorpayOrderId: string;
  keyId: string;
  amount: number;
  currency: string;
  demoMode: boolean;
  gatewayMode: "demo" | "test" | "live" | "unknown";
};

export async function retryOrderPayment(input: {
  data: z.infer<typeof RetryPaymentInput>;
}): Promise<RetryPaymentInit> {
  const parsed = RetryPaymentInput.parse(input.data);
  return api.post("/api/v1/orders/retry-payment", parsed, { auth: true });
}

export async function listMyOrders(): Promise<{ orders: any[] }> {
  return api.get("/api/v1/orders", { auth: true });
}

const OrderNumberInput = z.object({ orderNumber: z.string().min(3).max(40) });

export async function getOrder(input: {
  data: z.infer<typeof OrderNumberInput>;
}): Promise<{ order: any }> {
  const parsed = OrderNumberInput.parse(input.data);
  return api.get(`/api/v1/orders/${encodeURIComponent(parsed.orderNumber)}`, { auth: true });
}

export async function getAdminOrder(orderNumber: string): Promise<{ order: any }> {
  const parsed = OrderNumberInput.parse({ orderNumber });
  return api.get(`/api/v1/admin/orders/${encodeURIComponent(parsed.orderNumber)}`, { auth: true });
}

export async function cancelOrder(orderNumber: string, reason: string): Promise<{ order: any }> {
  const parsed = OrderNumberInput.parse({ orderNumber });
  return api.post(
    `/api/v1/orders/${encodeURIComponent(parsed.orderNumber)}/cancel`,
    { reason },
    { auth: true },
  );
}

export async function trackOrderPublic(input: {
  data: z.infer<typeof OrderNumberInput>;
}): Promise<{ order: any }> {
  const parsed = OrderNumberInput.parse(input.data);
  return api.get(`/api/v1/orders/track/${encodeURIComponent(parsed.orderNumber)}`);
}

export async function listMyPayments(): Promise<{ payments: any[] }> {
  return api.get("/api/v1/payments", { auth: true });
}

export type NotificationEvent = {
  id: number;
  event_type: string;
  title: string;
  body: string;
  order_number: string;
  created_at: string;
};

export async function listMyNotifications(): Promise<{ events: NotificationEvent[] }> {
  const res = await api.get<{ events: NotificationEvent[] }>("/api/v1/account/notifications", {
    auth: true,
  });
  return { events: res?.events ?? [] };
}

export async function getMyProfile(): Promise<{ profile: { full_name: string; phone: string } }> {
  const profile = await api.get("/api/v1/account/profile", { auth: true });
  const full_name = (profile as any)?.full_name ?? "";
  const phone = (profile as any)?.phone ?? "";
  return { profile: { full_name, phone } };
}

const FULL_NAME_PATTERN = /^\p{L}[\p{L} .'-]*$/u;
const PHONE_PATTERN = /^(\+91[\s-]?)?[6-9]\d{4}[\s-]?\d{5}$/;

export const FullNameSchema = z
  .string()
  .trim()
  .min(2, "Full name must be at least 2 characters")
  .max(120, "Full name is too long")
  .regex(
    FULL_NAME_PATTERN,
    "Full name may only contain letters, spaces, apostrophes, periods and hyphens",
  );

export function fullNameFieldError(fullName: string): string | undefined {
  const result = FullNameSchema.safeParse(fullName);
  if (result.success) return undefined;
  return result.error.issues[0]?.message;
}

export const ProfileUpdate = z.object({
  full_name: FullNameSchema,
  phone: z
    .string()
    .trim()
    .min(1, "Phone number is required")
    .max(20, "Phone number is too long")
    .regex(PHONE_PATTERN, "Enter a valid 10-digit Indian mobile number"),
});

export async function updateMyProfile(input: {
  data: z.infer<typeof ProfileUpdate>;
}): Promise<{ ok: boolean }> {
  const parsed = ProfileUpdate.parse(input.data);
  return api.put(
    "/api/v1/account/profile",
    { full_name: parsed.full_name, phone: parsed.phone },
    { auth: true },
  );
}

const ProfileBootstrap = z.object({
  full_name: FullNameSchema,
  email: z.string().trim().email().max(160),
});

export async function bootstrapProfileFromSignup(input: {
  data: z.infer<typeof ProfileBootstrap>;
}): Promise<{ ok: boolean }> {
  const parsed = ProfileBootstrap.parse(input.data);
  return api.post(
    "/api/v1/account/profile/bootstrap",
    { full_name: parsed.full_name, email: parsed.email.toLowerCase() },
    { auth: true },
  );
}

export type OrderReturnRow = {
  id: number;
  return_number: string;
  status: string;
  request_type: "return" | "exchange";
  reason: string;
  rejection_reason?: string | null;
  refund_amount?: number | null;
  requested_at: string;
  resolved_at?: string | null;
  order_number?: string;
  order_total?: number;
  order_item_id?: number;
  product_name?: string;
  product_slug?: string;
  original_size?: string | null;
  original_color?: string | null;
  replacement_size?: string | null;
  replacement_color?: string | null;
};

export async function listMyOrderReturns(): Promise<{ returns: OrderReturnRow[] }> {
  const returns = await api.get<OrderReturnRow[]>("/api/v1/order-returns", { auth: true });
  return { returns: returns ?? [] };
}

export async function checkReturnEligibility(
  orderNumber: string,
): Promise<{ eligible: boolean; message?: string; order_number: string }> {
  return api.get(`/api/v1/order-returns/eligibility/${encodeURIComponent(orderNumber)}`, {
    auth: true,
  });
}

export async function getReturnsConfig(): Promise<{ enabled: boolean; window_days: number }> {
  return api.get("/api/v1/order-returns/config", { auth: true });
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

const CreateExchangeInput = z.object({
  order_number: z.string().min(3).max(40),
  order_item_id: z.number().int().positive(),
  reason: z.string().min(10).max(4000),
  replacement_size: z.string().min(1).max(40),
  replacement_color: z.string().min(1).max(40),
});

export async function requestOrderExchange(input: {
  data: z.infer<typeof CreateExchangeInput>;
}): Promise<OrderReturnRow> {
  const parsed = CreateExchangeInput.parse(input.data);
  return api.post("/api/v1/order-returns/exchange", parsed, { auth: true });
}

export async function checkExchangeReplacementAvailability(
  orderItemId: number,
  size: string,
  color: string,
): Promise<{ sellable: boolean }> {
  const query = `?size=${encodeURIComponent(size)}&color=${encodeURIComponent(color)}`;
  return api.get(`/api/v1/order-returns/exchange-availability/${orderItemId}${query}`, {
    auth: true,
  });
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

export async function listAdminOrderReturns(
  status?: string,
): Promise<{ returns: AdminOrderReturnRow[] }> {
  const query = status ? `?status=${encodeURIComponent(status)}` : "";
  const returns = await api.get<AdminOrderReturnRow[]>(`/api/v1/admin/order-returns${query}`, {
    auth: true,
  });
  return { returns: returns ?? [] };
}

function adminReturnAction(returnNumber: string, action: string, adminNotes?: string) {
  return api.post(
    `/api/v1/admin/order-returns/${encodeURIComponent(returnNumber)}/${action}`,
    { admin_notes: adminNotes ?? "" },
    { auth: true },
  );
}

export async function approveOrderReturn(returnNumber: string, adminNotes?: string) {
  return adminReturnAction(returnNumber, "approve", adminNotes);
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

export async function markOrderReturnPickupRequested(returnNumber: string, adminNotes?: string) {
  return adminReturnAction(returnNumber, "pickup-requested", adminNotes);
}

export async function markOrderReturnPickedUp(returnNumber: string, adminNotes?: string) {
  return adminReturnAction(returnNumber, "picked-up", adminNotes);
}

export async function markOrderReturnReceived(returnNumber: string, adminNotes?: string) {
  return adminReturnAction(returnNumber, "mark-received", adminNotes);
}

export async function markOrderReturnInspected(returnNumber: string, adminNotes?: string) {
  return adminReturnAction(returnNumber, "inspect", adminNotes);
}

export async function acceptOrderReturnAfterInspection(returnNumber: string, adminNotes?: string) {
  return adminReturnAction(returnNumber, "accept", adminNotes);
}

export async function allocateReplacementForOrderReturn(returnNumber: string, adminNotes?: string) {
  return adminReturnAction(returnNumber, "allocate-replacement", adminNotes);
}

export async function convertOrderReturnToRefund(returnNumber: string, adminNotes?: string) {
  return adminReturnAction(returnNumber, "convert-to-refund", adminNotes);
}

export async function markOrderReturnRepacking(returnNumber: string, adminNotes?: string) {
  return adminReturnAction(returnNumber, "repacking", adminNotes);
}

export async function markOrderReturnReadyToShip(returnNumber: string, adminNotes?: string) {
  return adminReturnAction(returnNumber, "ready-to-ship", adminNotes);
}

export async function markOrderReturnShipped(returnNumber: string, adminNotes?: string) {
  return adminReturnAction(returnNumber, "shipped", adminNotes);
}

export async function markOrderReturnDelivered(returnNumber: string, adminNotes?: string) {
  return adminReturnAction(returnNumber, "delivered", adminNotes);
}

export async function completeOrderReturnExchange(returnNumber: string, adminNotes?: string) {
  return adminReturnAction(returnNumber, "complete", adminNotes);
}

export async function refundOrderReturn(returnNumber: string, adminNotes?: string) {
  return adminReturnAction(returnNumber, "refund", adminNotes);
}

export type AdminCodOrderRow = {
  id: number;
  order_number: string;
  total: number;
  cod_fee: number;
  status: string;
  placed_at: string;
  full_name?: string;
  phone?: string;
  city?: string;
};

export type AdminOrderRow = AdminCodOrderRow & {
  tracking_status?: string;
  payment_status: string;
  payment_method: string;
  zoho_sync_status: string;
  zoho_sync_error: string;
  zoho_last_synced_at: string;
  zoho_sales_order_id: string;
  zoho_invoice_id: string;
  zoho_sync_eligible: boolean;
  ledgerly_sync_status: string;
  ledgerly_sync_error: string;
  ledgerly_last_synced_at: string;
  ledgerly_sales_order_id: string;
  ledgerly_invoice_id: string;
  ledgerly_sync_eligible: boolean;
};

export type AdminOrderList = {
  orders: AdminOrderRow[];
  page: number;
  pageSize: number;
  totalItems: number;
  totalPages: number;
};

export type AdminOrderListFilters = {
  page?: number;
  size?: number;
  q?: string;
  status?: string;
  paymentStatus?: string;
  paymentMethod?: string;
  zohoSyncStatus?: string;
  ledgerlySyncStatus?: string;
};

export async function listAdminOrders(
  from?: string,
  to?: string,
  options?: AdminOrderListFilters,
): Promise<AdminOrderList> {
  const query = new URLSearchParams();
  if (from) query.set("from", from);
  if (to) query.set("to", to);
  const q = options?.q?.trim();
  if (q) query.set("q", q);
  if (options?.status) query.set("status", options.status);
  if (options?.paymentStatus) query.set("paymentStatus", options.paymentStatus);
  if (options?.paymentMethod) query.set("paymentMethod", options.paymentMethod);
  if (options?.zohoSyncStatus) query.set("zohoSyncStatus", options.zohoSyncStatus);
  if (options?.ledgerlySyncStatus) query.set("ledgerlySyncStatus", options.ledgerlySyncStatus);
  query.set("page", String(options?.page ?? 1));
  query.set("size", String(options?.size ?? LIST_PAGE_SIZE));
  const res = await api.get<Partial<AdminOrderList> & { orders?: AdminOrderRow[] }>(
    `/api/v1/admin/orders?${query.toString()}`,
    {
      auth: true,
    },
  );
  const orders = res.orders ?? [];
  const pageSize = res.pageSize ?? options?.size ?? LIST_PAGE_SIZE;
  const totalItems = res.totalItems ?? orders.length;
  return {
    orders,
    page: res.page ?? options?.page ?? 1,
    pageSize,
    totalItems,
    totalPages: Math.max(
      1,
      res.totalPages ?? Math.ceil(totalItems / Math.max(1, pageSize)),
    ),
  };
}

export async function listCodPendingOrders(): Promise<{ orders: AdminCodOrderRow[] }> {
  const res = await api.get<{ orders: AdminCodOrderRow[] }>("/api/v1/admin/orders/cod-pending", {
    auth: true,
  });
  return { orders: res.orders ?? [] };
}

export async function markCodCollected(orderNumber: string) {
  return api.post(
    `/api/v1/admin/orders/${encodeURIComponent(orderNumber)}/mark-cod-collected`,
    undefined,
    { auth: true },
  );
}
