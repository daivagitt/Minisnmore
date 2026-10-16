import { needsOnlinePayment, type OrderStatusFields } from "@/lib/order-status";

export type InvoiceAvailabilityOrder = OrderStatusFields;

/** Blocks only orders still waiting for an online payment confirmation. */
export function isInvoiceAvailable(order: InvoiceAvailabilityOrder): boolean {
  if (needsOnlinePayment(order)) return false;
  return true;
}
