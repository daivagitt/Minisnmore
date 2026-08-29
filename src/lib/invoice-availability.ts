import { needsOnlinePayment, type OrderStatusFields } from "./order-status";

export type InvoiceAvailabilityOrder = OrderStatusFields;

/** Unpaid online checkout attempts must not produce a tax invoice yet. */
export function isInvoiceAvailable(order: InvoiceAvailabilityOrder): boolean {
  return !needsOnlinePayment(order);
}
