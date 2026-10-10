import { CheckCircle2, Clock3, Home, MapPin, Package, Truck, type LucideIcon } from "lucide-react";
import { needsOnlinePayment } from "@/lib/order-status";

export type TrackingStageState = "current" | "done" | "upcoming";

export const TRACKING_STAGES = [
  {
    key: "pending",
    icon: CheckCircle2,
    label: "Order placed",
    detail: {
      current: "We've received your order and are confirming it.",
      done: "Order received.",
      upcoming: "Waiting for your order.",
    },
  },
  {
    key: "processing",
    icon: Package,
    label: "Hand-wrapped",
    detail: {
      current:
        "Your order has been hand-wrapped with tissue, twine, and a tiny note — it's waiting for dispatch.",
      done: "Hand-wrapped with care.",
      upcoming: "We'll hand-wrap yours with tissue, twine, and a tiny note before dispatch.",
    },
  },
  {
    key: "awaiting_pickup",
    icon: Clock3,
    label: "Waiting for pickup",
    detail: {
      current: "The shipping label is ready. We'll mark it on the way when the courier collects the parcel.",
      done: "Collected by the courier.",
      upcoming: "After packing, we'll prepare the shipping label and wait for courier collection.",
    },
  },
  {
    key: "shipped",
    icon: Truck,
    label: "On the way",
    detail: {
      current: "Your parcel is with the courier — tracking updates appear as they move.",
      done: "Handed to the courier.",
      upcoming: "Tracking appears once we dispatch.",
    },
  },
  {
    key: "out_for_delivery",
    icon: MapPin,
    label: "Out for delivery",
    detail: {
      current: "Your parcel is out for delivery today — please keep your phone handy.",
      done: "Out for delivery.",
      upcoming: "The courier will take it out for delivery soon.",
    },
  },
  {
    key: "delivered",
    icon: Home,
    label: "Delivered",
    detail: {
      current: "It's with you — enjoy the little moments.",
      done: "Delivered to your door.",
      upcoming: "Almost there.",
    },
  },
] as const;

/**
 * Confirmed orders stay at stage 0 until a package is created for the Zoho sales order.
 * Waiting for pickup is a distinct stage after hand-wrapping and before physical dispatch.
 */
const STATUS_INDEX: Record<string, number> = {
  pending: 0,
  confirmed: 0,
  processing: 1,
  hand_wrapped: 1,
  wrapped: 1,
  awaiting_pickup: 2,
  carrier_cancelled: 2,
  shipped: 3,
  out_for_delivery: 4,
  delivery_failed: 4,
  delivered: 5,
  fulfilled: 5,
  cancelled: -1,
};

export type OrderTrackingFields = {
  status?: string;
  tracking_status?: string;
  tracking_number?: string;
  tracking_carrier?: string;
  tracking_url?: string;
  shipment_date?: string;
  shipment_delivered_at?: string;
  payment_status?: string;
  payment_method?: string;
};

export function resolveTrackingStage(order: OrderTrackingFields) {
  const tracking = order.tracking_status?.toLowerCase();
  if (tracking && tracking in STATUS_INDEX) {
    return STATUS_INDEX[tracking];
  }
  return STATUS_INDEX[String(order.status ?? "").toLowerCase()] ?? 0;
}

function normalizedTrackingKey(order: OrderTrackingFields): string {
  const tracking = (order.tracking_status ?? order.status ?? "pending").toLowerCase();
  if (tracking === "fulfilled") return "delivered";
  if (tracking === "wrapped") return "hand_wrapped";
  return tracking;
}

function isHandWrapped(order: OrderTrackingFields): boolean {
  const key = normalizedTrackingKey(order);
  return key === "processing" || key === "hand_wrapped";
}

export function trackingStatusLabel(order: OrderTrackingFields) {
  const key = normalizedTrackingKey(order);
  if (key === "cancelled" || key === "canceled") return "Cancelled";
  if (key === "confirmed") return "Order confirmed";
  if (key === "awaiting_pickup") return "Waiting for pickup";
  if (key === "carrier_cancelled") return "ExpressBees cancelled";
  if (key === "delivery_failed") return "Delivery attempt unsuccessful";
  if (isHandWrapped(order)) return "Hand-wrapped";
  const stage = TRACKING_STAGES.find((s) => s.key === key);
  if (stage) return stage.label;
  return (order.tracking_status ?? order.status ?? "pending").replace(/_/g, " ");
}

