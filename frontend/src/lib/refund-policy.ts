/** Return window and refund credit timelines — loaded from site settings with safe defaults. */

export const DEFAULT_RETURN_WINDOW_DAYS = 3;
export const MAX_RETURN_WINDOW_DAYS = 90;
export const DEFAULT_REFUND_MIN_BUSINESS_DAYS = 2;
export const DEFAULT_REFUND_MAX_BUSINESS_DAYS = 5;
export const MAX_REFUND_BUSINESS_DAYS = 30;

export type RefundTimeline = {
  min?: number | null;
  max?: number | null;
};

function parseBoundedDays(v: unknown, fallback: number, min: number, max: number): number {
  const n = typeof v === "number" ? v : Number(v);
  if (!Number.isFinite(n) || n < min || n > max) return fallback;
  return Math.trunc(n);
}

export function parseReturnWindowDays(
  v: unknown,
  fallback: number = DEFAULT_RETURN_WINDOW_DAYS,
): number {
  return parseBoundedDays(v, fallback, 1, MAX_RETURN_WINDOW_DAYS);
}

export function parseRefundMinBusinessDays(
  v: unknown,
  fallback: number = DEFAULT_REFUND_MIN_BUSINESS_DAYS,
): number {
  return parseBoundedDays(v, fallback, 1, MAX_REFUND_BUSINESS_DAYS);
}

export function parseRefundMaxBusinessDays(
  v: unknown,
  min: number = DEFAULT_REFUND_MIN_BUSINESS_DAYS,
  fallback: number = DEFAULT_REFUND_MAX_BUSINESS_DAYS,
): number {
  const parsed = parseBoundedDays(v, fallback, 1, MAX_REFUND_BUSINESS_DAYS);
  return Math.max(min, parsed);
}

export function formatRefundBusinessDayRange(min: number, max: number): string {
  const low = Math.max(1, min);
  const high = Math.max(low, max);
  if (low === high) {
    return low === 1 ? "1 business day" : `${low} business days`;
  }
  return `${low}–${high} business days`;
}

export function refundGatewayStartedMessage(): string {
  return "A refund has been initiated to your original payment method.";
}

export function isOrderRefundSettled(paymentStatus: string | null | undefined): boolean {
  const status = paymentStatus?.trim().toLowerCase();
  return status === "refunded" || status === "partially_refunded";
}

export function refundProcessedMessage(min?: number | null, max?: number | null): string {
  const parsedMin = parseRefundMinBusinessDays(min);
  const parsedMax = parseRefundMaxBusinessDays(max, parsedMin);
  return `It will take ${formatRefundBusinessDayRange(parsedMin, parsedMax)} to get refunded into your original payment source.`;
}

export function refundListLineMessage(min: number, max: number, paymentStatus?: string | null): string {
  if (isOrderRefundSettled(paymentStatus)) {
    return `Refund processed — ${refundProcessedMessage(min, max)}`;
  }
  return refundGatewayStartedMessage();
}
