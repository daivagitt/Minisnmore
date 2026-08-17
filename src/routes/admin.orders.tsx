import { createFileRoute } from "@tanstack/react-router";
import { useCallback, useEffect, useState } from "react";
import { AdminPage } from "@/components/admin/AdminPage";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";
import { listCodPendingOrders, markCodCollected, type AdminCodOrderRow } from "@/lib/orders.functions";
import { showApiError } from "@/lib/ui-errors";

export const Route = createFileRoute("/admin/orders")({ component: AdminOrdersPage });

function AdminOrdersPage() {
  const [rows, setRows] = useState<AdminCodOrderRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await listCodPendingOrders();
      setRows(res.orders ?? []);
    } catch (error) {
      showApiError(error, "Could not load Cash on Delivery orders");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const collect = async (orderNumber: string) => {
    setBusy(orderNumber);
    try {
      await markCodCollected(orderNumber);
      toast.success(`${orderNumber} marked as collected`);
      await load();
    } catch (error) {
      showApiError(error, "Could not mark order as collected");
    } finally {
      setBusy(null);
    }
  };

  return (
    <AdminPage
      eyebrow="Fulfilment"
      title="Cash on Delivery"
      description="COD orders awaiting doorstep collection. Orders sync to Zoho Books when placed. Mark collected once payment is received — this records the cash payment in Zoho and Ledgerly."
    >
      {loading ? (
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      ) : rows.length === 0 ? (
        <p className="text-sm text-muted-foreground">No COD orders awaiting collection.</p>
      ) : (
        <ul className="space-y-4">
          {rows.map((o) => (
            <li key={o.order_number} className="glass-strong rounded-2xl p-4 flex flex-wrap items-center justify-between gap-3">
              <div>
                <p className="font-medium">{o.order_number}</p>
                <p className="text-xs text-muted-foreground">
                  {o.full_name || "—"} · {o.phone || "—"} · {o.city || "—"}
                </p>
                <p className="text-xs text-muted-foreground mt-1">
                  {o.placed_at ? new Date(o.placed_at).toLocaleString("en-IN") : ""}
                </p>
              </div>
              <div className="flex items-center gap-3">
                <div className="text-right">
                  <p className="font-medium">₹{Number(o.total ?? 0).toLocaleString("en-IN")}</p>
                  <p className="text-xs text-muted-foreground">incl. ₹{Number(o.cod_fee ?? 0).toLocaleString("en-IN")} COD fee</p>
                </div>
                <button
                  type="button"
                  disabled={busy === o.order_number}
                  className="text-xs rounded-full bg-primary text-primary-foreground px-3 py-1.5 disabled:opacity-60"
                  onClick={() => collect(o.order_number)}
                >
                  Mark collected
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </AdminPage>
  );
}
