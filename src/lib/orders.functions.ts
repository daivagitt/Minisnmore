import { api } from "@/lib/api";
import { LIST_PAGE_SIZE } from "@/lib/pagination";
import type { FulfillmentAddress } from "@/lib/fulfillment-docs";
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

export async function cancelOrder(orderNumber: string, reason: string): Promise<{ order: any; cancellation_request?: any }> {
  const parsed = OrderNumberInput.parse({ orderNumber });
  return api.post(
    `/api/v1/orders/${encodeURIComponent(parsed.orderNumber)}/cancel-request`,
    { reason },
    { auth: true },
  );
}

export async function withdrawCancelRequest(orderNumber: string): Promise<{ order: any; cancellation_request?: any }> {
  const parsed = OrderNumberInput.parse({ orderNumber });
  return api.post(
    `/api/v1/orders/${encodeURIComponent(parsed.orderNumber)}/cancel-request/withdraw`,
    {},
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

export type AccountProfile = { full_name: string; phone: string; subscribed: boolean };

export async function getMyProfile(): Promise<{ profile: AccountProfile }> {
  const profile = await api.get("/api/v1/account/profile", { auth: true });
  const full_name = (profile as any)?.full_name ?? "";
  const phone = (profile as any)?.phone ?? "";
  const subscribed = (profile as any)?.subscribed === true;
  return { profile: { full_name, phone, subscribed } };
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
  subscribed: z.boolean().optional(),
});

export async function updateMyProfile(input: {
  data: z.infer<typeof ProfileUpdate>;
}): Promise<{ ok: boolean }> {
  const parsed = ProfileUpdate.parse(input.data);
  return api.put(
    "/api/v1/account/profile",
    {
      full_name: parsed.full_name,
      phone: parsed.phone,
      ...(parsed.subscribed !== undefined ? { subscribed: parsed.subscribed } : {}),
    },
    { auth: true },
  );
}

const ProfileBootstrap = z.object({
  full_name: FullNameSchema,
  email: z.string().trim().email().max(160),
  subscribed: z.boolean().optional(),
});

export async function bootstrapProfileFromSignup(input: {
  data: z.infer<typeof ProfileBootstrap>;
}): Promise<{ ok: boolean }> {
  const parsed = ProfileBootstrap.parse(input.data);
  return api.post(
    "/api/v1/account/profile/bootstrap",
    {
      full_name: parsed.full_name,
      email: parsed.email.toLowerCase(),
      ...(parsed.subscribed ? { subscribed: true } : {}),
    },
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
  order_shipping?: number | null;
  order_cod_fee?: number | null;
  order_payment_status?: string | null;
  order_item_id?: number;
  product_name?: string;
  product_slug?: string;
  product_image?: string | null;
  original_size?: string | null;
  original_color?: string | null;
  sku?: string | null;
  variant_sku?: string | null;
  replacement_size?: string | null;
  replacement_color?: string | null;
  original_item_amount?: number | null;
  replacement_item_amount?: number | null;
  price_difference?: number | null;
  settlement_status?: string | null;
  outbound_awb?: string | null;
  outbound_tracking_url?: string | null;
  outbound_carrier_name?: string | null;
  reverse_awb?: string | null;
  tracking_url?: string | null;
  carrier_name?: string | null;
  pickup_window?: string | null;
  pickup_failure_reason?: string | null;
  reverse_carrier_status?: string | null;
  outbound_carrier_status?: string | null;
  tracking_status?: string;
  shipment_delivered_at?: string | null;
  items?: OrderReturnLine[];
  image_urls?: string[];
};

export type OrderReturnLine = {
  id: number;
  order_item_id: number;
  product_name: string;
  product_image?: string | null;
  size?: string | null;
  color?: string | null;
  sku?: string | null;
  variant_sku?: string | null;
  requested_qty: number;
  received_qty?: number | null;
  accepted_qty?: number | null;
  unit_amount: number;
  gross_amount: number;
  discount_allocation: number;
  refund_amount: number;
  disposition?: string | null;
};

export async function listMyOrderReturns(): Promise<{ returns: OrderReturnRow[] }> {
  const returns = await api.get<OrderReturnRow[]>("/api/v1/order-returns", { auth: true });
  return { returns: returns ?? [] };
}

export type ReturnEligibility = {
  eligible: boolean;
  message?: string;
  order_number: string;
  tracking_status?: string;
  delivered_at?: string | null;
  return_deadline?: string | null;
  window_days?: number;
  refund_min_business_days?: number;
  refund_max_business_days?: number;
  enabled?: boolean;
};

export async function checkReturnEligibility(orderNumber: string): Promise<ReturnEligibility> {
  return api.get(`/api/v1/order-returns/eligibility/${encodeURIComponent(orderNumber)}`, {
    auth: true,
  });
}

export async function getReturnsConfig(): Promise<{
  enabled: boolean;
  window_days: number;
  refund_min_business_days?: number;
  refund_max_business_days?: number;
}> {
  return api.get("/api/v1/order-returns/config", { auth: true });
}

const returnImageUrlSchema = z.string().trim().url().max(600);

const CreateReturnInput = z.object({
  order_number: z.string().min(3).max(40),
  reason: z.string().min(10).max(4000),
  items: z
    .array(
      z.object({
        order_item_id: z.number().int().positive(),
        quantity: z.number().int().positive(),
      }),
    )
    .min(1)
    .optional(),
  image_urls: z.array(returnImageUrlSchema).max(5).optional(),
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
  image_urls: z.array(returnImageUrlSchema).max(5).optional(),
});

export async function requestOrderExchange(input: {
  data: z.infer<typeof CreateExchangeInput>;
}): Promise<OrderReturnRow> {
  const parsed = CreateExchangeInput.parse(input.data);
  return api.post("/api/v1/order-returns/exchange", parsed, { auth: true });
}

export const MAX_RETURN_IMAGES = 5;

const ReturnImageUploadResponse = z.object({
  publicUrl: z.string().url(),
});

export async function uploadReturnImage(file: File): Promise<string> {
  const form = new FormData();
  const filename =
    file.name?.trim() ||
    (file.type === "image/png"
      ? "return.png"
      : file.type === "image/webp"
        ? "return.webp"
        : file.type === "image/gif"
          ? "return.gif"
          : "return.jpg");
  form.append("file", file, filename);
  const uploaded = await api.post<unknown>("/api/v1/order-returns/uploads", form, { auth: true });
  return ReturnImageUploadResponse.parse(uploaded).publicUrl;
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
  disposition?: string | null;
  logistics_mode?: string | null;
  carrier_name?: string | null;
  reverse_awb?: string | null;
  tracking_url?: string | null;
  pickup_window?: string | null;
  pickup_failure_reason?: string | null;
  outbound_logistics_mode?: string | null;
  outbound_carrier_name?: string | null;
  outbound_awb?: string | null;
  outbound_tracking_url?: string | null;
  outbound_label_url?: string | null;
  outbound_provider_request_id?: string | null;
  settlement_method?: string | null;
  settlement_reference?: string | null;
  settlement_amount?: number | null;
  settled_at?: string | null;
  settled_by?: string | null;
  can_razorpay_refund_price_difference?: boolean;
  replacement_reserved_at?: string | null;
  payment_method?: string | null;
  payment_status?: string | null;
  shipment_date?: string | null;
  cod_refund_pending?: boolean;
  manual_refund_method?: string | null;
  manual_refund_reference?: string | null;
  manual_refund_recorded_at?: string | null;
  manual_refund_recorded_by?: string | null;
  case_age_hours?: number;
  sla_due_at?: string | null;
  sla_overdue?: boolean;
  sla_limit_hours?: number;
  packed_at?: string | null;
  package_slip_number?: string | null;
  shipment_order_number?: string | null;
  shipping_address?: FulfillmentAddress;
  dispatch_address?: FulfillmentAddress;
  can_complete_package?: boolean;
  can_book_outbound?: boolean;
  default_package_weight_grams?: number | null;
  package_weight_grams?: number | null;
  package_length_cm?: number | null;
  package_breadth_cm?: number | null;
  package_height_cm?: number | null;
  package_chargeable_weight_grams?: number | null;
  package_courier_id?: string | null;
  package_courier_name?: string | null;
  package_type?: "custom" | "box" | "envelope" | string | null;
};

export type AdminOrderReturnsStageCounts = {
  request: number;
  pickup: number;
  inspection: number;
  exchange: number;
  closed: number;
};

export type AdminOrderReturnsSummary = {
  total: number;
  returns: number;
  exchanges: number;
  open: number;
  closed: number;
  overdue: number;
  xpressbeesReverseReady: number;
  stages: AdminOrderReturnsStageCounts;
};

export const EMPTY_ADMIN_RETURNS_SUMMARY: AdminOrderReturnsSummary = {
  total: 0,
  returns: 0,
  exchanges: 0,
  open: 0,
  closed: 0,
  overdue: 0,
  xpressbeesReverseReady: 0,
  stages: { request: 0, pickup: 0, inspection: 0, exchange: 0, closed: 0 },
};

function numberOrZero(value: unknown): number {
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
}

export function normalizeAdminOrderReturnsSummary(
  raw?: Partial<AdminOrderReturnsSummary> | null,
): AdminOrderReturnsSummary {
  const stages = raw?.stages ?? EMPTY_ADMIN_RETURNS_SUMMARY.stages;
  const reverseReadyRaw = raw as (Partial<AdminOrderReturnsSummary> & {
    xpressbees_reverse_ready?: unknown;
  }) | null | undefined;
  return {
    total: numberOrZero(raw?.total),
    returns: numberOrZero(raw?.returns),
    exchanges: numberOrZero(raw?.exchanges),
    open: numberOrZero(raw?.open),
    closed: numberOrZero(raw?.closed),
    overdue: numberOrZero(raw?.overdue),
    xpressbeesReverseReady: numberOrZero(
      reverseReadyRaw?.xpressbeesReverseReady ?? reverseReadyRaw?.xpressbees_reverse_ready,
    ),
    stages: {
      request: numberOrZero(stages.request),
      pickup: numberOrZero(stages.pickup),
      inspection: numberOrZero(stages.inspection),
      exchange: numberOrZero(stages.exchange),
      closed: numberOrZero(stages.closed),
    },
  };
}

export async function listAdminOrderReturns(
  status?: string,
): Promise<{ returns: AdminOrderReturnRow[] }> {
  const query = status ? `?status=${encodeURIComponent(status)}` : "";
  const returns = await api.get<AdminOrderReturnRow[]>(`/api/v1/admin/order-returns${query}`, {
    auth: true,
  });
  return { returns: returns ?? [] };
}

export async function getAdminOrderReturnsSummary(): Promise<AdminOrderReturnsSummary> {
  const data = await api.get<Partial<AdminOrderReturnsSummary> | null>(
    "/api/v1/admin/order-returns/summary",
    { auth: true },
  );
  return normalizeAdminOrderReturnsSummary(data);
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

export async function recordOrderReturnCodRefund(
  returnNumber: string,
  payload: {
    refund_method: "upi" | "bank_transfer" | "cash" | "other";
    refund_reference: string;
    admin_notes?: string;
  },
) {
  return api.post(
    `/api/v1/admin/order-returns/${encodeURIComponent(returnNumber)}/record-cod-refund`,
    payload,
    { auth: true },
  );
}

export async function bookOrderReturnPickup(
  returnNumber: string,
  payload: {
    mode: "manual" | "self_ship" | "xpressbees";
    carrier_name?: string;
    reverse_awb?: string;
    tracking_url?: string;
    pickup_window?: string;
    admin_notes?: string;
  },
) {
  return api.post(
    `/api/v1/admin/order-returns/${encodeURIComponent(returnNumber)}/book-pickup`,
    payload,
    { auth: true },
  );
}

export async function bookOrderReturnOutbound(
  returnNumber: string,
  payload: {
    mode: "manual" | "xpressbees";
    carrier_name?: string;
    outbound_awb?: string;
    tracking_url?: string;
    label_url?: string;
    courier_id?: string;
    courier_name?: string;
    package_type?: "custom" | "box" | "envelope";
    admin_notes?: string;
  },
) {
  return api.post(
    `/api/v1/admin/order-returns/${encodeURIComponent(returnNumber)}/book-outbound`,
    payload,
    { auth: true },
  );
}

export async function previewOrderReturnPackageRates(
  returnNumber: string,
  payload: {
    weight_grams?: number;
    length_cm: number;
    breadth_cm: number;
    height_cm: number;
  },
) {
  return api.post<PackageRatesResult>(
    `/api/v1/admin/order-returns/${encodeURIComponent(returnNumber)}/package-rates`,
    payload,
    { auth: true },
  );
}

export async function completeOrderReturnPackage(
  returnNumber: string,
  payload: {
    weight_grams?: number;
    length_cm: number;
    breadth_cm: number;
    height_cm: number;
    courier_id?: string;
    courier_name?: string;
    package_type?: "custom" | "box" | "envelope";
  },
) {
  return api.post(
    `/api/v1/admin/order-returns/${encodeURIComponent(returnNumber)}/complete-package`,
    payload,
    { auth: true },
  );
}

export async function markOrderReturnPickupFailed(
  returnNumber: string,
  failureReason: string,
  adminNotes?: string,
) {
  return api.post(
    `/api/v1/admin/order-returns/${encodeURIComponent(returnNumber)}/pickup-failed`,
    { failure_reason: failureReason, admin_notes: adminNotes ?? "" },
    { auth: true },
  );
}

export async function rescheduleOrderReturnPickup(returnNumber: string, adminNotes?: string) {
  return api.post(
    `/api/v1/admin/order-returns/${encodeURIComponent(returnNumber)}/reschedule-pickup`,
    { admin_notes: adminNotes ?? "" },
    { auth: true },
  );
}

export async function closeFailedOrderReturnPickup(returnNumber: string, adminNotes?: string) {
  return adminReturnAction(returnNumber, "close-pickup-failed", adminNotes);
}

export async function acceptOrderReturnAfterInspection(
  returnNumber: string,
  disposition: string,
  adminNotes?: string,
  items?: Array<{ return_item_id: number; accepted_quantity: number; disposition: string }>,
) {
  return api.post(
    `/api/v1/admin/order-returns/${encodeURIComponent(returnNumber)}/accept`,
    { disposition, admin_notes: adminNotes ?? "", items: items ?? [] },
    { auth: true },
  );
}

export async function settleOrderReturnPriceDifference(
  returnNumber: string,
  payload: {
    status: "collected" | "refunded" | "waived";
    amount: number;
    method?: string;
    reference?: string;
    admin_notes?: string;
    force_manual?: boolean;
  },
) {
  return api.post(
    `/api/v1/admin/order-returns/${encodeURIComponent(returnNumber)}/settle-price-difference`,
    payload,
    { auth: true },
  );
}

export type AdminCancellationRequestRow = {
  id: number;
  request_number: string;
  status: string;
  reason: string;
  rejection_reason?: string | null;
  admin_notes?: string | null;
  requested_at: string;
  resolved_at?: string | null;
  order_id?: number;
  order_number?: string;
  order_total?: number;
  customer_login?: string | null;
  payment_method?: string | null;
  payment_status?: string | null;
  cod_refund_pending?: boolean;
  refund_amount?: number | null;
  manual_refund_method?: string | null;
  manual_refund_reference?: string | null;
  manual_refund_recorded_at?: string | null;
  manual_refund_recorded_by?: string | null;
  case_age_hours?: number;
  sla_due_at?: string | null;
  sla_overdue?: boolean;
  sla_limit_hours?: number;
};

export async function listAdminCancellationRequests(
  status?: string,
): Promise<{ requests: AdminCancellationRequestRow[] }> {
  const query = status ? `?status=${encodeURIComponent(status)}` : "";
  const requests = await api.get<AdminCancellationRequestRow[]>(
    `/api/v1/admin/cancellation-requests${query}`,
    { auth: true },
  );
  return { requests: requests ?? [] };
}

export async function approveCancellationRequest(requestNumber: string, adminNotes?: string) {
  return api.post(
    `/api/v1/admin/cancellation-requests/${encodeURIComponent(requestNumber)}/approve`,
    { admin_notes: adminNotes ?? "" },
    { auth: true },
  );
}

export async function rejectCancellationRequest(
  requestNumber: string,
  rejectionReason: string,
  adminNotes?: string,
) {
  return api.post(
    `/api/v1/admin/cancellation-requests/${encodeURIComponent(requestNumber)}/reject`,
    { rejection_reason: rejectionReason, admin_notes: adminNotes ?? "" },
    { auth: true },
  );
}

export async function retryCancellationRefund(requestNumber: string) {
  return api.post(
    `/api/v1/admin/cancellation-requests/${encodeURIComponent(requestNumber)}/retry-refund`,
    {},
    { auth: true },
  );
}

export async function recordCancellationCodRefund(
  requestNumber: string,
  payload: {
    refund_method: "upi" | "bank_transfer" | "cash" | "other";
    refund_reference: string;
    admin_notes?: string;
  },
) {
  return api.post(
    `/api/v1/admin/cancellation-requests/${encodeURIComponent(requestNumber)}/record-cod-refund`,
    payload,
    { auth: true },
  );
}

export type AdminCodOrderRow = {
  id: number;
  order_number: string;
  total: number;
  cod_fee: number;
  status: string;
  placed_at: string;
  coupon_code?: string | null;
  full_name?: string;
  phone?: string;
  city?: string;
};

export type AdminOrderItem = {
  id?: number;
  product_id?: string | number;
  product_slug?: string;
  product_name?: string;
  product_image?: string;
  size?: string;
  color?: string;
  sku?: string;
  qty?: number;
  price?: number;
  mrp?: number;
};

export type AdminOrderRow = AdminCodOrderRow & {
  tracking_status?: string;
  tracking_number?: string;
  tracking_carrier?: string;
  tracking_url?: string;
  shipment_date?: string;
  shipment_label_url?: string;
  shipment_logistics_mode?: string;
  shipment_carrier_status?: string;
  shipment_delivered_at?: string;
  pending_cancellation?: boolean;
  can_cancel?: boolean;
  can_admin_cancel?: boolean;
  can_book_shipment?: boolean;
  can_book_shipment_with_override?: boolean;
  can_book_expressbees_shipment?: boolean;
  can_book_expressbees_shipment_with_override?: boolean;
  can_rebook_carrier_cancelled_shipment?: boolean;
  can_complete_package?: boolean;
  default_package_weight_grams?: number | null;
  package_weight_grams?: number | null;
  package_length_cm?: number | null;
  package_breadth_cm?: number | null;
  package_height_cm?: number | null;
  package_chargeable_weight_grams?: number | null;
  package_courier_id?: string;
  package_courier_name?: string;
  package_type?: "custom" | "box" | "envelope" | string;
  packed_at?: string;
  package_slip_number?: string;
  shipment_order_number?: string;
  shipping_address?: FulfillmentAddress;
  dispatch_address?: FulfillmentAddress;
  payment_status: string;
  payment_method: string;
  zoho_sync_status: string;
  zoho_sync_error: string;
  zoho_last_synced_at: string;
  zoho_sales_order_id: string;
  zoho_invoice_id: string;
  zoho_credit_note_id?: string;
  zoho_sync_eligible: boolean;
  ledgerly_sync_status: string;
  ledgerly_sync_error: string;
  ledgerly_last_synced_at: string;
  ledgerly_sales_order_id: string;
  ledgerly_invoice_id: string;
  ledgerly_sync_eligible: boolean;
  order_items?: AdminOrderItem[];
};

export type AdminOrderList = {
  orders: AdminOrderRow[];
  page: number;
  pageSize: number;
  totalItems: number;
  totalPages: number;
  express_cancellation_count: number;
};

export type AdminOrderListFilters = {
  page?: number;
  size?: number;
  q?: string;
  status?: string;
  trackingStatus?: string;
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
  if (options?.trackingStatus) query.set("trackingStatus", options.trackingStatus);
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
    express_cancellation_count: Number(res.express_cancellation_count ?? 0),
  };
}

export async function listCodPendingOrders(): Promise<{ orders: AdminCodOrderRow[] }> {
  const res = await api.get<{ orders: AdminCodOrderRow[] }>("/api/v1/admin/orders/cod-pending", {
    auth: true,
  });
  return { orders: res.orders ?? [] };
}

/** Lowercased logins with at least one placed (non-cancelled, non-pending) order. */
export async function listOrderedCustomerLogins(): Promise<string[]> {
  const res = await api.get<{ logins?: string[] }>("/api/v1/admin/orders/ordered-logins", {
    auth: true,
  });
  return (res.logins ?? [])
    .map((login) => String(login ?? "").trim().toLowerCase())
    .filter(Boolean);
}

export async function markCodCollected(orderNumber: string) {
  return api.post(
    `/api/v1/admin/orders/${encodeURIComponent(orderNumber)}/mark-cod-collected`,
    undefined,
    { auth: true },
  );
}

export async function bookAdminOrderShipment(
  orderNumber: string,
  payload: {
    mode: "manual" | "xpressbees";
    carrier_name?: string;
    awb?: string;
    tracking_url?: string;
    label_url?: string;
    override_fulfillment_hold?: boolean;
    override_reason?: string;
    courier_id?: string;
    courier_name?: string;
    package_type?: "custom" | "box" | "envelope";
  },
) {
  return api.post(
    `/api/v1/admin/orders/${encodeURIComponent(orderNumber)}/book-shipment`,
    payload,
    { auth: true },
  );
}

export type PackageCourierOption = {
  id: string;
  name: string;
  edd_days?: number;
  eta?: string;
  rate?: number | string | null;
  preferred?: boolean;
};

export type PackageRatesResult = {
  actual_weight_grams: number;
  volumetric_weight_grams: number;
  chargeable_weight_grams: number;
  volumetric_divisor?: number;
  preferred_courier_id?: string | null;
  preferred_courier_name?: string;
  couriers: PackageCourierOption[];
  rates_warning?: string;
};

export async function previewAdminOrderPackageRates(
  orderNumber: string,
  payload: {
    weight_grams?: number;
    length_cm: number;
    breadth_cm: number;
    height_cm: number;
  },
) {
  return api.post<PackageRatesResult>(
    `/api/v1/admin/orders/${encodeURIComponent(orderNumber)}/package-rates`,
    payload,
    { auth: true },
  );
}

export async function completeAdminOrderPackage(
  orderNumber: string,
  payload: {
    weight_grams?: number;
    length_cm: number;
    breadth_cm: number;
    height_cm: number;
    courier_id?: string;
    courier_name?: string;
    package_type?: "custom" | "box" | "envelope";
  },
) {
  return api.post(
    `/api/v1/admin/orders/${encodeURIComponent(orderNumber)}/complete-package`,
    payload,
    { auth: true },
  );
}

export async function cancelAdminOrderShipment(
  orderNumber: string,
  payload: { mode: "manual" | "xpressbees"; reason: string },
) {
  return api.post(
    `/api/v1/admin/orders/${encodeURIComponent(orderNumber)}/cancel-shipment`,
    payload,
    { auth: true },
  );
}

export async function cancelAdminOrder(
  orderNumber: string,
  payload: { reason: string; shipment_mode?: "manual" | "xpressbees" },
) {
  return api.post(
    `/api/v1/admin/orders/${encodeURIComponent(orderNumber)}/cancel`,
    payload,
    { auth: true },
  );
}
