import { describe, expect, it } from "vitest";
import {
  isTerminalTrackingState,
  resolveTrackingStage,
  shouldPollOrderTracking,
  trackingActivityDate,
  trackingActivityTitle,
  trackingStageDetail,
  trackingStageLabel,
  trackingStatusLabel,
} from "@/lib/order-tracking";

describe("order-tracking helpers", () => {
  it("detects terminal delivery states", () => {
    expect(isTerminalTrackingState({ status: "fulfilled" })).toBe(true);
    expect(isTerminalTrackingState({ tracking_status: "delivered" })).toBe(true);
    expect(isTerminalTrackingState({ status: "cancelled" })).toBe(true);
    expect(isTerminalTrackingState({ status: "confirmed" })).toBe(false);
  });

  it("polls only paid, in-transit orders", () => {
    expect(shouldPollOrderTracking(null)).toBe(false);
    expect(shouldPollOrderTracking({ payment_status: "pending", status: "confirmed" })).toBe(false);
    expect(shouldPollOrderTracking({ payment_status: "paid", status: "confirmed" })).toBe(true);
    expect(shouldPollOrderTracking({ payment_status: "paid", status: "fulfilled" })).toBe(false);
  });

  it("does not auto-advance Hand-wrapped on payment confirmation", () => {
    expect(resolveTrackingStage({ status: "confirmed", tracking_status: "processing" })).toBe(0);
    expect(resolveTrackingStage({ status: "confirmed" })).toBe(0);
    expect(trackingStatusLabel({ tracking_status: "processing" })).toBe("Order confirmed");
    expect(trackingStageLabel({ tracking_status: "processing" }, "pending")).toBe("Order confirmed");
    expect(trackingStageLabel({ tracking_status: "processing" }, "processing")).toBe("Hand-wrapped");
  });

  it("marks Hand-wrapped complete only once shipped", () => {
    expect(resolveTrackingStage({ tracking_status: "shipped" })).toBe(2);
    expect(resolveTrackingStage({ status: "confirmed", tracking_status: "shipped" })).toBe(2);
  });

  it("builds activity titles from tracking_status", () => {
    expect(trackingActivityTitle({ tracking_status: "processing" })).toBe("Order confirmed");
    expect(trackingActivityTitle({ tracking_status: "shipped" })).toBe("On the way");
    expect(trackingActivityTitle({ tracking_status: "delivered" })).toBe("Delivered");
    expect(trackingActivityTitle({ status: "cancelled" })).toBe("Order cancelled");
  });

  it("keeps hand-wrapped as upcoming copy while confirmed", () => {
    const order = { tracking_status: "processing" };
    expect(trackingStageDetail(order, "processing", "upcoming")).toMatch(/hand-wrap/i);
    expect(trackingStageDetail(order, "pending", "current")).toMatch(/confirmed/i);
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
        tracking_status: "processing",
        created_at: "2026-08-01T10:00:00Z",
      }),
    ).toEqual({ prefix: "Ordered", value: "2026-08-01T10:00:00Z" });
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