/** Label for a timeline row — stage 0 becomes "Order confirmed" after payment. */
export function trackingStageLabel(order: OrderTrackingFields, stageKey: string): string {
  const key = normalizedTrackingKey(order);
  if (
    stageKey === "pending" &&
    (key === "confirmed" || isHandWrapped(order) || key === "awaiting_pickup" || key === "carrier_cancelled")
  ) {
    return "Order confirmed";
  }
  return TRACKING_STAGES.find((s) => s.key === stageKey)?.label ?? stageKey;
}

export function trackingStageDetail(
  order: OrderTrackingFields,
  stageKey: string,
  state: TrackingStageState,
): string {
  const key = normalizedTrackingKey(order);
  if (stageKey === "pending" && (key === "confirmed" || isHandWrapped(order))) {
    if (state === "current") {
      return "Your order is confirmed — we'll hand-wrap it with care before dispatch (usually 1–3 business days).";
    }
    if (state === "done") return "Order confirmed.";
  }
  const stage = TRACKING_STAGES.find((s) => s.key === stageKey);
  if (stageKey === "awaiting_pickup" && key === "carrier_cancelled" && state === "current") {
    return "The courier cancelled this shipment booking. We are arranging a new pickup.";
  }
  if (stageKey === "out_for_delivery" && key === "delivery_failed" && state === "current") {
    return "Delivery attempt was unsuccessful. A reattempt is scheduled by the courier.";
  }
  if (stage) return stage.detail[state];
  if (state === "done") return "Completed";
  if (state === "current") return "In progress";
  return "Upcoming";
}

const TERMINAL_TRACKING_STATUSES = new Set(["delivered", "fulfilled", "cancelled", "canceled"]);

export function isTerminalTrackingState(order: OrderTrackingFields | null | undefined): boolean {
  if (!order) return false;
  const tracking = String(order.tracking_status ?? "").toLowerCase();
  const status = String(order.status ?? "").toLowerCase();
  return TERMINAL_TRACKING_STATUSES.has(tracking) || TERMINAL_TRACKING_STATUSES.has(status);
}

export function shouldShowOrderTrackingLink(
  order: (OrderTrackingFields & { payment_status?: string }) | null | undefined,
): boolean {
  if (!order) return false;
  if (order.payment_status && order.payment_status !== "paid") return false;
  if (String(order.tracking_status ?? "").toLowerCase() === "carrier_cancelled") return false;
  return !isTerminalTrackingState(order);
}

export function trackingStageIcon(key: string): LucideIcon {
  return TRACKING_STAGES.find((s) => s.key === key)?.icon ?? Package;
}

/** Customer-facing headline for order activity feeds (notifications, etc.). */
export function trackingActivityTitle(order: OrderTrackingFields): string {
  if (needsOnlinePayment(order)) return "Payment pending";
  const key = normalizedTrackingKey(order);
  if (key === "cancelled" || key === "canceled") return "Order cancelled";
  if (key === "confirmed") return "Order confirmed";
  if (key === "awaiting_pickup") return "Waiting for pickup";
  if (key === "carrier_cancelled") return "ExpressBees cancelled";
  if (key === "delivery_failed") return "Delivery attempt unsuccessful";
  if (isHandWrapped(order)) return "Hand-wrapped";
  const stage = TRACKING_STAGES.find((s) => s.key === key);
  if (stage) return stage.label;
  return "Order update";
}

export function trackingActivityIcon(order: OrderTrackingFields): LucideIcon {
  const key = normalizedTrackingKey(order);
  if (key === "cancelled" || key === "canceled") return Package;
  if (key === "carrier_cancelled") return trackingStageIcon("awaiting_pickup");
  if (isHandWrapped(order)) return Package;
  return trackingStageIcon(key);
}

export type TrackingActivityDatePrefix = "Ordered" | "Shipped" | "Payment pending";

/** Best available date string for activity UI — shipment date when in transit, else order placed. */
export function trackingActivityDate(order: OrderTrackingFields & { created_at?: string }): {
  prefix: TrackingActivityDatePrefix;
  value: string;
} {
  if (needsOnlinePayment(order)) {
    return { prefix: "Payment pending", value: order.created_at ?? "" };
  }
  const key = normalizedTrackingKey(order);
  const shipped = key === "shipped" || key === "out_for_delivery" || key === "delivery_failed" || key === "delivered";
  if (shipped && order.shipment_date) {
    return { prefix: "Shipped", value: order.shipment_date };
  }
  return { prefix: "Ordered", value: order.created_at ?? "" };
}
