import { describe, expect, it } from "vitest";
import {
  TRACKING_STAGES,
  isTerminalTrackingState,
  resolveTrackingStage,
  shouldShowOrderTrackingLink,
  trackingActivityDate,
  trackingActivityTitle,
  trackingStageDetail,
  trackingStageLabel,
  trackingStatusLabel,
} from "@/lib/order-tracking";

describe("order-tracking helpers", () => {
  it("renders the complete six-stage delivery timeline in order", () => {
    expect(TRACKING_STAGES.map((stage) => stage.key)).toEqual([
      "pending",
      "processing",
      "awaiting_pickup",
      "shipped",
      "out_for_delivery",
      "delivered",
    ]);
  });

  it("detects terminal delivery states", () => {
    expect(isTerminalTrackingState({ status: "fulfilled" })).toBe(true);
    expect(isTerminalTrackingState({ tracking_status: "delivered" })).toBe(true);
    expect(isTerminalTrackingState({ status: "cancelled" })).toBe(true);
    expect(isTerminalTrackingState({ status: "confirmed" })).toBe(false);
  });

  it("shows tracking links only for paid, in-transit orders", () => {
    expect(shouldShowOrderTrackingLink(null)).toBe(false);
    expect(shouldShowOrderTrackingLink({ payment_status: "pending", status: "confirmed" })).toBe(false);
    expect(shouldShowOrderTrackingLink({ payment_status: "paid", status: "confirmed" })).toBe(true);
    expect(shouldShowOrderTrackingLink({ payment_status: "paid", status: "fulfilled" })).toBe(false);
    expect(
      shouldShowOrderTrackingLink({
        payment_status: "paid",
        status: "confirmed",
        tracking_status: "carrier_cancelled",
      }),
    ).toBe(false);
  });

  it("keeps confirmed separate from the explicit Hand-wrapped state", () => {
    expect(resolveTrackingStage({ status: "confirmed", tracking_status: "confirmed" })).toBe(0);
    expect(resolveTrackingStage({ status: "confirmed" })).toBe(0);
    expect(trackingStatusLabel({ tracking_status: "confirmed" })).toBe("Order confirmed");
    expect(resolveTrackingStage({ status: "hand_wrapped", tracking_status: "hand_wrapped" })).toBe(1);
    expect(trackingStatusLabel({ tracking_status: "hand_wrapped" })).toBe("Hand-wrapped");
    expect(trackingStageLabel({ tracking_status: "hand_wrapped" }, "pending")).toBe("Order confirmed");
    expect(trackingStageLabel({ tracking_status: "hand_wrapped" }, "processing")).toBe("Hand-wrapped");
  });

  it("maps ExpressBees carrier cancellation to awaiting pickup with a distinct label", () => {
    expect(resolveTrackingStage({ tracking_status: "carrier_cancelled" })).toBe(2);
    expect(trackingStatusLabel({ tracking_status: "carrier_cancelled" })).toBe("ExpressBees cancelled");
    expect(trackingActivityTitle({ tracking_status: "carrier_cancelled" })).toBe("ExpressBees cancelled");
    expect(
      trackingStageDetail({ tracking_status: "carrier_cancelled" }, "awaiting_pickup", "current"),
    ).toMatch(/cancelled this shipment booking/i);
    expect(isTerminalTrackingState({ tracking_status: "carrier_cancelled" })).toBe(false);
  });

  it("shows Waiting for pickup as a distinct stage before shipping", () => {
    expect(resolveTrackingStage({ tracking_status: "shipped" })).toBe(3);
    expect(resolveTrackingStage({ status: "confirmed", tracking_status: "shipped" })).toBe(3);
    expect(resolveTrackingStage({ tracking_status: "awaiting_pickup" })).toBe(2);
    expect(trackingStatusLabel({ tracking_status: "awaiting_pickup" })).toBe("Waiting for pickup");
    expect(trackingStageLabel({ tracking_status: "confirmed" }, "awaiting_pickup")).toBe("Waiting for pickup");
    expect(trackingStageDetail({ tracking_status: "awaiting_pickup" }, "awaiting_pickup", "current")).toMatch(
      /courier collects/i,
    );
  });

  it("maps ExpressBees OUT FOR DELIVERY and Delivered to their own stages", () => {
    expect(resolveTrackingStage({ tracking_status: "out_for_delivery" })).toBe(4);
    expect(trackingStatusLabel({ tracking_status: "out_for_delivery" })).toBe("Out for delivery");
    expect(resolveTrackingStage({ tracking_status: "delivered" })).toBe(5);
    expect(trackingStatusLabel({ tracking_status: "delivered" })).toBe("Delivered");
    expect(isTerminalTrackingState({ tracking_status: "out_for_delivery" })).toBe(false);
    expect(
      trackingActivityDate({ tracking_status: "out_for_delivery", shipment_date: "2026-08-10", created_at: "2026-08-01" }),
    ).toEqual({ prefix: "Shipped", value: "2026-08-10" });
  });

  it("maps delivery_failed to out_for_delivery stage with custom label and detail", () => {
    expect(resolveTrackingStage({ tracking_status: "delivery_failed" })).toBe(4);
    expect(trackingStatusLabel({ tracking_status: "delivery_failed" })).toBe("Delivery attempt unsuccessful");
    expect(trackingActivityTitle({ tracking_status: "delivery_failed" })).toBe("Delivery attempt unsuccessful");
    expect(isTerminalTrackingState({ tracking_status: "delivery_failed" })).toBe(false);
    expect(
      trackingActivityDate({ tracking_status: "delivery_failed", shipment_date: "2026-08-10", created_at: "2026-08-01" }),
    ).toEqual({ prefix: "Shipped", value: "2026-08-10" });
  });

  it("builds activity titles from tracking_status", () => {
    expect(trackingActivityTitle({ tracking_status: "confirmed" })).toBe("Order confirmed");
    expect(trackingActivityTitle({ tracking_status: "hand_wrapped" })).toBe("Hand-wrapped");
    expect(trackingActivityTitle({ tracking_status: "awaiting_pickup" })).toBe("Waiting for pickup");
    expect(trackingActivityTitle({ tracking_status: "shipped" })).toBe("On the way");
    expect(trackingActivityTitle({ tracking_status: "delivered" })).toBe("Delivered");
    expect(trackingActivityTitle({ status: "cancelled" })).toBe("Order cancelled");
    expect(
      trackingActivityTitle({
        status: "pending",
        payment_status: "pending",
        payment_method: "online",
      }),
    ).toBe("Payment pending");
  });

  it("shows hand-wrapped as current only after the backend transition", () => {
    const order = { tracking_status: "hand_wrapped" };
    expect(trackingStageDetail(order, "processing", "current")).toMatch(/has been hand-wrapped/i);
    expect(trackingStageDetail(order, "pending", "done")).toMatch(/confirmed/i);
  });

  it("prefers shipment date for in-transit activity", () => {
    expect(
      trackingActivityDate({
        tracking_status: "shipped",
        shipment_date: "2026-08-10",
        created_at: "2026-08-01T10:00:00Z",
      }),
    ).toEqual({ prefix: "Shipped", value: "2026-08-10" });

    expect(
      trackingActivityDate({
        tracking_status: "delivered",
        shipment_date: "2026-08-10",
        shipment_delivered_at: "2026-08-12T09:57:19Z",
        created_at: "2026-08-01T10:00:00Z",
      }),
    ).toEqual({ prefix: "Shipped", value: "2026-08-10" });

    expect(
      trackingActivityDate({
        tracking_status: "hand_wrapped",
        created_at: "2026-08-01T10:00:00Z",
      }),
    ).toEqual({ prefix: "Ordered", value: "2026-08-01T10:00:00Z" });

    expect(
      trackingActivityDate({
        status: "pending",
        payment_status: "pending",
        payment_method: "online",
        created_at: "2026-08-01T10:00:00Z",
      }),
    ).toEqual({ prefix: "Payment pending", value: "2026-08-01T10:00:00Z" });
  });
});

describe("formatOrderDate", () => {
  it("returns em dash for invalid values", async () => {
    const { formatOrderDate } = await import("@/lib/dates");
    expect(formatOrderDate(null)).toBe("—");
    expect(formatOrderDate("not-a-date")).toBe("—");
  });

  it("formats datetime when time options are provided", async () => {
    const { formatOrderDate } = await import("@/lib/dates");
    const formatted = formatOrderDate("2026-07-13T07:36:40.750Z", {
      day: "numeric",
      month: "short",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
    expect(formatted).toMatch(/13/);
    expect(formatted).toMatch(/2026/);
  });
});
