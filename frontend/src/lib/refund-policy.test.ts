import { describe, expect, it } from "vitest";
import {
  DEFAULT_REFUND_MAX_BUSINESS_DAYS,
  DEFAULT_REFUND_MIN_BUSINESS_DAYS,
  DEFAULT_RETURN_WINDOW_DAYS,
  formatRefundBusinessDayRange,
  parseRefundMaxBusinessDays,
  parseRefundMinBusinessDays,
  parseReturnWindowDays,
  isOrderRefundSettled,
  refundGatewayStartedMessage,
  refundListLineMessage,
  refundProcessedMessage,
} from "./refund-policy";

describe("refund-policy", () => {
  it("parses return window and refund days within range", () => {
    expect(parseReturnWindowDays(7)).toBe(7);
    expect(parseReturnWindowDays("7")).toBe(7);
    expect(parseRefundMinBusinessDays(3)).toBe(3);
    expect(parseRefundMaxBusinessDays(4, 2)).toBe(4);
  });

  it("falls back for missing or out-of-range values", () => {
    expect(parseReturnWindowDays(undefined)).toBe(DEFAULT_RETURN_WINDOW_DAYS);
    expect(parseReturnWindowDays(0)).toBe(DEFAULT_RETURN_WINDOW_DAYS);
    expect(parseReturnWindowDays(999)).toBe(DEFAULT_RETURN_WINDOW_DAYS);
    expect(parseRefundMinBusinessDays(-1)).toBe(DEFAULT_REFUND_MIN_BUSINESS_DAYS);
    expect(parseRefundMaxBusinessDays(undefined, 3)).toBe(Math.max(3, DEFAULT_REFUND_MAX_BUSINESS_DAYS));
  });

  it("formats a customer-facing refund timeline", () => {
    expect(formatRefundBusinessDayRange(2, 5)).toBe("2–5 business days");
    expect(formatRefundBusinessDayRange(3, 3)).toBe("3 business days");
    expect(formatRefundBusinessDayRange(1, 1)).toBe("1 business day");
    expect(refundProcessedMessage(2, 5)).toContain("2–5 business days");
    expect(refundProcessedMessage(2, 5)).toContain("original payment source");
    expect(refundGatewayStartedMessage()).not.toContain("business day");
  });

  it("shows the bank timeline only after the gateway has processed the refund", () => {
    expect(isOrderRefundSettled("refunded")).toBe(true);
    expect(isOrderRefundSettled("REFUNDED")).toBe(true);
    expect(isOrderRefundSettled("partially_refunded")).toBe(true);
    expect(isOrderRefundSettled("refund_initiated")).toBe(false);
    expect(refundListLineMessage(2, 5, "refunded")).toMatch(/^Refund processed — /);
    expect(refundListLineMessage(2, 5, "refunded")).toContain("2–5 business days");
    expect(refundListLineMessage(2, 5, "refund_initiated")).toBe(refundGatewayStartedMessage());
    expect(refundProcessedMessage()).toBe(
      "It will take 2–5 business days to get refunded into your original payment source.",
    );
    expect(refundProcessedMessage(99, 99)).toBe(refundProcessedMessage());
  });
});
