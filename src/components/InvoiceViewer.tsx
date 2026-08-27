import { useEffect, useState } from "react";
import { ArrowLeft, Loader2, Printer } from "lucide-react";
import { InvoiceDocument } from "@/components/InvoiceDocument";
import { useSiteSettings } from "@/hooks/useSiteSettings";
import type { InvoiceOrder } from "@/lib/invoice-breakdown";

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
        if (active) setOrder(loaded);
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
      <style>{`@media print { body * { visibility: hidden !important; } .invoice-document, .invoice-document * { visibility: visible !important; } .invoice-document { position: absolute; inset: 0; width: 100%; max-width: none; } @page { size: A4 landscape; margin: 8mm; } }`}</style>
      <div className="mx-auto mb-3 flex w-full max-w-[1120px] items-center justify-between gap-2 sm:gap-3 print:hidden flex-wrap">
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
      {order && <InvoiceDocument order={order} settings={settings} />}
    </div>
  );
}
