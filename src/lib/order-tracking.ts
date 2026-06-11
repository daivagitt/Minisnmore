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

export function trackingStageIcon(key: string): LucideIcon {
  return TRACKING_STAGES.find((s) => s.key === key)?.icon ?? Package;
}
