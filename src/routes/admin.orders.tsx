import { createFileRoute, Link } from "@tanstack/react-router";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AdminPage } from "@/components/admin/AdminPage";
import { AlertCircle, CheckCircle2, CloudUpload, FileText, Loader2, RefreshCw, Search, X } from "lucide-react";
import { toast } from "sonner";
import { listAdminOrders, markCodCollected, type AdminOrderRow } from "@/lib/orders.functions";
import {
  formatZohoSyncFailures,
  formatZohoSyncSummary,
  retryZohoOrders,
  syncZohoOrderByNumber,
} from "@/lib/backend-data";
import { showApiError } from "@/lib/ui-errors";
import { ListPagination } from "@/components/ListPagination";
import { LIST_PAGE_SIZE } from "@/lib/pagination";

export const Route = createFileRoute("/admin/orders")({ component: AdminOrdersPage });

type Period = "all" | "day" | "week";

function localDateValue(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function selectedRange(value: string, period: Period) {
  if (period === "all") {
    return { from: undefined as string | undefined, to: undefined as string | undefined, label: "All orders" };
  }
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

function FilterSelect({
  label,
  value,
  onChange,
  options,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  options: { value: string; label: string }[];
}) {
  return (
    <label className="block min-w-[9.5rem]">
      <span className="mb-1.5 block text-xs font-semibold uppercase tracking-wide text-muted-foreground">
        {label}
      </span>
      <select
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="h-10 w-full rounded-xl border border-border bg-background px-3 text-sm"
      >
        {options.map((option) => (
          <option key={option.value || "all"} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </label>
  );
}

function AdminOrdersPage() {
  const [rows, setRows] = useState<AdminOrderRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);
  const [period, setPeriod] = useState<Period>("all");
  const [page, setPage] = useState(1);
  const [totalItems, setTotalItems] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [selectedDate, setSelectedDate] = useState(() => localDateValue(new Date()));
  const [qInput, setQInput] = useState("");
  const [q, setQ] = useState("");
  const [status, setStatus] = useState("");
  const [paymentStatus, setPaymentStatus] = useState("");
  const [paymentMethod, setPaymentMethod] = useState("");
  const [zohoSyncStatus, setZohoSyncStatus] = useState("");
  const [ledgerlySyncStatus, setLedgerlySyncStatus] = useState("");
  const range = useMemo(() => selectedRange(selectedDate, period), [selectedDate, period]);
  const loadGen = useRef(0);
  const filtersKey = `${period}:${selectedDate}:${q}:${status}:${paymentStatus}:${paymentMethod}:${zohoSyncStatus}:${ledgerlySyncStatus}`;
  const filtersActive = Boolean(
    q || status || paymentStatus || paymentMethod || zohoSyncStatus || ledgerlySyncStatus || period !== "all",
  );

  useEffect(() => {
    const timer = window.setTimeout(() => setQ(qInput.trim()), 300);
    return () => window.clearTimeout(timer);
  }, [qInput]);

  useEffect(() => {
    setPage(1);
  }, [filtersKey]);

  const load = useCallback(async () => {
    const gen = ++loadGen.current;
    setLoading(true);
    try {
      const res = await listAdminOrders(range.from, range.to, {
        page,
        size: LIST_PAGE_SIZE,
        q,
        status,
        paymentStatus,
        paymentMethod,
        zohoSyncStatus,
        ledgerlySyncStatus,
      });
      if (gen !== loadGen.current) return;
      setRows(res.orders ?? []);
      setTotalItems(res.totalItems);
      setTotalPages(Math.max(1, res.totalPages));
      const nextPage = Math.min(Math.max(1, res.page), Math.max(1, res.totalPages));
      if (nextPage !== page) setPage(nextPage);
    } catch (error) {
      if (gen !== loadGen.current) return;
      showApiError(error, "Could not load orders");
    } finally {
      if (gen === loadGen.current) setLoading(false);
    }
  }, [range.from, range.to, page, q, status, paymentStatus, paymentMethod, zohoSyncStatus, ledgerlySyncStatus]);

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

  const syncPending = async () => {
    setBusy("zoho:pending");
    try {
      const summary = await retryZohoOrders();
      toast.success(formatZohoSyncSummary(summary, "Order sync"));
      const notes = formatZohoSyncFailures(summary);
      if (notes.length > 0) {
        toast.message(notes.slice(0, 3).join(" · "));
      }
      await load();
    } catch (error) {
      showApiError(error, "Could not sync pending orders to Zoho");
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
      description="Search and filter orders, confirm COD collection, and sync paid orders to Zoho Books."
    >
      <div className="mb-5 flex flex-wrap items-end gap-3 rounded-2xl border border-border/70 bg-card/70 p-4">
        <div>
          <p className="mb-1.5 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            View
          </p>
          <div className="flex rounded-xl bg-muted p-1">
            {(["all", "day", "week"] as Period[]).map((option) => (
              <button
                key={option}
                type="button"
                onClick={() => setPeriod(option)}
                className={`rounded-lg px-3 py-1.5 text-sm font-medium transition ${period === option ? "bg-background shadow-sm" : "text-muted-foreground"}`}
              >
                {option === "all" ? "All" : option === "day" ? "By date" : "By week"}
              </button>
            ))}
          </div>
        </div>
        {period !== "all" && (
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
        )}
        <p className="pb-2 text-sm text-muted-foreground">{range.label}</p>
        <label className="block min-w-[16rem] flex-1">
          <span className="mb-1.5 block text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            Search
          </span>
          <span className="relative block">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <input
              type="search"
              value={qInput}
              onChange={(event) => setQInput(event.target.value)}
              placeholder="Order no., name, phone, city, email"
              className="h-10 w-full rounded-xl border border-border bg-background py-2 pl-9 pr-3 text-sm"
            />
          </span>
        </label>
        <div className="ml-auto flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => void syncPending()}
            disabled={loading || busy !== null}
            className="inline-flex h-10 items-center gap-2 rounded-xl border border-border bg-background px-3 text-sm font-medium disabled:opacity-60"
          >
            {busy === "zoho:pending" ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <CloudUpload className="h-4 w-4" />
            )}
            {busy === "zoho:pending" ? "Syncing…" : "Sync pending to Zoho"}
          </button>
          <button
            type="button"
            onClick={() => void load()}
            disabled={loading}
            className="inline-flex h-10 items-center gap-2 rounded-xl border border-border bg-background px-3 text-sm font-medium disabled:opacity-60"
          >
            <RefreshCw className={`h-4 w-4 ${loading ? "animate-spin" : ""}`} /> Refresh
          </button>
        </div>
      </div>

      <div className="mb-5 flex flex-wrap items-end gap-3 rounded-2xl border border-border/70 bg-card/70 p-4">
        <FilterSelect
          label="Order status"
          value={status}
          onChange={setStatus}
          options={[
            { value: "", label: "All statuses" },
            { value: "pending", label: "Pending" },
            { value: "confirmed", label: "Confirmed" },
            { value: "fulfilled", label: "Delivered" },
            { value: "cancelled", label: "Cancelled" },
          ]}
        />
        <FilterSelect
          label="Payment"
          value={paymentStatus}
          onChange={setPaymentStatus}
          options={[
            { value: "", label: "All payments" },
            { value: "pending", label: "Pending" },
            { value: "paid", label: "Paid" },
            { value: "failed", label: "Failed" },
            { value: "refunded", label: "Refunded" },
          ]}
        />
        <FilterSelect
          label="Method"
          value={paymentMethod}
          onChange={setPaymentMethod}
          options={[
            { value: "", label: "All methods" },
            { value: "online", label: "Online" },
            { value: "cod", label: "COD" },
          ]}
        />
        <FilterSelect
          label="Zoho"
          value={zohoSyncStatus}
          onChange={setZohoSyncStatus}
          options={[
            { value: "", label: "All Zoho" },
            { value: "attention", label: "Needs sync" },
            { value: "not_synced", label: "Not synced" },
            { value: "pending", label: "Pending" },
            { value: "synced", label: "Synced" },
            { value: "failed", label: "Failed" },
          ]}
        />
        <FilterSelect
          label="Ledgerly"
          value={ledgerlySyncStatus}
          onChange={setLedgerlySyncStatus}
          options={[
            { value: "", label: "All Ledgerly" },
            { value: "pending", label: "Pending" },
            { value: "synced", label: "Synced" },
            { value: "failed", label: "Failed" },
          ]}
        />
        {filtersActive && (
          <button
            type="button"
            onClick={() => {
              setPeriod("all");
              setQInput("");
              setQ("");
              setStatus("");
              setPaymentStatus("");
              setPaymentMethod("");
              setZohoSyncStatus("");
              setLedgerlySyncStatus("");
              setSelectedDate(localDateValue(new Date()));
            }}
            className="inline-flex h-10 items-center gap-1.5 rounded-xl border border-border bg-background px-3 text-sm font-medium"
          >
            <X className="h-4 w-4" /> Clear filters
          </button>
        )}
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
                ? "Eligible orders on this page are synced to Zoho"
                : `${attentionOrders.length} ${attentionOrders.length === 1 ? "order on this page needs" : "orders on this page need"} Zoho sync attention`}
            </p>
            <div className="ml-auto flex gap-2 text-xs text-muted-foreground">
              <span className="rounded-full bg-white/70 px-2.5 py-1">
                Zoho {zohoSynced}/{zohoEligible.length} on this page
              </span>
            </div>
          </div>
        </div>
      )}

      {loading ? (
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      ) : rows.length === 0 ? (
        <p className="text-sm text-muted-foreground">No orders match the current filters.</p>
      ) : (
        <ul id="admin-orders-list" className="space-y-4">
          {rows.map((order) => {
            const isPendingCod =
              order.payment_method === "cod" && order.payment_status === "pending";
            const alreadySynced = normalizedStatus(order.zoho_sync_status) === "synced";
            const canSync = order.zoho_sync_eligible;
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
                    <Link
                      to="/admin/orders/$orderNumber/invoice"
                      params={{ orderNumber: order.order_number }}
                      className="mt-2 ml-2 inline-flex items-center gap-1.5 rounded-full border border-border bg-background px-3 py-1.5 text-xs font-medium"
                    >
                      <FileText className="h-3.5 w-3.5" /> View invoice
                    </Link>
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
                  <button
                    type="button"
                    disabled={busy !== null || !canSync}
                    title={
                      canSync
                        ? alreadySynced
                          ? "Push this order to Zoho Books again"
                          : "Create the sales order and invoice in Zoho Books"
                        : "Available after payment (or once a COD order is confirmed)"
                    }
                    onClick={() => void sync(order.order_number)}
                    className="mt-2 inline-flex items-center gap-1.5 rounded-full border border-border bg-background px-3 py-1.5 text-xs font-medium disabled:opacity-60"
                  >
                    {busy === `zoho:${order.order_number}` ? (
                      <Loader2 className="h-3.5 w-3.5 animate-spin" />
                    ) : (
                      <CloudUpload className="h-3.5 w-3.5" />
                    )}
                    {busy === `zoho:${order.order_number}`
                      ? "Syncing…"
                      : alreadySynced
                        ? "Re-sync to Zoho"
                        : "Sync to Zoho"}
                  </button>
                </div>
              </li>
            );
          })}
        </ul>
      )}
      {!loading && totalItems > 0 && (
        <ListPagination
          page={page}
          totalPages={totalPages}
          totalItems={totalItems}
          pageSize={LIST_PAGE_SIZE}
          scrollToId="admin-orders-list"
          onPageChange={setPage}
        />
      )}
    </AdminPage>
  );
}
