import { useEffect, useState } from "react";
import { ArrowLeft, Loader2, Printer } from "lucide-react";
import { PortraitInvoiceDocument } from "@/components/PortraitInvoiceDocument";
import { useSiteSettings } from "@/hooks/useSiteSettings";
import type { InvoiceOrder } from "@/lib/invoice-breakdown";
import { isInvoiceAvailable } from "@/lib/invoice-availability";

/** Force a single A4 sheet: hide app chrome from layout and pin the invoice to 210×297mm. */
export const INVOICE_PRINT_CSS = `@media print {
  @page { size: 210mm 297mm portrait; margin: 0; }
  html, body {
    margin: 0 !important;
    padding: 0 !important;
    width: 210mm !important;
    height: 297mm !important;
    overflow: hidden !important;
    background: #fff !important;
  }
  body * { visibility: hidden !important; }
  .invoice-document, .invoice-document * { visibility: visible !important; }
  .invoice-print-root {
    position: static !important;
    inset: auto !important;
    padding: 0 !important;
    overflow: hidden !important;
    background: #fff !important;
    height: 297mm !important;
  }
  .invoice-print-root > :not(.invoice-document) { display: none !important; }
  .invoice-document {
    position: absolute !important;
    left: 0 !important;
    top: 0 !important;
    margin: 0 !important;
    box-sizing: border-box !important;
    width: 210mm !important;
    max-width: 210mm !important;
    height: 297mm !important;
    max-height: 297mm !important;
    min-height: 0 !important;
    padding: 6mm !important;
    overflow: hidden !important;
    box-shadow: none !important;
    page-break-after: avoid !important;
    page-break-inside: avoid !important;
    break-after: avoid !important;
    break-inside: avoid !important;
  }
  .invoice-document img { max-height: 40px !important; }
  .invoice-document table th,
  .invoice-document table td { padding-top: 2px !important; padding-bottom: 2px !important; }
}`;

export function InvoiceViewer({
  orderNumber,
  loadOrder,
  onBack,
  backLabel,
}: {
  orderNumber: string;
  loadOrder: (orderNumber: string) => Promise<InvoiceOrder>;
  onBack: () => void;
  backLabel: string;
}) {
  const settings = useSiteSettings();
  const [order, setOrder] = useState<InvoiceOrder | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    void loadOrder(orderNumber)
      .then((loaded) => {
        if (!active) return;
        if (!isInvoiceAvailable(loaded)) {
          setError("Invoice will be available once the order is confirmed and payment is recorded.");
          return;
        }
        setOrder(loaded);
      })
      .catch((reason) => {
        if (active) setError(reason instanceof Error ? reason.message : "Could not load invoice");
      });
    return () => {
      active = false;
    };
  }, [loadOrder, orderNumber]);

  return (
    <div className="invoice-print-root fixed inset-0 z-[100] overflow-auto bg-slate-200 px-2 py-4 sm:px-6 print:static print:bg-white print:p-0">
      <style>{INVOICE_PRINT_CSS}</style>
      <div className="mx-auto mb-3 flex w-full max-w-[794px] items-center justify-between gap-2 sm:gap-3 print:hidden flex-wrap">
        <button
          type="button"
          onClick={onBack}
          className="inline-flex items-center gap-2 rounded-full bg-white px-3 sm:px-4 py-2 text-sm font-medium shadow-sm"
        >
          <ArrowLeft className="h-4 w-4" /> {backLabel}
        </button>
        {order && (
          <button
            type="button"
            onClick={() => window.print()}
            className="inline-flex items-center gap-2 rounded-full bg-primary px-3 sm:px-4 py-2 text-sm font-medium text-primary-foreground shadow-sm"
          >
            <Printer className="h-4 w-4" /> Print / Save PDF
          </button>
        )}
      </div>

      {!order && !error && (
        <div className="grid min-h-72 place-items-center">
          <Loader2 className="h-7 w-7 animate-spin text-primary" />
        </div>
      )}
      {error && (
        <div className="mx-auto max-w-lg rounded-2xl bg-white p-8 text-center text-sm text-destructive">
          {error}
        </div>
      )}
      {order && <PortraitInvoiceDocument order={order} settings={settings} />}
    </div>
  );
}
