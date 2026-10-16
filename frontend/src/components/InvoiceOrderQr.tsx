import { useState } from "react";
import { QRCodeSVG } from "qrcode.react";
import { invoiceQrOrigin, orderDetailsUrl } from "@/lib/order-details-url";

export function InvoiceOrderQr({
  orderNumber,
  invoiceWebsite,
  origin,
}: {
  orderNumber: string;
  invoiceWebsite?: string | null;
  /** Overrides the runtime-origin fallback — useful in tests. */
  origin?: string;
}) {
  const trimmed = orderNumber.trim();
  const [runtimeOrigin] = useState(
    () => origin ?? (typeof window !== "undefined" ? window.location.origin : ""),
  );
  if (!trimmed) return null;

  const base = invoiceQrOrigin(invoiceWebsite, runtimeOrigin);
  if (!base) return null;

  const orderUrl = orderDetailsUrl(trimmed, base);

  return (
    <a
      href={orderUrl}
      aria-label={`View order ${trimmed}`}
      className="text-center text-black no-underline"
    >
      <QRCodeSVG
        value={orderUrl}
        size={80}
        level="M"
        marginSize={4}
        title={`Order ${trimmed} QR code`}
        className="h-[80px] w-[80px]"
      />
      <span className="mt-0.5 block text-[9px] font-semibold leading-none">
        Scan to view order
      </span>
    </a>
  );
}
