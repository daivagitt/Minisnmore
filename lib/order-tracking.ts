import { CheckCircle2, Home, Package, Truck, type LucideIcon } from "lucide-react";

export const TRACKING_STAGES = [
  { key: "pending", icon: CheckCircle2, label: "Order placed" },
  { key: "processing", icon: Package, label: "Hand-wrapped" },
  { key: "shipped", icon: Truck, label: "On the way" },
  { key: "delivered", icon: Home, label: "Delivered" },
] as const;

const STATUS_INDEX: Record<string, number> = {
  pending: 0,
  confirmed: 1,
  processing: 1,
  shipped: 2,
  delivered: 3,
  fulfilled: 3,
  cancelled: -1,
};

export type OrderTrackingFields = {
  status?: string;
  tracking_status?: string;
  tracking_number?: string;
  tracking_carrier?: string;
  shipment_date?: string;
};

export function resolveTrackingStage(order: OrderTrackingFields) {
  const tracking = order.tracking_status?.toLowerCase();
  if (tracking && tracking in STATUS_INDEX) {
    return STATUS_INDEX[tracking];
  }
  return STATUS_INDEX[String(order.status ?? "").toLowerCase()] ?? 0;
}

export function trackingStatusLabel(order: OrderTrackingFields) {
  return (order.tracking_status ?? order.status ?? "pending").replace(/_/g, " ");
}

const TERMINAL_TRACKING_STATUSES = new Set(["delivered", "fulfilled", "cancelled", "canceled"]);

export function isTerminalTrackingState(order: OrderTrackingFields | null | undefined): boolean {
  if (!order) return false;
  const tracking = String(order.tracking_status ?? "").toLowerCase();
  const status = String(order.status ?? "").toLowerCase();
  return TERMINAL_TRACKING_STATUSES.has(tracking) || TERMINAL_TRACKING_STATUSES.has(status);
}

export function shouldPollOrderTracking(
  order: (OrderTrackingFields & { payment_status?: string }) | null | undefined,
): boolean {
  if (!order) return false;
  if (order.payment_status && order.payment_status !== "paid") return false;
  return !isTerminalTrackingState(order);
}

export function trackingStageIcon(key: string): LucideIcon {
  return TRACKING_STAGES.find((s) => s.key === key)?.icon ?? Package;
}
