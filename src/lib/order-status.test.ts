import { describe, expect, it } from "vitest";
import {
  formatOrderStatusLabel,
  formatOrderStatusSummary,
  formatPaymentStatusLabel,
  formatAdminPaymentStatusLabel,
  formatAccountOrderPaymentLabel,
  isCodOrder,
  isCancelledOrder,
  isOnlineRefundAwaitingBank,
  isCodCancelledUncollected,
  isPrepaidCancellationCreditInitiated,
  isPrepaidCancelledPaymentOnlySummary,
  orderStatusSummaryTone,
  paymentStatusTone,
  shouldContactCareToCancel,
  shouldShowAccountDeliveryTracking,
  shouldShowContactCareBanner,
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

  it("summarises COD orders with payment status only", () => {
    expect(
      formatOrderStatusSummary({
        payment_status: "pending",
        payment_method: "cod",
        status: "confirmed",
      }),
    ).toBe("Pay on delivery");
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

  it("renders cancelled COD with cash uncollected as Cancelled, not payment status", () => {
    expect(
      formatOrderStatusSummary({
        payment_status: "pending",
        payment_method: "cod",
        status: "cancelled",
      }),
    ).toBe("Cancelled");
    expect(
      formatOrderStatusSummary({
        payment_status: "pending",
        payment_method: "cod",
        status: "canceled",
      }),
    ).toBe("Cancelled");
    expect(
      orderStatusSummaryTone({
        payment_status: "pending",
        payment_method: "cod",
        status: "cancelled",
      }),
    ).toBe("muted");
    expect(
      isCodCancelledUncollected({
        payment_status: "pending",
        payment_method: "cod",
        status: "cancelled",
      }),
    ).toBe(true);
    expect(
      formatOrderStatusSummary({
        payment_status: "refunded",
        payment_method: "cod",
        status: "cancelled",
      }),
    ).toBe("Refund processed");
    expect(
      isCodCancelledUncollected({
        payment_status: "refunded",
        payment_method: "cod",
        status: "cancelled",
      }),
    ).toBe(false);
    expect(
      isCodCancelledUncollected({
        payment_status: "paid",
        payment_method: "online",
        status: "cancelled",
      }),
    ).toBe(false);
    expect(
      formatOrderStatusSummary({
        payment_status: null,
        payment_method: "cod",
        status: "cancelled",
      }),
    ).toBe("Cancelled");
    expect(
      isCodCancelledUncollected({
        payment_status: "   ",
        payment_method: "cod",
        status: "cancelled",
      }),
    ).toBe(true);
    expect(
      formatOrderStatusSummary({
        payment_status: "failed",
        payment_method: "cod",
        status: "cancelled",
      }),
    ).toBe("Cancelled");
    expect(
      formatOrderStatusSummary({
        payment_status: "pending",
        payment_method: "online",
        status: "cancelled",
      }),
    ).toBe("Payment pending");
  });

  it("shows refund initiated for cancelled prepaid orders until gateway refund completes", () => {
    expect(formatPaymentStatusLabel("paid", "online")).toBe("Paid");
    expect(formatPaymentStatusLabel("paid", "online", { status: "cancelled" })).toBe("Refund initiated");
    expect(
      formatOrderStatusSummary({
        payment_status: "partially_refunded",
        payment_method: "online",
        status: "cancelled",
      }),
    ).toBe("Partially refunded");
    expect(
      formatOrderStatusSummary({
        payment_status: "paid",
        payment_method: "online",
        status: "cancelled",
      }),
    ).toBe("Refund initiated");
    expect(
      formatOrderStatusSummary({
        payment_status: "refund_initiated",
        payment_method: "online",
        status: "cancelled",
      }),
    ).toBe("Refund initiated");
    expect(formatPaymentStatusLabel("refund_initiated", "online")).toBe("Refund initiated");
    expect(
      formatOrderStatusSummary({
        payment_status: "refund_initiated",
        payment_method: "online",
        status: "fulfilled",
      }),
    ).toBe("Refund initiated");
    expect(
      formatOrderStatusSummary({
        payment_status: "refunded",
        payment_method: "online",
        status: "cancelled",
        tracking_status: "fulfilled",
      }),
    ).toBe("Refund processed");
    expect(formatPaymentStatusLabel("refunded", "online", { status: "cancelled" })).toBe(
      "Refund processed",
    );
    expect(
      isPrepaidCancellationCreditInitiated({
        payment_status: "refunded",
        payment_method: "online",
        status: "cancelled",
      }),
    ).toBe(false);
    expect(
      formatOrderStatusSummary({
        payment_status: "paid",
        payment_method: "online",
        status: "fulfilled",
      }),
    ).toBe("Paid");
    expect(
      formatOrderStatusSummary({
        payment_status: "refunded",
        payment_method: "online",
        status: "fulfilled",
      }),
    ).toBe("Refund processed");
    expect(formatPaymentStatusLabel("refunded", "online")).toBe("Refund processed");
    expect(formatPaymentStatusLabel("refund_failed", "online")).toBe("Refund failed");
    expect(
      formatOrderStatusSummary({
        payment_status: "partially_refunded",
        payment_method: "online",
        status: "fulfilled",
        has_failed_refund: true,
      }),
    ).toBe("Refund failed");
    expect(paymentStatusTone("refund_failed", { payment_status: "refund_failed", payment_method: "online" })).toBe(
      "failed",
    );
    expect(formatPaymentStatusLabel("partially_refunded", "online")).toBe("Partially refunded");
    expect(
      isPrepaidCancelledPaymentOnlySummary({
        payment_status: "partially_refunded",
        payment_method: "online",
        status: "cancelled",
      }),
    ).toBe(true);
    expect(
      isOnlineRefundAwaitingBank({
        payment_status: "refunded",
        payment_method: "online",
        status: "fulfilled",
      }),
    ).toBe(true);
  });

  it("tones list pills for prepaid cancellation payment states", () => {
    const inProgress = {
      payment_status: "paid",
      payment_method: "online",
      status: "cancelled",
    };
    expect(paymentStatusTone("paid", inProgress)).toBe("pending");
    expect(
      paymentStatusTone("refund_initiated", {
        payment_status: "refund_initiated",
        payment_method: "online",
        status: "cancelled",
      }),
    ).toBe("pending");
    const processed = {
      payment_status: "refunded",
      payment_method: "online",
      status: "cancelled",
    };
    expect(paymentStatusTone("refunded", processed)).toBe("pending");
  });

  it("keeps ops payment pills as gateway status, not customer refund copy", () => {
    expect(formatAdminPaymentStatusLabel("refunded")).toBe("Refund processed");
    expect(formatAdminPaymentStatusLabel("refund_initiated")).toBe("Refund initiated");
    expect(formatAdminPaymentStatusLabel("partially_refunded")).toBe("Partially refunded");
    expect(formatAdminPaymentStatusLabel("refund_failed")).toBe("Refund failed");
    expect(formatAdminPaymentStatusLabel("paid")).toBe("paid");
    expect(formatAdminPaymentStatusLabel("pending")).toBe("pending");
    expect(formatAdminPaymentStatusLabel(null)).toBe("pending");
  });

  it("formats account order payment labels for cancelled orders", () => {
    expect(
      formatAccountOrderPaymentLabel({
        payment_status: "pending",
        payment_method: "online",
        status: "cancelled",
      }),
    ).toBe("No payment due");
    expect(
      formatAccountOrderPaymentLabel({
        payment_status: "paid",
        payment_method: "online",
        status: "cancelled",
      }),
    ).toBe("Refund initiated");
    expect(
      formatAccountOrderPaymentLabel({
        payment_status: "refund_initiated",
        payment_method: "online",
        status: "cancelled",
      }),
    ).toBe("Refund initiated");
    expect(
      formatAccountOrderPaymentLabel({
        payment_status: "refunded",
        payment_method: "online",
        status: "cancelled",
      }),
    ).toBe("Refund processed");
    expect(
      formatAccountOrderPaymentLabel({
        payment_status: "pending",
        payment_method: "cod",
        status: "cancelled",
      }),
    ).toBe("No payment due");
    expect(
      formatAccountOrderPaymentLabel({
        payment_status: "   ",
        payment_method: "online",
        status: "cancelled",
      }),
    ).toBe("No payment due");
  });

  it("shows contact-care banner when M2 UI is off but cancel is pending", () => {
    expect(
      shouldShowContactCareBanner(
        { status: "confirmed", can_cancel: true, pending_cancellation: false },
        { m2PostOrderUi: false },
      ),
    ).toBe(true);
    expect(
      shouldShowContactCareBanner(
        { status: "confirmed", pending_cancellation: true },
        { m2PostOrderUi: false },
      ),
    ).toBe(false);
    expect(
      shouldShowContactCareBanner(
        { status: "confirmed", can_cancel: true },
        { m2PostOrderUi: true },
      ),
    ).toBe(false);
  });

  it("hides account delivery tracking for cancelled prepaid paid orders", () => {
    expect(
      shouldShowAccountDeliveryTracking({
        payment_status: "paid",
        payment_method: "online",
        status: "cancelled",
      }),
    ).toBe(false);
    expect(
      shouldShowAccountDeliveryTracking({
        payment_status: "paid",
        payment_method: "online",
        status: "canceled",
      }),
    ).toBe(false);
    expect(
      shouldShowAccountDeliveryTracking({
        payment_status: "paid",
        payment_method: "online",
        status: "confirmed",
      }),
    ).toBe(true);
  });

  it("treats canceled spelling as cancelled for customer order state", () => {
    expect(isCancelledOrder({ status: "canceled" })).toBe(true);
  });

  it("does not include fulfillment in the customer status summary", () => {
    expect(
      formatOrderStatusSummary({
        payment_status: "paid",
        payment_method: "online",
        status: "fulfilled",
        tracking_status: "hand_wrapped",
      }),
    ).toBe("Paid");
    expect(
      formatOrderStatusSummary({
        payment_status: "pending",
        payment_method: "cod",
        status: "hand_wrapped",
        tracking_status: "out_for_delivery",
      }),
    ).toBe("Pay on delivery");
    expect(
      formatOrderStatusSummary({
        payment_status: "paid",
        status: "hand_wrapped",
        tracking_status: "delivery_failed",
      }),
    ).toBe("Paid");
  });

  it("detects COD orders", () => {
    expect(isCodOrder({ payment_method: "cod" })).toBe(true);
    expect(isCodOrder({ payment_method: "online" })).toBe(false);
  });

  it("prefers contact_care_to_cancel from the API when present", () => {
    expect(shouldContactCareToCancel({ contact_care_to_cancel: true, status: "confirmed" })).toBe(true);
    expect(shouldContactCareToCancel({ contact_care_to_cancel: false, status: "hand_wrapped" })).toBe(false);
  });

  it("asks packed orders to contact customer care instead of requesting cancellation", () => {
    expect(shouldContactCareToCancel({ status: "confirmed", can_cancel: true })).toBe(false);
    expect(shouldContactCareToCancel({ status: "hand_wrapped", can_cancel: false })).toBe(true);
    expect(shouldContactCareToCancel({ status: "confirmed", packed_at: "2026-09-30T00:00:00Z", can_cancel: false })).toBe(true);
    expect(shouldContactCareToCancel({ status: "hand_wrapped", pending_cancellation: true })).toBe(false);
    expect(shouldContactCareToCancel({ status: "fulfilled", tracking_status: "hand_wrapped" })).toBe(false);
    expect(shouldContactCareToCancel({ status: "cancelled", packed_at: "2026-09-30T00:00:00Z" })).toBe(false);
  });
});
