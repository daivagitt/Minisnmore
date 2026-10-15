import {
  ClipboardList,
  Clock3,
  Home,
  Package,
  PackageCheck,
  Truck,
  Wallet,
  type LucideIcon,
} from "lucide-react";
import {
  parseRefundMaxBusinessDays,
  parseRefundMinBusinessDays,
  isOrderRefundSettled,
  refundGatewayStartedMessage,
  refundProcessedMessage,
} from "@/lib/refund-policy";

export type ReturnStageState = "current" | "done" | "upcoming";

export type RefundTimeline = {
  min?: number | null;
  max?: number | null;
};

function refundRange(timeline?: RefundTimeline) {
  const min = parseRefundMinBusinessDays(timeline?.min);
  const max = parseRefundMaxBusinessDays(timeline?.max, min);
  return { min, max };
}

function refundCaseLabel(item: ReturnTrackingFields): string {
  return isOrderRefundSettled(item.order_payment_status) ? "Refund processed" : "Refund initiated";
}

export type ReturnTrackingFields = {
  status?: string | null;
  request_type?: string | null;
  rejection_reason?: string | null;
  pickup_failure_reason?: string | null;
  reverse_awb?: string | null;
  reverse_carrier_status?: string | null;
  outbound_carrier_status?: string | null;
  tracking_url?: string | null;
  carrier_name?: string | null;
  pickup_window?: string | null;
  outbound_awb?: string | null;
  outbound_tracking_url?: string | null;
  outbound_carrier_name?: string | null;
  order_payment_status?: string | null;
};

type StageCopy = {
  key: string;
  icon: LucideIcon;
  label: string;
  detail: Record<ReturnStageState, string>;
};

/**
 * Customer stepper, shaped like Myntra / Amazon return tracking.
 * Stable step names stay in RETURN_STAGES / EXCHANGE_STAGES. The headline and the
 * current row use the ExpressBees milestone from reverse_carrier_status / outbound_carrier_status.
 */
export const RETURN_STAGES: readonly StageCopy[] = [
  {
    key: "request",
    icon: ClipboardList,
    label: "Requested",
    detail: {
      current: "We've received your return request and will review it shortly.",
      done: "Request approved.",
      upcoming: "Submit a return request to get started.",
    },
  },
  {
    key: "pickup",
    icon: Clock3,
    label: "Pickup",
    detail: {
      current: "A courier will collect the item from your address. Keep it packed and ready.",
      done: "Picked up.",
      upcoming: "We'll book a free pickup after approval.",
    },
  },
  {
    key: "transit",
    icon: Truck,
    label: "In transit",
    detail: {
      current: "Your parcel is on the way back to us.",
      done: "Received at our warehouse.",
      upcoming: "Tracking updates appear once the courier collects the parcel.",
    },
  },
  {
    key: "inspection",
    icon: PackageCheck,
    label: "Quality check",
    detail: {
      current: "We've received the item and are checking it.",
      done: "Quality check complete.",
      upcoming: "We'll check the item after it reaches us.",
    },
  },
  {
    key: "refunded",
    icon: Wallet,
    label: "Refund",
    detail: {
      current: "Your refund has been initiated.",
      done: "Refund processed.",
      upcoming: "Refunds start after the item passes the quality check.",
    },
  },
] as const;

