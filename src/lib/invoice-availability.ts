import { isCodOrder, type OrderStatusFields } from "@/lib/order-status";

/** Mirrors backend ZohoSyncService.shouldCreateInvoice plus pending-payment guard. */
export function isInvoiceAvailable(order: OrderStatusFields): boolean {
  const status = order.status?.trim().toLowerCase();
  if (status === "pending") return false;
  if (isCodOrder(order) && order.payment_status?.trim().toLowerCase() !== "paid") return false;
  return true;
}
