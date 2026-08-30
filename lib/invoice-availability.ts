import { isCodOrder, needsOnlinePayment, type OrderStatusFields } from "./order-status";

export type InvoiceAvailabilityOrder = OrderStatusFields;

/** Mirrors backend ZohoSyncService.shouldCreateInvoice plus pending-payment guard. */
export function isInvoiceAvailable(order: InvoiceAvailabilityOrder): boolean {
  if (needsOnlinePayment(order)) return false;
  if (isCodOrder(order) && order.payment_status?.trim().toLowerCase() !== "paid") return false;
  return true;
}