export const EXCHANGE_STAGES: readonly StageCopy[] = [
  {
    key: "request",
    icon: ClipboardList,
    label: "Requested",
    detail: {
      current: "We've received your exchange request and will review it shortly.",
      done: "Request approved.",
      upcoming: "Submit an exchange request to get started.",
    },
  },
  {
    key: "pickup",
    icon: Clock3,
    label: "Pickup",
    detail: {
      current: "A courier will collect the original item. Keep it packed and ready.",
      done: "Picked up.",
      upcoming: "We'll book a free pickup after approval.",
    },
  },
  {
    key: "inspection",
    icon: PackageCheck,
    label: "Quality check",
    detail: {
      current: "We've received the original item and are checking it.",
      done: "Quality check complete.",
      upcoming: "We'll check the original item after it reaches us.",
    },
  },
  {
    key: "replacement",
    icon: Package,
    label: "Replacement",
    detail: {
      current: "We're packing your replacement and getting it ready to ship.",
      done: "Handed to the courier.",
      upcoming: "We'll pack the replacement after the quality check.",
    },
  },
  {
    key: "out_for_delivery",
    icon: Truck,
    label: "Out for delivery",
    detail: {
      current: "Your replacement is out for delivery today — please keep your phone handy.",
      done: "Out for delivery.",
      upcoming: "The courier will take it out for delivery soon.",
    },
  },
  {
    key: "delivered",
    icon: Home,
    label: "Delivered",
    detail: {
      current: "Your replacement is with you — enjoy the little moments.",
      done: "Replacement delivered.",
      upcoming: "Almost there.",
    },
  },
] as const;

const RETURN_STATUS_INDEX: Record<string, number> = {
  requested: 0,
  approved: 0,
  pickup_requested: 1,
  pickup_scheduled: 1,
  pickup_failed: 1,
  picked_up: 2,
  received: 3,
  inspected: 3,
  accepted: 3,
  refunded: 4,
  completed: 4,
};

const EXCHANGE_STATUS_INDEX: Record<string, number> = {
  requested: 0,
  approved: 0,
  pickup_requested: 1,
  pickup_scheduled: 1,
  pickup_failed: 1,
  picked_up: 1,
  received: 2,
  inspected: 2,
  accepted: 3,
  item_allocated: 3,
  repacking: 3,
  ready_to_ship: 3,
  shipped: 3,
  delivered: 5,
  completed: 5,
  refunded: 5,
};

const TERMINAL_CLOSED = new Set(["rejected", "cancelled", "canceled", "failed"]);
const PICKUP_WINDOW = new Set(["approved", "pickup_requested", "pickup_scheduled"]);

export function isExchangeRequest(item: ReturnTrackingFields): boolean {
  return String(item.request_type ?? "return").toLowerCase() === "exchange";
}

export function returnTrackingStages(item: ReturnTrackingFields) {
  return isExchangeRequest(item) ? EXCHANGE_STAGES : RETURN_STAGES;
}

function normalizedStatus(item: ReturnTrackingFields): string {
  return String(item.status ?? "requested").toLowerCase();
}

export function isClosedReturn(item: ReturnTrackingFields): boolean {
  const status = normalizedStatus(item);
  if (TERMINAL_CLOSED.has(status)) return true;
  if (status === "refunded" && isExchangeRequest(item)) return true;
  return false;
}

export function isRejectedReturn(item: ReturnTrackingFields): boolean {
  return normalizedStatus(item) === "rejected";
}

/** Blocks starting another return/exchange for the same order or line item on account order detail. */
const ORDER_UI_OPEN_RETURN_STATUSES = new Set([
  "requested",
  "approved",
  "pickup_requested",
  "pickup_scheduled",
  "pickup_failed",
  "picked_up",
  "received",
  "inspected",
  "accepted",
  "item_allocated",
  "repacking",
  "ready_to_ship",
  "shipped",
  "delivered",
  "failed",
]);

export function isOpenReturnForOrderUi(item: ReturnTrackingFields): boolean {
  return ORDER_UI_OPEN_RETURN_STATUSES.has(normalizedStatus(item));
}

function carrier(value?: string | null): string {
  return String(value ?? "")
    .trim()
    .toLowerCase()
    .replace(/[\s-]+/g, "_");
}

type Display = { idx: number; label: string };

function closedDisplay(item: ReturnTrackingFields): Display | null {
  const status = normalizedStatus(item);
  if (status === "rejected") return { idx: -1, label: "Declined" };
  if (status === "cancelled" || status === "canceled") return { idx: -1, label: "Cancelled" };
  if (status === "failed") return { idx: -1, label: "Refund failed" };
  return null;
}

