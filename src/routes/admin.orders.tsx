import { createFileRoute } from "@tanstack/react-router";
import { useCallback, useEffect, useMemo, useState } from "react";
import { AdminPage } from "@/components/admin/AdminPage";
import { AlertCircle, CheckCircle2, Loader2, RefreshCw } from "lucide-react";
import { toast } from "sonner";
import { listAdminOrders, markCodCollected, type AdminOrderRow } from "@/lib/orders.functions";
import { syncZohoOrderByNumber } from "@/lib/backend-data";
import { showApiError } from "@/lib/ui-errors";

export const Route = createFileRoute("/admin/orders")({ component: AdminOrdersPage });

type Period = "day" | "week";

function localDateValue(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function selectedRange(value: string, period: Period) {
  const [year, month, day] = value.split("-").map(Number);
  const start = new Date(year, month - 1, day);
  if (period === "week") {
    const daysSinceMonday = (start.getDay() + 6) % 7;
    start.setDate(start.getDate() - daysSinceMonday);
  }
  const end = new Date(start);
  end.setDate(end.getDate() + (period === "day" ? 1 : 7));
  const dateFormat = new Intl.DateTimeFormat("en-IN", {
    day: "numeric",
    month: "short",
    year: "numeric",
  });
  const label =
    period === "day"
      ? dateFormat.format(start)
      : `${dateFormat.format(start)} – ${dateFormat.format(new Date(end.getTime() - 1))}`;
  return { from: start.toISOString(), to: end.toISOString(), label };
}

function normalizedStatus(value: string | null | undefined) {
  return value?.trim().toLowerCase() || "not_synced";
}

function SyncBadge({ status, eligible }: { status: string; eligible: boolean }) {
  if (!eligible) {
    return (
      <span className="rounded-full bg-muted px-2.5 py-1 text-[11px] font-medium text-muted-foreground">
        Not due
      </span>
    );
  }
  const normalized = normalizedStatus(status);
  const styles =
    normalized === "synced"
      ? "bg-emerald-100 text-emerald-800"
      : normalized === "pending"
        ? "bg-amber-100 text-amber-800"
        : "bg-red-100 text-red-800";
  const label =
    normalized === "not_synced" ? "Not synced" : normalized[0].toUpperCase() + normalized.slice(1);
  return (
    <span className={`rounded-full px-2.5 py-1 text-[11px] font-semibold ${styles}`}>{label}</span>
  );
}

function AdminOrdersPage() {
  const [rows, setRows] = useState<AdminOrderRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);
  const [period, setPeriod] = useState<Period>("day");
  const [selectedDate, setSelectedDate] = useState(() => localDateValue(new Date()));
  const range = useMemo(() => selectedRange(selectedDate, period), [selectedDate, period]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await listAdminOrders(range.from, range.to);
      setRows(res.orders ?? []);
    } catch (error) {
      showApiError(error, "Could not load orders");
    } finally {
      setLoading(false);
    }
  }, [range.from, range.to]);

  useEffect(() => {
    void load();
  }, [load]);

  const collect = async (orderNumber: string) => {
    setBusy(`collect:${orderNumber}`);
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

  const sync = async (orderNumber: string) => {
    setBusy(`zoho:${orderNumber}`);
    try {
      const result = await syncZohoOrderByNumber(orderNumber);
      if (normalizedStatus(result.zohoSyncStatus) !== "synced") {
        throw new Error(result.zohoSyncError || "Zoho sync did not complete");
      }
      toast.success(`${orderNumber} synced to Zoho`);
      await load();
    } catch (error) {
      showApiError(error, `Could not sync ${orderNumber} to Zoho`);
    } finally {
      setBusy(null);
    }
  };

  const attentionOrders = useMemo(
    () =>
      rows.filter(
        (order) => order.zoho_sync_eligible && normalizedStatus(order.zoho_sync_status) !== "synced",
      ),
    [rows],
  );
  const zohoEligible = rows.filter((order) => order.zoho_sync_eligible);
  const zohoSynced = zohoEligible.filter(
    (order) => normalizedStatus(order.zoho_sync_status) === "synced",
  ).length;

  return (
    <AdminPage
      eyebrow="Fulfilment"
      title="Orders"
      description="Review orders by day or week, confirm COD collection, and immediately see or retry Zoho syncs."
    >
      <div className="mb-5 flex flex-wrap items-end gap-3 rounded-2xl border border-border/70 bg-card/70 p-4">
        <div>
          <p className="mb-1.5 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            View
          </p>
          <div className="flex rounded-xl bg-muted p-1">
            {(["day", "week"] as Period[]).map((option) => (
              <button
                key={option}
                type="button"
                onClick={() => setPeriod(option)}
                className={`rounded-lg px-3 py-1.5 text-sm font-medium transition ${period === option ? "bg-background shadow-sm" : "text-muted-foreground"}`}
              >
                {option === "day" ? "By date" : "By week"}
              </button>
            ))}
          </div>
        </div>
        <label className="block">
          <span className="mb-1.5 block text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            Selected date
          </span>
          <input
            type="date"
            value={selectedDate}
            onChange={(event) => setSelectedDate(event.target.value || localDateValue(new Date()))}
            className="h-10 rounded-xl border border-border bg-background px-3 text-sm"
          />
        </label>
        <p className="pb-2 text-sm text-muted-foreground">{range.label}</p>
        <button
          type="button"
          onClick={() => void load()}
          disabled={loading}
          className="ml-auto inline-flex h-10 items-center gap-2 rounded-xl border border-border bg-background px-3 text-sm font-medium disabled:opacity-60"
        >
          <RefreshCw className={`h-4 w-4 ${loading ? "animate-spin" : ""}`} /> Refresh
        </button>
      </div>

      {!loading && rows.length > 0 && (
        <div
          className={`mb-5 rounded-2xl border p-4 ${attentionOrders.length === 0 ? "border-emerald-200 bg-emerald-50/80" : "border-amber-200 bg-amber-50/80"}`}
        >
          <div className="flex flex-wrap items-center gap-3">
            {attentionOrders.length === 0 ? (
              <CheckCircle2 className="h-5 w-5 text-emerald-700" />
            ) : (
              <AlertCircle className="h-5 w-5 text-amber-700" />
            )}
            <p className="font-semibold">
              {attentionOrders.length === 0
                ? "All eligible orders in this period are synced to Zoho"
                : `${attentionOrders.length} ${attentionOrders.length === 1 ? "order needs" : "orders need"} Zoho sync attention`}
            </p>
            <div className="ml-auto flex gap-2 text-xs text-muted-foreground">
              <span className="rounded-full bg-white/70 px-2.5 py-1">
                Zoho {zohoSynced}/{zohoEligible.length}
              </span>
            </div>
          </div>
        </div>
      )}

      {loading ? (
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      ) : rows.length === 0 ? (
        <p className="text-sm text-muted-foreground">No orders found for {range.label}.</p>
      ) : (
        <ul className="space-y-4">
          {rows.map((order) => {
            const isPendingCod =
              order.payment_method === "cod" && order.payment_status === "pending";
            return (
              <li key={order.order_number} className="glass-strong rounded-2xl p-4">
                <div className="flex flex-wrap items-start justify-between gap-3 border-b border-border/60 pb-3">
                  <div>
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="font-semibold">{order.order_number}</p>
                      <span className="rounded-full bg-muted px-2 py-0.5 text-[11px] font-medium uppercase">
                        {order.status || "pending"}
                      </span>
                      <span className="rounded-full bg-muted px-2 py-0.5 text-[11px] font-medium uppercase">
                        {order.payment_status || "pending"}
                      </span>
                      <span className="text-[11px] font-medium uppercase text-muted-foreground">
                        {order.payment_method}
                      </span>
                    </div>
                    <p className="mt-1 text-xs text-muted-foreground">
                      {order.full_name || "—"} · {order.phone || "—"} · {order.city || "—"}
                    </p>
                    <p className="mt-1 text-xs text-muted-foreground">
                      {order.placed_at
                        ? new Date(order.placed_at).toLocaleString("en-IN")
                        : "Date unavailable"}
                    </p>
                  </div>
                  <div className="text-right">
                    <p className="font-semibold">
                      ₹{Number(order.total ?? 0).toLocaleString("en-IN")}
                    </p>
                    {Number(order.cod_fee ?? 0) > 0 && (
                      <p className="text-xs text-muted-foreground">
                        includes ₹{Number(order.cod_fee).toLocaleString("en-IN")} COD fee
                      </p>
                    )}
                    {isPendingCod && (
                      <button
                        type="button"
                        disabled={busy !== null}
                        onClick={() => void collect(order.order_number)}
                        className="mt-2 rounded-full bg-primary px-3 py-1.5 text-xs text-primary-foreground disabled:opacity-60"
                      >
                        {busy === `collect:${order.order_number}`
                          ? "Updating…"
                          : "Mark COD collected"}
                      </button>
                    )}
                  </div>
                </div>

                <div className="mt-3 rounded-xl border border-border/70 bg-background/60 p-3">
                  <div className="flex items-center justify-between gap-2">
                    <p className="text-sm font-semibold">Zoho</p>
                    <SyncBadge status={order.zoho_sync_status} eligible={order.zoho_sync_eligible} />
                  </div>
                  {order.zoho_sync_error && (
                    <p className="mt-2 text-xs text-destructive" title={order.zoho_sync_error}>
                      {order.zoho_sync_error}
                    </p>
                  )}
                  {order.zoho_last_synced_at && (
                    <p className="mt-2 text-[11px] text-muted-foreground">
                      Last synced {new Date(order.zoho_last_synced_at).toLocaleString("en-IN")}
                    </p>
                  )}
                  {order.zoho_sync_eligible && normalizedStatus(order.zoho_sync_status) !== "synced" && (
                    <button
                      type="button"
                      disabled={busy !== null}
                      onClick={() => void sync(order.order_number)}
                      className="mt-2 inline-flex items-center gap-1.5 rounded-full border border-border bg-background px-3 py-1.5 text-xs font-medium disabled:opacity-60"
                    >
                      {busy === `zoho:${order.order_number}` ? (
                        <Loader2 className="h-3.5 w-3.5 animate-spin" />
                      ) : (
                        <RefreshCw className="h-3.5 w-3.5" />
                      )}
                      {busy === `zoho:${order.order_number}` ? "Syncing…" : "Sync to Zoho"}
                    </button>
                  )}
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </AdminPage>
  );
}
