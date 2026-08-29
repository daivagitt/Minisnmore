export type InvoiceAvailabilityOrder = {
  status?: string | null;
};

/** Pending orders are only payment attempts, so they must not produce a tax invoice yet. */
export function isInvoiceAvailable(order: InvoiceAvailabilityOrder): boolean {
  return order.status?.trim().toLowerCase() !== "pending";
}
