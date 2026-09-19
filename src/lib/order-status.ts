export type OrderStatusFields = {
  payment_status?: string | null;
  payment_method?: string | null;
  status?: string | null;
  tracking_status?: string | null;
};

export function isCodOrder(order: OrderStatusFields): boolean {
  return order.payment_method?.trim().toLowerCase() === "cod";
}

/** Online checkout started but Razorpay (or similar) payment not completed yet. */
export function needsOnlinePayment(order: OrderStatusFields): boolean {
  const status = (order.payment_status ?? "").trim().toLowerCase();
  const orderStatus = (order.status ?? "").trim().toLowerCase();
  const cancelled = orderStatus === "cancelled" || orderStatus === "canceled";
  const pendingByLegacyStatus = !status && orderStatus === "pending";
  return !cancelled && !isCodOrder(order) && (status === "pending" || pendingByLegacyStatus);
}

export function formatPaymentStatusLabel(paymentStatus?: string | null, paymentMethod?: string | null): string {
  const status = (paymentStatus ?? "").toLowerCase();
  const cod = paymentMethod?.toLowerCase() === "cod";

  if (status === "pending" && cod) return "Pay on delivery";
  if (status === "pending") return "Payment pending";
  if (status === "paid") return "Paid";
  if (status === "failed") return "Payment failed";
  if (status === "refunded") return "Settled";
  return paymentStatus ?? "";
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

function formatFulfillmentLabel(order: OrderStatusFields): string {
  const tracking = (order.tracking_status ?? "").toLowerCase();
  if (tracking === "confirmed") return "Order confirmed";
  if (tracking === "processing" || tracking === "hand_wrapped" || tracking === "wrapped") {
    return "Hand-wrapped";
  }
  if (tracking === "shipped") return "On the way";
  if (tracking === "awaiting_pickup") return "Waiting for pickup";
  if (tracking === "delivered") return "Delivered";
  if (tracking === "cancelled" || tracking === "canceled") return "Cancelled";
  return formatOrderStatusLabel(order.status);
}

/** Customer-facing summary for badges and order cards (payment · fulfillment). */
export function formatOrderStatusSummary(order: OrderStatusFields): string {
  const paymentLabel = formatPaymentStatusLabel(order.payment_status, order.payment_method);
  const statusLabel = formatFulfillmentLabel(order);
  const paymentStatus = (order.payment_status ?? "").toLowerCase();
  const orderStatus = (order.status ?? "").toLowerCase();

  if (orderStatus === "cancelled" || orderStatus === "canceled") {
    return paymentStatus === "refunded" ? "Settled · Cancelled" : "Cancelled";
  }
  if (paymentStatus === "pending" && orderStatus === "pending") return paymentLabel;
  return `${paymentLabel} · ${statusLabel}`;
}

export function paymentStatusTone(paymentStatus?: string | null): "paid" | "pending" | "failed" | "muted" {
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