/** One resolver: workflow status + ExpressBees reverse/outbound milestones. */
function resolveDisplay(item: ReturnTrackingFields): Display {
  const closed = closedDisplay(item);
  if (closed) return closed;
  const status = normalizedStatus(item);
  const exchange = isExchangeRequest(item);
  const map = exchange ? EXCHANGE_STATUS_INDEX : RETURN_STATUS_INDEX;
  if (!(status in map)) return { idx: -1, label: "Status update" };

  const reverse = carrier(item.reverse_carrier_status);
  const outbound = carrier(item.outbound_carrier_status);
  let idx = map[status];
  let label = returnTrackingStages(item)[idx]?.label ?? status.replace(/_/g, " ");

  if (status === "requested") label = "Request received";
  if (status === "approved") label = "Approved";
  if (status === "pickup_failed" || reverse === "pickup_failed") {
    idx = 1;
    label = "Pickup unsuccessful";
  } else if (reverse === "out_for_pickup" && PICKUP_WINDOW.has(status)) {
    idx = 1;
    label = "Out for pickup";
  } else if (status === "pickup_scheduled" || status === "pickup_requested") {
    label = "Pickup scheduled";
  } else if (status === "picked_up") {
    if (exchange) {
      idx = 1;
      label = "Picked up";
    } else {
      idx = 2;
      label = reverse === "in_transit" ? "In transit" : "Picked up";
    }
  } else if (status === "received" || status === "inspected") {
    label = "Quality check";
  } else if (!exchange && status === "accepted") {
    label = "Quality check complete";
  } else if (!exchange && (status === "refunded" || status === "completed")) {
    label = refundCaseLabel(item);
  } else if (status === "accepted" || status === "item_allocated" || status === "repacking") {
    label = "Preparing replacement";
  } else if (status === "ready_to_ship") {
    label = outbound === "awaiting_pickup" || item.outbound_awb ? "Waiting for pickup" : "Preparing replacement";
  } else if (status === "shipped") {
    if (outbound === "out_for_delivery" || outbound === "ofd") {
      idx = 4;
      label = "Out for delivery";
    } else if (outbound === "delivery_failed") {
      idx = 4;
      label = "Delivery attempt unsuccessful";
    } else {
      label = "Replacement on the way";
    }
  } else if (status === "delivered" || status === "completed") {
    label = "Delivered";
  } else if (status === "refunded" && exchange) {
    label = refundCaseLabel(item);
  }

  return { idx, label };
}

export function resolveReturnStage(item: ReturnTrackingFields): number {
  return resolveDisplay(item).idx;
}

export function returnStatusLabel(item: ReturnTrackingFields): string {
  return resolveDisplay(item).label;
}

export function returnStageLabel(item: ReturnTrackingFields, stageKey: string): string {
  const stages = returnTrackingStages(item);
  const stageIndex = stages.findIndex((stage) => stage.key === stageKey);
  const current = resolveDisplay(item);
  if (stageIndex >= 0 && stageIndex === current.idx) {
    return current.label;
  }
  return stages[stageIndex]?.label ?? stageKey;
}

