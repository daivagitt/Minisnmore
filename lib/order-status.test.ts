import { describe, expect, it } from "vitest";
import {
  formatOrderStatusLabel,
  formatOrderStatusSummary,
  formatPaymentStatusLabel,
  isCodOrder,
} from "./order-status";

describe("order-status labels", () => {
  it("uses COD-friendly payment label while payment is still pending", () => {
    expect(formatPaymentStatusLabel("pending", "cod")).toBe("Pay on delivery");
    expect(formatPaymentStatusLabel("pending", "online")).toBe("Payment pending");
    expect(formatPaymentStatusLabel("paid", "cod")).toBe("Paid");
  });

  it("maps order status to customer-facing labels", () => {
    expect(formatOrderStatusLabel("confirmed")).toBe("Order confirmed");
    expect(formatOrderStatusLabel("hand_wrapped")).toBe("Hand-wrapped");
    expect(formatOrderStatusLabel("pending")).toBe("Awaiting payment");
    expect(formatOrderStatusLabel("fulfilled")).toBe("Delivered");
  });

  it("summarises COD orders as pay-on-delivery plus confirmed order", () => {
    expect(
      formatOrderStatusSummary({
        payment_status: "pending",
        payment_method: "cod",
        status: "confirmed",
      }),
    ).toBe("Pay on delivery · Order confirmed");
  });

  it("collapses duplicate pending labels for online checkout", () => {
    expect(
      formatOrderStatusSummary({
        payment_status: "pending",
        payment_method: "online",
        status: "pending",
      }),
    ).toBe("Payment pending");
  });

  it("renders cancelled orders as terminal regardless of their old payment state", () => {
    expect(
      formatOrderStatusSummary({
        payment_status: "pending",
        payment_method: "cod",
        status: "cancelled",
      }),
    ).toBe("Cancelled");
    expect(
      formatOrderStatusSummary({
        payment_status: "refunded",
        payment_method: "online",
        status: "cancelled",
      }),
    ).toBe("Settled · Cancelled");
  });

  it("uses tracking_status for fulfillment when present", () => {
    expect(
      formatOrderStatusSummary({
        payment_status: "paid",
        payment_method: "online",
        status: "fulfilled",
        tracking_status: "hand_wrapped",
      }),
    ).toBe("Paid · Hand-wrapped");
  });

  it("detects COD orders", () => {
    expect(isCodOrder({ payment_method: "cod" })).toBe(true);
    expect(isCodOrder({ payment_method: "online" })).toBe(false);
  });
});
