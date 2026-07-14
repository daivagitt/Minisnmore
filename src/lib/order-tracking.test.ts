import { describe, expect, it } from "vitest";
import {
  isTerminalTrackingState,
  resolveTrackingStage,
  shouldPollOrderTracking,
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

  it("prefers tracking_status for stage resolution", () => {
    expect(resolveTrackingStage({ status: "confirmed", tracking_status: "shipped" })).toBe(2);
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
