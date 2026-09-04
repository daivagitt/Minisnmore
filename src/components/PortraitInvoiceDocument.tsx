import { useMemo } from "react";
import { InvoiceOrderQr } from "@/components/InvoiceOrderQr";
import { buildInvoiceBreakdown, type InvoiceOrder } from "@/lib/invoice-breakdown";
import { invoiceNumberFromOrder } from "@/lib/invoice-number";
import { DEFAULT_SELLER_STATE, formatPlaceOfSupply } from "@/lib/india-states";

export type InvoiceBrandSettings = {
  brand_name?: string | null;
  legal_entity_name?: string | null;
  invoice_logo_url?: string | null;
  logo_horizontal_url?: string | null;
  logo_url?: string | null;
  contact_email?: string | null;
  contact_phone?: string | null;
  invoice_website?: string | null;
  invoice_gstin?: string | null;
  invoice_sold_by_address?: string | null;
  invoice_dispatch_from_address?: string | null;
  invoice_registered_office_address?: string | null;
  invoice_cin?: string | null;
  invoice_footer_note?: string | null;
  invoice_seller_state?: string | null;
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
  return parsed.toLocaleDateString("en-CA", {
    day: "2-digit",
    month: "2-digit",
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

function display(value: string | null | undefined) {
  return value?.trim() || "—";
}

export function PortraitInvoiceDocument({
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
        sellerState: settings.invoice_seller_state?.trim() || DEFAULT_SELLER_STATE,
      }),
    [order, settings.gst_rate, settings.invoice_seller_state, settings.prices_include_gst],
  );
  const logo =
    settings.invoice_logo_url?.trim() ||
    settings.logo_horizontal_url?.trim() ||
    settings.logo_url?.trim() ||
    "";
  const customerAddress = addressLines(order.shipping_address);
  const brandName = settings.brand_name || "Mini's n More";
  const seller = settings.invoice_sold_by_address?.trim();
  const invoiceNumber = order.invoice_number?.trim() || invoiceNumberFromOrder(order.order_number);

  return (
    <article
      className={`invoice-document box-border mx-auto flex flex-col bg-white font-sans text-[12px] leading-[1.35] text-black shadow-xl print:shadow-none print:overflow-visible ${compact ? "min-h-[720px] w-full p-4" : "min-h-[297mm] w-[210mm] max-w-full p-[8mm] print:w-[210mm] print:min-h-[297mm]"}`}
    >
      <header className="grid grid-cols-[minmax(0,1fr)_auto_minmax(72px,1fr)] items-start gap-3">
        <div className="min-w-0">
          {logo ? (
            <img
              src={logo}
              alt={`${brandName} invoice logo`}
              className="mb-1.5 h-auto max-h-12 w-full max-w-[190px] object-contain object-left"
            />
          ) : (
            <div className="text-base font-bold leading-tight">{brandName}</div>
          )}
          <p className="break-all text-[10px]">{display(settings.invoice_website)}</p>
        </div>
        <h1 className="whitespace-nowrap pt-0.5 text-center text-[22px] font-bold uppercase tracking-wide">
          Tax Invoice
        </h1>
        <div className="flex min-h-20 min-w-0 flex-col items-end justify-start">
          <InvoiceOrderQr
            orderNumber={order.order_number}
            invoiceWebsite={settings.invoice_website}
          />
        </div>
      </header>

      <section className="mt-2 grid grid-cols-[1.15fr_0.85fr] gap-4">
        <div className="min-w-0">
          <h2 className="font-bold">Shipping Address</h2>
          <div className="mt-1 min-h-14">
            {customerAddress.length ? (
              customerAddress.map((line, index) => <p key={`${line}-${index}`}>{line}</p>)
            ) : (
              <p>Customer address unavailable</p>
            )}
          </div>
        </div>
        <div className="grid content-end gap-0.5">
          <MetaLine label="Invoice No" value={invoiceNumber} />
          <MetaLine label="Invoice Date" value={invoiceDate(order.created_at)} />
          <MetaLine label="Order Date" value={invoiceDate(order.created_at)} />
        </div>
      </section>

      <section className="mt-2 grid grid-cols-[1.15fr_0.85fr] gap-x-4 gap-y-0.5">
        <MetaLine
          className="col-start-1 row-start-1"
          label="GST NO"
          value={display(settings.invoice_gstin)}
        />
        <MetaLine
          className="col-start-2 row-start-1 hidden"
          label="Shipment ID"
          value={display(order.tracking_number)}
        />
        <MetaLine
          className="col-start-1 row-start-2"
          label="Place Of Supply"
          value={formatPlaceOfSupply(order.shipping_address?.state)}
        />
        <MetaLine
          className="col-start-2 row-start-2"
          label="Order No"
          value={order.order_number}
        />
      </section>

      <section className="mt-2 space-y-1 border-t border-black pt-1.5">
        <LegalLine label="Sold By" value={display(seller)} />
        <LegalLine label="Dispatch From" value={display(settings.invoice_dispatch_from_address)} />
      </section>

      <table className="mt-2 w-full table-fixed border-collapse border border-black text-[11px] leading-[1.3]">
        <colgroup>
          <col className="w-[35%]" />
          <col className="w-[5%]" />
          <col className="w-[8%]" />
          <col className="w-[8%]" />
          <col className="w-[10%]" />
          <col className="w-[8%]" />
          <col className="w-[8%]" />
          <col className="w-[8%]" />
          <col className="w-[10%]" />
        </colgroup>
        <thead>
          <tr className="text-center font-bold">
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
              <th key={heading} className="border border-black px-1 py-1">
                {heading}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {breakdown.lines.map((line) => (
            <tr key={line.key} className="break-inside-avoid align-top">
              <td className="border border-black px-1 py-1">
                <p>{line.itemName}</p>
                {line.details.map((detail) => (
                  <p key={detail} className="text-[9px]">
                    {detail}
                  </p>
                ))}
              </td>
              <NumberCell value={String(line.qty)} center />
              <NumberCell value={money(line.mrp)} />
              <NumberCell value={money(line.totalDiscount)} />
              <NumberCell value={money(line.taxableAmount)} />
              <td className="whitespace-pre-line border border-black px-1 py-1 text-center">
                {line.taxType}
              </td>
              <td className="border border-black px-1 py-1 text-right tabular-nums">
                {line.taxRates.map((value, index) => (
                  <div key={`${line.key}-rate-${index}`}>{rate(value)}</div>
                ))}
              </td>
              <td className="border border-black px-1 py-1 text-right tabular-nums">
                {line.taxAmounts.map((value, index) => (
                  <div key={`${line.key}-tax-${index}`}>{money(value)}</div>
                ))}
              </td>
              <NumberCell value={money(line.subTotal)} />
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
          {breakdown.roundOff !== 0 ? (
            <TotalTableRow label="Round Off" value={breakdown.roundOff} />
          ) : null}
          <TotalTableRow
            label="Grand Total (Inclusive of all taxes)"
            value={breakdown.grandTotal}
            strong
          />
        </tbody>
      </table>

      <footer className="mt-auto border-t border-black pt-2 text-[10px] leading-[1.4]">
        <p className="font-bold">Not For Resale</p>
        {settings.invoice_footer_note?.trim() ? <p>{settings.invoice_footer_note}</p> : null}
        <p>This is a computer-generated invoice and requires no signature or stamp.</p>
        <LegalLine
          label="Registered Office"
          value={display(settings.invoice_registered_office_address)}
        />
        <div className="flex flex-wrap gap-x-4">
          <MetaLine label="CIN" value={display(settings.invoice_cin)} />
          {settings.contact_email ? (
            <MetaLine label="Email" value={settings.contact_email} />
          ) : null}
          {settings.contact_phone ? (
            <MetaLine label="Phone" value={settings.contact_phone} />
          ) : null}
        </div>
        <p className="font-semibold">Disclaimer: Applicable GST benefits are passed on.</p>
      </footer>
    </article>
  );
}

function MetaLine({
  label,
  value,
  className,
}: {
  label: string;
  value: string;
  className?: string;
}) {
  return (
    <p className={className ? `min-w-0 ${className}` : "min-w-0"}>
      <span className="font-bold">{label} :</span> <span className="break-words">{value}</span>
    </p>
  );
}

function LegalLine({ label, value }: { label: string; value: string }) {
  return (
    <p className="whitespace-pre-line break-words">
      <span className="font-bold">{label} :</span> {value}
    </p>
  );
}

function NumberCell({ value, center = false }: { value: string; center?: boolean }) {
  return (
    <td
      className={`border border-black px-1 py-1 tabular-nums ${center ? "text-center" : "text-right"}`}
    >
      {value}
    </td>
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
    <tr className={strong ? "font-bold" : ""}>
      <td colSpan={8} className="border border-black px-1 py-0.5 text-right">
        {label}
      </td>
      <td className="border border-black px-1 py-0.5 text-right tabular-nums">{money(value)}</td>
    </tr>
  );
}