export function returnStageDetail(
  item: ReturnTrackingFields,
  stageKey: string,
  state: ReturnStageState,
  timeline?: RefundTimeline,
): string {
  const status = normalizedStatus(item);
  const reverse = carrier(item.reverse_carrier_status);
  const outbound = carrier(item.outbound_carrier_status);
  if (stageKey === "request" && status === "approved" && state === "current") {
    const kind = isExchangeRequest(item) ? "exchange" : "return";
    return `Your ${kind} is approved. We'll arrange a free pickup next.`;
  }
  if (stageKey === "pickup" && (status === "pickup_failed" || reverse === "pickup_failed") && state === "current") {
    return item.pickup_failure_reason?.trim()
      ? item.pickup_failure_reason
      : "Pickup could not be completed. We'll arrange another attempt.";
  }
  if (stageKey === "pickup" && reverse === "out_for_pickup" && state === "current") {
    return "The pickup partner is on the way to you. Keep the parcel packed and ready.";
  }
  if (stageKey === "pickup" && status === "picked_up" && state === "current") {
    return reverse === "in_transit"
      ? "Your parcel is on the way back to us."
      : "The courier has collected your parcel.";
  }
  if (stageKey === "transit" && status === "picked_up" && reverse !== "in_transit" && state === "current") {
    return "The courier has collected your parcel.";
  }
  if (stageKey === "inspection" && status === "accepted" && state === "current" && !isExchangeRequest(item)) {
    return "The item passed the quality check. Your refund will start next.";
  }
  if (stageKey === "refunded") {
    const range = refundRange(timeline);
    const processed = isOrderRefundSettled(item.order_payment_status);
    if (state === "current") {
      return processed ? refundProcessedMessage(range.min, range.max) : refundGatewayStartedMessage();
    }
    if (state === "done") return processed ? "Refund processed." : "Refund initiated.";
  }
  if (stageKey === "replacement" && status === "accepted" && state === "current") {
    return "The original item passed the quality check. We're preparing your replacement.";
  }
  if (stageKey === "replacement" && state === "current" && returnStatusLabel(item) === "Waiting for pickup") {
    return "The shipping label is ready. We'll update this when the courier collects the replacement.";
  }
  if (stageKey === "replacement" && status === "shipped" && state === "current") {
    return "Your replacement is with the courier — tracking updates appear as it moves.";
  }
  if (stageKey === "out_for_delivery" && outbound === "delivery_failed" && state === "current") {
    return "Delivery attempt was unsuccessful. A reattempt is scheduled by the courier.";
  }
  const stage = returnTrackingStages(item).find((row) => row.key === stageKey);
  if (stage) return stage.detail[state];
  if (state === "done") return "Completed";
  if (state === "current") return "In progress";
  return "Upcoming";
}

export function closedReturnMessage(item: ReturnTrackingFields, timeline?: RefundTimeline): string {
  const status = normalizedStatus(item);
  const kind = isExchangeRequest(item) ? "exchange" : "return";
  if (status === "rejected") {
    const reason = item.rejection_reason?.trim();
    return reason
      ? `This ${kind} request was declined. Reason: ${reason}`
      : `This ${kind} request was declined.`;
  }
  if (status === "cancelled" || status === "canceled") {
    return `This ${kind} request was cancelled.`;
  }
  if (status === "failed") {
    return `The refund for this ${kind} could not be completed. Our team will retry it.`;
  }
  if (status === "refunded" && kind === "exchange") {
    if (!isOrderRefundSettled(item.order_payment_status)) {
      return "This exchange was completed as a refund. A refund has been initiated to your original payment method.";
    }
    const range = refundRange(timeline);
    return `This exchange was completed as a refund. ${refundProcessedMessage(range.min, range.max)}`;
  }
  return `This ${kind} is no longer in progress.`;
}

export function hasReturnShipmentDetails(item: ReturnTrackingFields): boolean {
  return Boolean(
    item.reverse_awb ||
      item.tracking_url ||
      item.carrier_name ||
      item.pickup_window ||
      item.outbound_awb ||
      item.outbound_tracking_url ||
      item.outbound_carrier_name,
  );
}

const DEFAULT_PICKUP_TRACKING_URL = "https://shipmentv2.xpressbees.com/orders/tracking/{awb}";

function isXpressBeesCarrier(item: ReturnTrackingFields): boolean {
  const carrier = String(item.carrier_name ?? "")
    .trim()
    .toLowerCase();
  return carrier.includes("xpressbees") || carrier.includes("expressbees");
}

/** Pickup tracking link: stored URL, or ExpressBees page when the carrier is ExpressBees. */
export function resolvePickupTrackingUrl(item: ReturnTrackingFields): string | null {
  const stored = item.tracking_url?.trim();
  if (stored) return stored;
  const awb = item.reverse_awb?.trim();
  if (!awb || !isXpressBeesCarrier(item)) return null;
  return DEFAULT_PICKUP_TRACKING_URL.replace("{awb}", encodeURIComponent(awb));
}
