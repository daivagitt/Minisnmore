import { useEffect, useState } from "react";
import { ArrowLeft, Loader2, Printer } from "lucide-react";
import { PortraitInvoiceDocument } from "@/components/PortraitInvoiceDocument";
import { useSiteSettings } from "@/hooks/useSiteSettings";
import type { InvoiceOrder } from "@/lib/invoice-breakdown";
import { isInvoiceAvailable } from "@/lib/invoice-availability";

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
          setError("Invoice will be available once the order is confirmed.");
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
      <style>{`@media print { html, body { margin: 0 !important; padding: 0 !important; } body * { visibility: hidden !important; } .invoice-document, .invoice-document * { visibility: visible !important; } .invoice-document { position: absolute; left: 0; top: 0; box-sizing: border-box; width: 210mm !important; max-width: none !important; min-height: auto !important; height: auto !important; padding: 8mm !important; overflow: visible !important; } .invoice-document tbody tr { break-inside: avoid; page-break-inside: avoid; } @page { size: A4 portrait; margin: 0; } }`}</style>
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
