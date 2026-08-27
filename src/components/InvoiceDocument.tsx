import { useMemo } from "react";
import { buildInvoiceBreakdown, type InvoiceOrder } from "@/lib/invoice-breakdown";

export type InvoiceBrandSettings = {
  brand_name?: string | null;
  legal_entity_name?: string | null;
  invoice_logo_url?: string | null;
  logo_horizontal_url?: string | null;
  logo_url?: string | null;
  contact_email?: string | null;
  contact_phone?: string | null;
  gst_rate?: number | null;
  prices_include_gst?: boolean | null;
};

function money(value: number) {
  return Number(value || 0).toLocaleString("en-IN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

function rate(value: number) {
  return `${Number(value).toFixed(2)}%`;
}

function invoiceDate(value: string | null | undefined) {
  if (!value) return "—";
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return "—";
  return parsed.toLocaleDateString("en-IN", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    timeZone: "Asia/Kolkata",
  });
}

function addressLines(address: Record<string, unknown> | null | undefined) {
  if (!address) return [];
  const locality = [address.city, address.state, address.pincode].filter(Boolean).join(", ");
  return [address.full_name, address.line1, address.line2, locality, address.phone, address.email]
    .map((value) => String(value ?? "").trim())
    .filter(Boolean);
}

function SummaryRow({
  label,
  value,
  strong = false,
}: {
  label: string;
  value: string;
  strong?: boolean;
}) {
  return (
    <div
      className={`flex justify-between gap-8 border-b border-slate-200 py-1.5 ${strong ? "font-bold" : ""}`}
    >
      <span>{label}</span>
      <span className="tabular-nums">{value}</span>
    </div>
  );
}

export function InvoiceDocument({
  order,
  settings,
  compact = false,
}: {
  order: InvoiceOrder & { shipping_address?: Record<string, unknown> | null };
  settings: InvoiceBrandSettings;
  compact?: boolean;
}) {
  const breakdown = useMemo(
    () =>
      buildInvoiceBreakdown(order, {
        gstRate: Number(settings.gst_rate ?? 5),
        pricesIncludeGst: settings.prices_include_gst ?? true,
        sellerState: "Karnataka",
      }),
    [order, settings.gst_rate, settings.prices_include_gst],
  );
  const logo =
    settings.invoice_logo_url?.trim() ||
    settings.logo_horizontal_url?.trim() ||
    settings.logo_url?.trim() ||
    "";
  const customerAddress = addressLines(order.shipping_address);

  return (
    <article
      className={`invoice-document mx-auto bg-white text-slate-950 shadow-xl print:shadow-none ${compact ? "w-full p-4 text-[9px]" : "w-full max-w-[1120px] p-5 text-[10px] sm:p-8 sm:text-xs"}`}
    >
      <header className="flex flex-col sm:flex-row items-start justify-between gap-4 sm:gap-6 border-b-2 border-slate-900 pb-5">
        <div className="min-w-0">
          {logo ? (
            <img
              src={logo}
              alt={settings.brand_name || "Invoice logo"}
              className="mb-3 max-h-20 max-w-[300px] object-contain object-left"
            />
          ) : (
            <div className="mb-2 text-2xl font-bold tracking-tight">
              {settings.brand_name || "Mini's n More"}
            </div>
          )}
          <p className="font-semibold">
            {settings.legal_entity_name || settings.brand_name || "Mini's n More"}
          </p>
          {settings.contact_email && <p>{settings.contact_email}</p>}
          {settings.contact_phone && <p>{settings.contact_phone}</p>}
        </div>
        <div className="shrink-0 sm:text-right">
          <h1 className="text-xl sm:text-2xl font-black uppercase tracking-[0.12em]">Tax Invoice</h1>
          <p className="mt-2">
            <span className="font-semibold">Invoice No:</span> {order.order_number}
          </p>
          <p>
            <span className="font-semibold">Invoice Date:</span> {invoiceDate(order.created_at)}
          </p>
          <p>
            <span className="font-semibold">Place of supply:</span>{" "}
            {String(order.shipping_address?.state || "Karnataka")}
          </p>
          <p>
            <span className="font-semibold">Payment:</span>{" "}
            {String(order.payment_method || "online").toUpperCase()}
          </p>
        </div>
      </header>

      <section className="grid grid-cols-1 sm:grid-cols-2 gap-4 sm:gap-6 border-b border-slate-300 py-4">
        <div>
          <h2 className="mb-1 font-bold uppercase tracking-wider text-slate-600">
            Bill to / Ship to
          </h2>
          {customerAddress.length ? (
            customerAddress.map((line) => <p key={line}>{line}</p>)
          ) : (
            <p>Customer address unavailable</p>
          )}
        </div>
        <div className="sm:text-right">
          <h2 className="mb-1 font-bold uppercase tracking-wider text-slate-600">Tax treatment</h2>
          <p className="font-semibold">
            {breakdown.taxKind === "local" ? "Intra-state supply" : "Inter-state supply"}
          </p>
          <p>{breakdown.taxKind === "local" ? "CGST + SGST" : "IGST"}</p>
          <p>Prices {settings.prices_include_gst === false ? "exclude" : "include"} GST</p>
        </div>
      </section>

      <div className="mt-5 -mx-1 overflow-x-auto overscroll-x-contain [scrollbar-width:thin]">
        <table className="w-full min-w-[900px] border-collapse border border-slate-400">
          <thead>
            <tr className="bg-slate-100 text-center font-bold">
              {[
                "Item Name",
                "Qty",
                "MRP",
                "Total Disc.",
                "Taxable Amt",
                "Tax Type",
                "Tax Rate",
                "Tax Amt",
                "Sub Total",
              ].map((heading) => (
                <th key={heading} className="border border-slate-400 px-2 py-2">
                  {heading}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {breakdown.lines.map((line) => (
              <tr key={line.key} className="align-top">
                <td className="border border-slate-400 px-2 py-2">
                  <p className="font-semibold">{line.itemName}</p>
                  {line.details.map((detail) => (
                    <p key={detail} className="text-[0.9em] text-slate-600">
                      {detail}
                    </p>
                  ))}
                </td>
                <td className="border border-slate-400 px-2 py-2 text-center tabular-nums">
                  {line.qty}
                </td>
                <td className="border border-slate-400 px-2 py-2 text-right tabular-nums">
                  {money(line.mrp)}
                </td>
                <td className="border border-slate-400 px-2 py-2 text-right tabular-nums">
                  {money(line.totalDiscount)}
                </td>
                <td className="border border-slate-400 px-2 py-2 text-right tabular-nums">
                  {money(line.taxableAmount)}
                </td>
                <td className="whitespace-pre-line border border-slate-400 px-2 py-2 text-center">
                  {line.taxType}
                </td>
                <td className="border border-slate-400 px-2 py-2 text-right tabular-nums">
                  {line.taxRates.map((value, index) => (
                    <div key={`${line.key}-rate-${index}`}>{rate(value)}</div>
                  ))}
                </td>
                <td className="border border-slate-400 px-2 py-2 text-right tabular-nums">
                  {line.taxAmounts.map((value, index) => (
                    <div key={`${line.key}-tax-${index}`}>{money(value)}</div>
                  ))}
                </td>
                <td className="border border-slate-400 px-2 py-2 text-right font-semibold tabular-nums">
                  {money(line.subTotal)}
                </td>
              </tr>
            ))}
            <TotalTableRow label="Taxable Total" value={breakdown.taxableTotal} />
            {breakdown.taxKind === "local" ? (
              <>
                <TotalTableRow label="Total CGST Amount" value={breakdown.cgst} />
                <TotalTableRow label="Total SGST Amount" value={breakdown.sgst} />
              </>
            ) : (
              <TotalTableRow label="Total IGST Amount" value={breakdown.igst} />
            )}
            <TotalTableRow
              label="Total Amount Including Tax"
              value={breakdown.totalAmountIncludingTax}
            />
            {breakdown.roundOff !== 0 && (
              <TotalTableRow label="Round Off" value={breakdown.roundOff} />
            )}
            <TotalTableRow
              label="Grand Total (Inclusive of all taxes)"
              value={breakdown.grandTotal}
              strong
            />
          </tbody>
        </table>
      </div>

      <section className="mt-6 ml-auto w-full max-w-md border border-slate-300 px-4 py-2">
        <h2 className="border-b border-slate-400 py-2 text-sm font-bold uppercase tracking-wider">
          Workbook price breakdown
        </h2>
        <SummaryRow label="MRP" value={`₹${money(breakdown.mrpTotal)}`} />
        {breakdown.productDiscount > 0 && (
          <SummaryRow
            label="Less: Product discount"
            value={`− ₹${money(breakdown.productDiscount)}`}
          />
        )}
        <SummaryRow label="Selling Price" value={`₹${money(breakdown.sellingPrice)}`} />
        {breakdown.couponDiscount > 0 && (
          <SummaryRow
            label={`Less Coupon: ${order.coupon_code || "Discount"}`}
            value={`− ₹${money(breakdown.couponDiscount)}`}
          />
        )}
        <SummaryRow label="Amount" value={`₹${money(breakdown.amountAfterCoupon)}`} />
        <SummaryRow
          label="Add: Shipping"
          value={breakdown.shipping > 0 ? `₹${money(breakdown.shipping)}` : "Free"}
        />
        {breakdown.codFee > 0 && (
          <SummaryRow label="Add: COD" value={`₹${money(breakdown.codFee)}`} />
        )}
        {breakdown.donation > 0 && (
          <SummaryRow label="Add: Donation" value={`₹${money(breakdown.donation)}`} />
        )}
        {breakdown.roundOff !== 0 && (
          <SummaryRow
            label="Round Off"
            value={`${breakdown.roundOff > 0 ? "+ " : "− "}₹${money(Math.abs(breakdown.roundOff))}`}
          />
        )}
        <SummaryRow label="Total" value={`₹${money(breakdown.grandTotal)}`} strong />
      </section>

      <footer className="mt-8 border-t border-slate-300 pt-4 text-center text-[0.9em] text-slate-600">
        <p>This is a computer-generated invoice.</p>
        <p>Thank you for shopping with {settings.brand_name || "Mini's n More"}.</p>
      </footer>
    </article>
  );
}

function TotalTableRow({
  label,
  value,
  strong = false,
}: {
  label: string;
  value: number;
  strong?: boolean;
}) {
  return (
    <tr className={strong ? "bg-slate-100 font-bold" : "font-semibold"}>
      <td colSpan={8} className="border border-slate-400 px-2 py-1.5 text-right">
        {label}
      </td>
      <td className="border border-slate-400 px-2 py-1.5 text-right tabular-nums">
        {money(value)}
      </td>
    </tr>
  );
}
