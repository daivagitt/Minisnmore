export type OrderStatusFields = {
  payment_status?: string | null;
  payment_method?: string | null;
  status?: string | null;
  tracking_status?: string | null;
  packed_at?: string | null;
  can_cancel?: boolean;
  contact_care_to_cancel?: boolean;
  pending_cancellation?: boolean;
  has_failed_refund?: boolean;
};

function normalizedStatus(value?: string | null): string {
  return (value ?? "").trim().toLowerCase();
}

function isCancelledStatus(value?: string | null): boolean {
  const status = normalizedStatus(value);
  return status === "cancelled" || status === "canceled";
}

export function isCodOrder(order: OrderStatusFields): boolean {
  return order.payment_method?.trim().toLowerCase() === "cod";
}

export function isCancelledOrder(order?: OrderStatusFields | null): boolean {
  if (!order) return false;
  return isCancelledStatus(order.status);
}

function isRefundInProgressPayment(paymentStatus?: string | null): boolean {
  const status = normalizedStatus(paymentStatus);
  return status === "paid" || status === "refund_initiated";
}

/** Prepaid cancel has a gateway refund in flight — customer should see payment status only. */
export function isPrepaidCancellationCreditInitiated(order?: OrderStatusFields | null): boolean {
  if (!order || isCodOrder(order) || !isCancelledOrder(order)) return false;
  return isRefundInProgressPayment(order.payment_status);
}

/** Cancelled prepaid with refund activity — show payment label only (no fulfillment). */
export function isPrepaidCancelledPaymentOnlySummary(order?: OrderStatusFields | null): boolean {
  if (!order || isCodOrder(order) || !isCancelledOrder(order)) return false;
  const paymentStatus = normalizedStatus(order.payment_status);
  return (
    isRefundInProgressPayment(paymentStatus) ||
    paymentStatus === "refunded" ||
    paymentStatus === "partially_refunded" ||
    paymentStatus === "refund_failed"
  );
}

/** Cancelled with no money collected (pending, blank, or whitespace payment). Not COD-only. */
function isCancelledWithUncollectedPayment(order?: OrderStatusFields | null): boolean {
  if (!order || !isCancelledOrder(order) || isPrepaidCancelledPaymentOnlySummary(order)) return false;
  const payment = normalizedStatus(order.payment_status);
  return payment === "pending" || !payment;
}

/** COD cancelled before cash was collected — customer sees Cancelled, not Pay on delivery. */
export function isCodCancelledUncollected(order?: OrderStatusFields | null): boolean {
  if (!order || !isCodOrder(order) || !isCancelledOrder(order)) return false;
  if (isCancelledWithUncollectedPayment(order)) return true;
  return normalizedStatus(order.payment_status) === "failed";
}

/** First-leg COD cash collection is only allowed while the outbound order is still open. */
export function canMarkCodCollected(order?: OrderStatusFields | null): boolean {
  if (!order || !isCodOrder(order) || isCancelledOrder(order)) return false;
  return normalizedStatus(order.payment_status) === "pending";
}

export function shouldContactCareToCancel(order?: OrderStatusFields | null) {
  return order?.contact_care_to_cancel === true;
}

/** Online checkout started but Razorpay (or similar) payment not completed yet. */
export function needsOnlinePayment(order: OrderStatusFields): boolean {
  const status = (order.payment_status ?? "").trim().toLowerCase();
  const orderStatus = (order.status ?? "").trim().toLowerCase();
  const cancelled = orderStatus === "cancelled" || orderStatus === "canceled";
  const pendingByLegacyStatus = !status && orderStatus === "pending";
  return !cancelled && !isCodOrder(order) && (status === "pending" || pendingByLegacyStatus);
}

/** Online refund is in flight at Razorpay or already processed and waiting on the customer's bank. */
export function isOnlineRefundAwaitingBank(order?: OrderStatusFields | null): boolean {
  if (!order || isCodOrder(order)) return false;
  const status = normalizedStatus(order.payment_status);
  return status === "refund_initiated" || status === "refunded" || status === "partially_refunded";
}

export function isOnlineRefundFailed(order?: OrderStatusFields | null): boolean {
  if (!order || isCodOrder(order)) return false;
  return normalizedStatus(order.payment_status) === "refund_failed" || order.has_failed_refund === true;
}

export function isOnlineRefundProcessedAwaitingBank(order?: OrderStatusFields | null): boolean {
  if (!order || isCodOrder(order)) return false;
  const status = normalizedStatus(order.payment_status);
  return status === "refunded" || status === "partially_refunded";
}

