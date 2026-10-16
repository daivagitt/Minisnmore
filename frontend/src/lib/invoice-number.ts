/** Tax invoice numbers must be distinct from store order numbers (GST Rule 46). */
export function invoiceNumberFromOrder(orderNumber: string | null | undefined): string {
  const trimmed = String(orderNumber ?? "").trim();
  if (!trimmed) return "—";
  if (trimmed.toUpperCase().startsWith("INV-")) return trimmed;
  if (trimmed.toUpperCase().startsWith("MNM-")) return `INV-${trimmed.slice(4)}`;
  return `INV-${trimmed}`;
}
