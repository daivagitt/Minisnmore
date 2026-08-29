export function orderDetailsPath(orderNumber: string): string {
  return `/account/orders/${encodeURIComponent(orderNumber.trim())}`;
}

export function orderDetailsUrl(orderNumber: string, origin: string): string {
  return new URL(orderDetailsPath(orderNumber), `${origin.replace(/\/+$/, "")}/`).toString();
}

/** Prefer the configured store URL so printed QR codes point at production, not the print host. */
export function invoiceQrOrigin(invoiceWebsite?: string | null, runtimeOrigin = ""): string {
  for (const candidate of [invoiceWebsite, runtimeOrigin]) {
    const value = candidate?.trim();
    if (!value) continue;
    try {
      const url = new URL(value);
      if (url.protocol === "http:" || url.protocol === "https:") return url.origin;
    } catch {
      // Try the runtime fallback when a configured value is not an absolute URL.
    }
  }
  return "";
}