export function formatPaymentStatusLabel(
  paymentStatus?: string | null,
  paymentMethod?: string | null,
  order?: Pick<OrderStatusFields, "status" | "tracking_status" | "has_failed_refund"> | null,
): string {
  if (normalizedStatus(paymentStatus) === "refund_failed" || order?.has_failed_refund) {
    return "Refund failed";
  }
  if (normalizedStatus(paymentStatus) === "refund_initiated") {
    return "Refund initiated";
  }
  if (
    isPrepaidCancellationCreditInitiated({
      payment_status: paymentStatus,
      payment_method: paymentMethod,
      status: order?.status,
      tracking_status: order?.tracking_status,
    })
  ) {
    return "Refund initiated";
  }
  const status = normalizedStatus(paymentStatus);
  const cod = normalizedStatus(paymentMethod) === "cod";

  if (status === "pending" && cod) return "Pay on delivery";
  if (status === "pending") return "Payment pending";
  if (status === "paid") return "Paid";
  if (status === "failed") return "Payment failed";
  if (status === "refunded") return "Refund processed";
  if (status === "partially_refunded") return "Partially refunded";
  return paymentStatus ?? "";
}

/** Ops/admin payment pill — gateway status only. Does not reuse customer cancel copy. */
export function formatAdminPaymentStatusLabel(paymentStatus?: string | null): string {
  const status = normalizedStatus(paymentStatus);
  if (status === "refund_initiated") return "Refund initiated";
  if (status === "refunded") return "Refund processed";
  if (status === "partially_refunded") return "Partially refunded";
  if (status === "refund_failed") return "Refund failed";
  return paymentStatus?.trim() || "pending";
}

/** Payment line on account order detail — includes cancelled-order wording. */
export function formatAccountOrderPaymentLabel(order: OrderStatusFields): string {
  if (isCancelledWithUncollectedPayment(order)) return "No payment due";
  return formatPaymentStatusLabel(order.payment_status, order.payment_method, order);
}

export function shouldShowContactCareBanner(
  order?: OrderStatusFields | null,
  options?: { m2PostOrderUi?: boolean },
): boolean {
  if (!order) return false;
  if (shouldContactCareToCancel(order)) return true;
  if (options?.m2PostOrderUi) return false;
  if (isCancelledOrder(order)) return false;
  if (order.pending_cancellation) return false;
  return Boolean(order.can_cancel);
}

/** Delivery tracking accordion on account order detail (hide when order is cancelled). */
export function shouldShowAccountDeliveryTracking(order?: OrderStatusFields | null): boolean {
  if (!order) return false;
  if (isCancelledOrder(order)) return false;
  return normalizedStatus(order.payment_status) === "paid";
}

export function formatOrderStatusLabel(status?: string | null): string {
  const normalized = (status ?? "").toLowerCase();

  if (normalized === "confirmed") return "Order confirmed";
  if (normalized === "hand_wrapped" || normalized === "wrapped") return "Hand-wrapped";
  if (normalized === "pending") return "Awaiting payment";
  if (normalized === "fulfilled") return "Delivered";
  if (normalized === "cancelled" || normalized === "canceled") return "Cancelled";
  return status ?? "";
}

/** Customer-facing summary for badges and order cards. */
export function formatOrderStatusSummary(order: OrderStatusFields): string {
  if (isCodCancelledUncollected(order)) return formatOrderStatusLabel(order.status);
  return formatPaymentStatusLabel(order.payment_status, order.payment_method, order);
}

/** Pill tone for the customer order-status summary. */
export function orderStatusSummaryTone(order: OrderStatusFields): ReturnType<typeof paymentStatusTone> {
  if (isCodCancelledUncollected(order)) return "muted";
  return paymentStatusTone(order.payment_status, order);
}

export function paymentStatusTone(
  paymentStatus?: string | null,
  order?: OrderStatusFields | null,
): "paid" | "pending" | "failed" | "muted" {
  if (
    normalizedStatus(paymentStatus ?? order?.payment_status) === "refund_failed" ||
    order?.has_failed_refund
  ) {
    return "failed";
  }
  if (normalizedStatus(paymentStatus ?? order?.payment_status) === "refund_initiated") {
    return "pending";
  }
  if (
    isPrepaidCancellationCreditInitiated({
      payment_status: paymentStatus ?? order?.payment_status,
      payment_method: order?.payment_method,
      status: order?.status,
    })
  ) {
    return "pending";
  }
  if (
    isOnlineRefundAwaitingBank({
      payment_status: paymentStatus ?? order?.payment_status,
      payment_method: order?.payment_method,
      status: order?.status,
    })
  ) {
    return "pending";
  }
  const status = (paymentStatus ?? "").toLowerCase();
  if (status === "paid") return "paid";
  if (status === "pending") return "pending";
  if (status === "failed") return "failed";
  return "muted";
}

export const PAYMENT_STATUS_TONE_CLASS: Record<ReturnType<typeof paymentStatusTone>, string> = {
  paid: "bg-primary/15 text-primary",
  pending: "bg-amber-500/15 text-amber-700",
  failed: "bg-destructive/15 text-destructive",
  muted: "bg-muted text-muted-foreground",
};
