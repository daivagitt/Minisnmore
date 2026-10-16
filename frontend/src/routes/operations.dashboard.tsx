import { createFileRoute, Link } from "@tanstack/react-router";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AdminPage } from "@/components/admin/AdminPage";
import { formatOrderDate } from "@/lib/dates";
import { formatInr } from "@/lib/money";
import { formatAdminPaymentStatusLabel, formatOrderStatusLabel, isCodOrder } from "@/lib/order-status";
import {
  listAdminOrders,
  listCodPendingOrders,
  type AdminCodOrderRow,
  type AdminOrderRow,
} from "@/lib/orders.functions";
import { showApiError } from "@/lib/ui-errors";
import {
  AlertCircle,
  ArrowRight,
  Banknote,
  Calendar,
  CheckCircle2,
  Clock,
  CreditCard,
  ExternalLink,
  FileText,
  Filter,
  Package,
  PackageCheck,
  RefreshCw,
  RotateCcw,
  Search,
  Truck,
  X,
  type LucideIcon,
} from "lucide-react";
import { toast } from "sonner";

export const Route = createFileRoute("/operations/dashboard")({
  head: () => ({ meta: [{ title: "Operations Dashboard · Mini's n More" }] }),
  component: OperationsDashboard,
});

export type OperationsPeriod = "all" | "today" | "yesterday" | "7d" | "30d" | "custom";

export function getOperationsDateRange(
  period: OperationsPeriod,
  customFrom?: string,
  customTo?: string,
): { from?: string; to?: string; label: string } {
  const now = new Date();
  if (period === "all") {
    return { from: undefined, to: undefined, label: "All time" };
  }
  if (period === "today") {
    const start = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0, 0, 0);
    const end = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1, 0, 0, 0, 0);
    return { from: start.toISOString(), to: end.toISOString(), label: "Today" };
  }
  if (period === "yesterday") {
    const start = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1, 0, 0, 0, 0);
    const end = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0, 0, 0);
    return { from: start.toISOString(), to: end.toISOString(), label: "Yesterday" };
  }
  if (period === "7d") {
    const start = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 7, 0, 0, 0, 0);
    const end = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1, 0, 0, 0, 0);
    return { from: start.toISOString(), to: end.toISOString(), label: "Last 7 days" };
  }
  if (period === "30d") {
    const start = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 30, 0, 0, 0, 0);
    const end = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1, 0, 0, 0, 0);
    return { from: start.toISOString(), to: end.toISOString(), label: "Last 30 days" };
  }
  if (period === "custom") {
    if (!customFrom || !customTo) {
      return { from: undefined, to: undefined, label: "Custom range" };
    }
    const [fy, fm, fd] = customFrom.split("-").map(Number);
    const [ty, tm, td] = customTo.split("-").map(Number);
    const start = new Date(fy, fm - 1, fd, 0, 0, 0, 0);
    const end = new Date(ty, tm - 1, td + 1, 0, 0, 0, 0);
    return {
      from: start.toISOString(),
      to: end.toISOString(),
      label: `${customFrom} – ${customTo}`,
    };
  }
  return { from: undefined, to: undefined, label: "All time" };
}

const PERIOD_OPTIONS: { id: OperationsPeriod; label: string }[] = [
  { id: "all", label: "All time" },
  { id: "today", label: "Today" },
  { id: "yesterday", label: "Yesterday" },
  { id: "7d", label: "Last 7 days" },
  { id: "30d", label: "Last 30 days" },
  { id: "custom", label: "Custom" },
];

const STATUS_FILTERS = [
  { id: "", label: "All statuses" },
  { id: "pending", label: "Pending payment" },
  { id: "confirmed", label: "Confirmed (Ready to pack)" },
  { id: "hand_wrapped", label: "Hand-wrapped (Packed)" },
  { id: "fulfilled", label: "Delivered" },
  { id: "cancelled", label: "Cancelled" },
];

const PAYMENT_METHOD_FILTERS = [
  { id: "", label: "All payment methods" },
  { id: "cod", label: "Cash on Delivery (COD)" },
  { id: "prepaid", label: "Prepaid / Online" },
];

const PAYMENT_STATUS_FILTERS = [
  { id: "", label: "All payment statuses" },
  { id: "paid", label: "Paid" },
  { id: "pending", label: "Pending" },
  { id: "failed", label: "Failed" },
  { id: "refund_initiated", label: "Refund initiated" },
  { id: "refund_failed", label: "Refund failed" },
  { id: "refunded", label: "Refunded" },
];

export function OperationsDashboard() {
  const [period, setPeriod] = useState<OperationsPeriod>("all");
  const [customFrom, setCustomFrom] = useState("");
  const [customTo, setCustomTo] = useState("");
  const [status, setStatus] = useState("");
  const [paymentMethod, setPaymentMethod] = useState("");
  const [paymentStatus, setPaymentStatus] = useState("");
  const [searchQuery, setSearchQuery] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [page, setPage] = useState(1);
  const pageSize = 10;

  // Data states
  const [orders, setOrders] = useState<AdminOrderRow[]>([]);
  const [totalItems, setTotalItems] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [counts, setCounts] = useState({
    all: 0,
    pending: 0,
    confirmed: 0,
    handWrapped: 0,
    fulfilled: 0,
    cancelled: 0,
  });
  const [codPendingOrders, setCodPendingOrders] = useState<AdminCodOrderRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [metricsLoading, setMetricsLoading] = useState(true);

  // Debounce search query
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(searchQuery.trim());
      setPage(1);
    }, 300);
    return () => clearTimeout(timer);
  }, [searchQuery]);

  // Compute date range
  const dateRange = useMemo(
    () => getOperationsDateRange(period, customFrom, customTo),
    [period, customFrom, customTo],
  );

  // Load KPI stage counts
  const loadMetrics = useCallback(async () => {
    setMetricsLoading(true);
    try {
      const from = dateRange.from;
      const to = dateRange.to;
      const [allRes, pendingRes, confirmedRes, handWrappedRes, fulfilledRes, codRes] =
        await Promise.all([
          listAdminOrders(from, to, {
            paymentMethod: paymentMethod || undefined,
            paymentStatus: paymentStatus || undefined,
            page: 1,
            size: 1,
          }),
          listAdminOrders(from, to, {
            status: "pending",
            paymentMethod: paymentMethod || undefined,
            paymentStatus: paymentStatus || undefined,
            page: 1,
            size: 1,
          }),
          listAdminOrders(from, to, {
            status: "confirmed",
            paymentMethod: paymentMethod || undefined,
            paymentStatus: paymentStatus || undefined,
            page: 1,
            size: 1,
          }),
          listAdminOrders(from, to, {
            status: "hand_wrapped",
            paymentMethod: paymentMethod || undefined,
            paymentStatus: paymentStatus || undefined,
            page: 1,
            size: 1,
          }),
          listAdminOrders(from, to, {
            status: "fulfilled",
            paymentMethod: paymentMethod || undefined,
            paymentStatus: paymentStatus || undefined,
            page: 1,
            size: 1,
          }),
          listCodPendingOrders().catch(() => ({ orders: [] })),
        ]);

      setCounts({
        all: allRes.totalItems,
        pending: pendingRes.totalItems,
        confirmed: confirmedRes.totalItems,
        handWrapped: handWrappedRes.totalItems,
        fulfilled: fulfilledRes.totalItems,
        cancelled: Math.max(
          0,
          allRes.totalItems -
            (pendingRes.totalItems +
              confirmedRes.totalItems +
              handWrappedRes.totalItems +
              fulfilledRes.totalItems),
        ),
      });
      setCodPendingOrders(codRes.orders ?? []);
    } catch (err) {
      showApiError(err, "Could not load pipeline counts");
    } finally {
      setMetricsLoading(false);
    }
  }, [dateRange.from, dateRange.to, paymentMethod, paymentStatus]);

  // Load orders queue
  const loadOrders = useCallback(async () => {
    setLoading(true);
    try {
      const res = await listAdminOrders(dateRange.from, dateRange.to, {
        page,
        size: pageSize,
        q: debouncedSearch || undefined,
        status: status || undefined,
        paymentMethod: paymentMethod || undefined,
        paymentStatus: paymentStatus || undefined,
      });
      setOrders(res.orders ?? []);
      setTotalItems(res.totalItems ?? 0);
      setTotalPages(Math.max(1, res.totalPages ?? 1));
    } catch (err) {
      showApiError(err, "Could not load operations orders");
    } finally {
      setLoading(false);
    }
  }, [dateRange.from, dateRange.to, page, debouncedSearch, status, paymentMethod, paymentStatus]);

  useEffect(() => {
    void loadMetrics();
  }, [loadMetrics]);

  useEffect(() => {
    void loadOrders();
  }, [loadOrders]);

  const hasActiveFilters = Boolean(
    period !== "all" ||
      status ||
      paymentMethod ||
      paymentStatus ||
      debouncedSearch ||
      customFrom ||
      customTo,
  );

  const clearAllFilters = () => {
    setPeriod("all");
    setCustomFrom("");
    setCustomTo("");
    setStatus("");
    setPaymentMethod("");
    setPaymentStatus("");
    setSearchQuery("");
    setDebouncedSearch("");
    setPage(1);
  };

  const handleRefresh = () => {
    void loadMetrics();
    void loadOrders();
    toast.success("Operations dashboard updated");
  };

  const metricCards: {
    key: string;
    label: string;
    sublabel: string;
    value: number;
    statusVal: string;
    icon: LucideIcon;
    colorClasses: string;
    badgeClasses: string;
  }[] = [
    {
      key: "pending",
      label: "Pending",
      sublabel: "Awaiting payment",
      value: counts.pending,
      statusVal: "pending",
      icon: Clock,
      colorClasses: "border-amber-200/60 dark:border-amber-900/50 bg-amber-50/40 dark:bg-amber-950/20",
      badgeClasses: "bg-amber-100 text-amber-800 dark:bg-amber-900/60 dark:text-amber-200",
    },
    {
      key: "confirmed",
      label: "Confirmed",
      sublabel: "Ready to pack",
      value: counts.confirmed,
      statusVal: "confirmed",
      icon: Package,
      colorClasses: "border-sky-200/60 dark:border-sky-900/50 bg-sky-50/40 dark:bg-sky-950/20",
      badgeClasses: "bg-sky-100 text-sky-800 dark:bg-sky-900/60 dark:text-sky-200",
    },
    {
      key: "hand_wrapped",
      label: "Hand-wrapped",
      sublabel: "Packed & awaiting dispatch",
      value: counts.handWrapped,
      statusVal: "hand_wrapped",
      icon: PackageCheck,
      colorClasses: "border-indigo-200/60 dark:border-indigo-900/50 bg-indigo-50/40 dark:bg-indigo-950/20",
      badgeClasses: "bg-indigo-100 text-indigo-800 dark:bg-indigo-900/60 dark:text-indigo-200",
    },
    {
      key: "fulfilled",
      label: "Delivered",
      sublabel: "Fulfilled orders",
      value: counts.fulfilled,
      statusVal: "fulfilled",
      icon: CheckCircle2,
      colorClasses: "border-emerald-200/60 dark:border-emerald-900/50 bg-emerald-50/40 dark:bg-emerald-950/20",
      badgeClasses: "bg-emerald-100 text-emerald-800 dark:bg-emerald-900/60 dark:text-emerald-200",
    },
  ];

  return (
    <AdminPage
      eyebrow="Operations"
      title="Fulfillment Dashboard"
      description="Live operational pipeline, fulfillment filters, and action dispatch queue."
      action={
        <div className="flex flex-wrap items-center gap-2">
          <button
            type="button"
            onClick={handleRefresh}
            className="inline-flex items-center gap-1.5 rounded-full border border-border px-3.5 py-1.5 text-xs font-medium hover:bg-muted/80 transition"
          >
            <RefreshCw className="h-3.5 w-3.5" /> Refresh
          </button>
          <Link
            to="/operations/orders"
            className="inline-flex items-center gap-1.5 rounded-full bg-primary text-primary-foreground px-4 py-2 text-xs font-semibold hover:opacity-90 transition shadow-sm"
          >
            Open All Orders <ArrowRight className="h-3.5 w-3.5" />
          </Link>
        </div>
      }
    >
      {/* COD & Urgent Attention Banner */}
      {codPendingOrders.length > 0 && (
        <div className="glass-strong rounded-2xl p-4 border border-amber-300/60 bg-amber-500/10 flex flex-wrap items-center justify-between gap-3 text-sm">
          <div className="flex items-center gap-3">
            <div className="grid h-9 w-9 place-items-center rounded-xl bg-amber-500 text-white font-bold shadow-sm">
              <Banknote className="h-5 w-5" />
            </div>
            <div>
              <div className="font-semibold text-amber-950 dark:text-amber-100">
                {codPendingOrders.length} Cash on Delivery {codPendingOrders.length === 1 ? "order" : "orders"} pending payment collection
              </div>
              <p className="text-xs text-amber-900/80 dark:text-amber-300">
                Collect and confirm payments once the courier completes doorstep delivery.
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={() => {
              setPaymentMethod("cod");
              setPaymentStatus("pending");
              setStatus("");
              setPage(1);
            }}
            className="rounded-full bg-amber-600 text-white px-3.5 py-1.5 text-xs font-medium hover:bg-amber-700 transition"
          >
            Filter COD Pending Orders
          </button>
        </div>
      )}

      {/* Modern Filter Controls Bar */}
      <section className="glass-strong rounded-[1.75rem] p-5 space-y-4 border border-border/70 shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-3 pb-3 border-b border-border/50">
          <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
            <Filter className="h-4 w-4 text-primary" />
            <span>Fulfillment &amp; Time Filters</span>
            {hasActiveFilters && (
              <span className="rounded-full bg-primary/10 text-primary px-2.5 py-0.5 text-xs font-medium">
                Active filters
              </span>
            )}
          </div>
          {hasActiveFilters && (
            <button
              type="button"
              onClick={clearAllFilters}
              className="inline-flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground transition underline underline-offset-4"
            >
              <RotateCcw className="h-3 w-3" /> Reset all filters
            </button>
          )}
        </div>

        {/* Period Preset Pills */}
        <div className="flex flex-wrap items-center gap-1.5">
          <span className="text-xs font-semibold uppercase tracking-wider text-muted-foreground mr-1.5 flex items-center gap-1">
            <Calendar className="h-3.5 w-3.5" /> Window:
          </span>
          {PERIOD_OPTIONS.map((opt) => {
            const active = period === opt.id;
            return (
              <button
                key={opt.id}
                type="button"
                onClick={() => {
                  setPeriod(opt.id);
                  setPage(1);
                }}
                className={`rounded-full px-3.5 py-1.5 text-xs font-medium transition ${
                  active
                    ? "bg-primary text-primary-foreground shadow-sm font-semibold"
                    : "border border-border/80 text-muted-foreground hover:text-foreground hover:bg-muted/60"
                }`}
              >
                {opt.label}
              </button>
            );
          })}
        </div>

        {/* Custom Date Range Picker when Custom is chosen */}
        {period === "custom" && (
          <div className="flex flex-wrap items-center gap-3 pt-1">
            <label className="text-xs flex items-center gap-1.5">
              <span className="text-muted-foreground font-medium">From:</span>
              <input
                type="date"
                value={customFrom}
                onChange={(e) => {
                  setCustomFrom(e.target.value);
                  setPage(1);
                }}
                className="rounded-xl border border-border bg-background px-2.5 py-1 text-xs"
              />
            </label>
            <label className="text-xs flex items-center gap-1.5">
              <span className="text-muted-foreground font-medium">To:</span>
              <input
                type="date"
                value={customTo}
                onChange={(e) => {
                  setCustomTo(e.target.value);
                  setPage(1);
                }}
                className="rounded-xl border border-border bg-background px-2.5 py-1 text-xs"
              />
            </label>
            <span className="text-[11px] text-muted-foreground">Range up to 31 days</span>
          </div>
        )}

        {/* Filter Dropdowns & Live Search */}
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4 pt-1">
          {/* Search box */}
          <label className="relative block">
            <span className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground mb-1 block">
              Search queue
            </span>
            <div className="relative">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
              <input
                type="search"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Order #, customer, phone…"
                className="h-9 w-full rounded-xl border border-border bg-background pl-8 pr-7 text-xs outline-none focus:ring-2 focus:ring-primary/30"
              />
              {searchQuery && (
                <button
                  type="button"
                  onClick={() => setSearchQuery("")}
                  className="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                >
                  <X className="h-3 w-3" />
                </button>
              )}
            </div>
          </label>

          {/* Status Select */}
          <label className="block">
            <span className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground mb-1 block">
              Fulfillment Status
            </span>
            <select
              value={status}
              onChange={(e) => {
                setStatus(e.target.value);
                setPage(1);
              }}
              className="h-9 w-full rounded-xl border border-border bg-background px-2.5 text-xs outline-none focus:ring-2 focus:ring-primary/30"
            >
              {STATUS_FILTERS.map((f) => (
                <option key={f.id} value={f.id}>
                  {f.label}
                </option>
              ))}
            </select>
          </label>

          {/* Payment Method Select */}
          <label className="block">
            <span className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground mb-1 block">
              Payment Method
            </span>
            <select
              value={paymentMethod}
              onChange={(e) => {
                setPaymentMethod(e.target.value);
                setPage(1);
              }}
              className="h-9 w-full rounded-xl border border-border bg-background px-2.5 text-xs outline-none focus:ring-2 focus:ring-primary/30"
            >
              {PAYMENT_METHOD_FILTERS.map((f) => (
                <option key={f.id} value={f.id}>
                  {f.label}
                </option>
              ))}
            </select>
          </label>

          {/* Payment Status Select */}
          <label className="block">
            <span className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground mb-1 block">
              Payment Status
            </span>
            <select
              value={paymentStatus}
              onChange={(e) => {
                setPaymentStatus(e.target.value);
                setPage(1);
              }}
              className="h-9 w-full rounded-xl border border-border bg-background px-2.5 text-xs outline-none focus:ring-2 focus:ring-primary/30"
            >
              {PAYMENT_STATUS_FILTERS.map((f) => (
                <option key={f.id} value={f.id}>
                  {f.label}
                </option>
              ))}
            </select>
          </label>
        </div>
      </section>

      {/* KPI Metric Cards */}
      <section>
        <div className="grid gap-3.5 sm:grid-cols-2 lg:grid-cols-4">
          {metricCards.map((card) => {
            const Icon = card.icon;
            const isSelected = status === card.statusVal;
            return (
              <button
                key={card.key}
                type="button"
                onClick={() => {
                  setStatus(isSelected ? "" : card.statusVal);
                  setPage(1);
                }}
                className={`relative overflow-hidden rounded-2xl p-5 text-left border transition-all duration-200 shadow-sm ${
                  isSelected
                    ? "ring-2 ring-primary ring-offset-2 " + card.colorClasses
                    : "glass-strong hover:border-primary/50 hover:translate-y-[-2px]"
                }`}
              >
                <div className="flex items-center justify-between">
                  <span className={`rounded-lg p-2 text-xs font-semibold ${card.badgeClasses}`}>
                    <Icon className="h-4 w-4" />
                  </span>
                  <span className="text-[11px] font-medium text-muted-foreground">
                    {dateRange.label}
                  </span>
                </div>
                <div className="mt-3">
                  <p className="font-display text-3xl font-semibold">
                    {metricsLoading ? "…" : card.value}
                  </p>
                  <p className="text-xs font-semibold uppercase tracking-wider text-foreground mt-1">
                    {card.label}
                  </p>
                  <p className="text-[11px] text-muted-foreground mt-0.5">{card.sublabel}</p>
                </div>
                {isSelected && (
                  <div className="absolute top-2 right-2 text-primary">
                    <CheckCircle2 className="h-4 w-4" />
                  </div>
                )}
              </button>
            );
          })}
        </div>
      </section>

      {/* Filtered Orders Queue Table */}
      <section className="space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 className="font-display text-xl font-semibold">Fulfillment Dispatch Queue</h2>
            <p className="text-xs text-muted-foreground mt-0.5">
              Showing {orders.length} of {totalItems} orders matching current filter criteria.
            </p>
          </div>
          <Link
            to="/operations/orders"
            className="text-xs text-primary font-medium hover:underline inline-flex items-center gap-1"
          >
            Manage full order list <ExternalLink className="h-3 w-3" />
          </Link>
        </div>

        {loading ? (
          <div className="glass-strong rounded-2xl p-12 text-center text-sm text-muted-foreground">
            <RefreshCw className="h-6 w-6 animate-spin mx-auto mb-2 text-primary" />
            Loading operational orders…
          </div>
        ) : orders.length === 0 ? (
          <div className="glass-strong rounded-2xl p-12 text-center space-y-3">
            <div className="grid h-12 w-12 place-items-center rounded-2xl bg-muted/60 text-muted-foreground mx-auto">
              <Package className="h-6 w-6" />
            </div>
            <p className="font-display text-lg font-semibold">No orders found</p>
            <p className="text-xs text-muted-foreground max-w-sm mx-auto">
              No orders matched your selected filters or search terms. Try widening the date window or clearing specific filters.
            </p>
            {hasActiveFilters && (
              <button
                type="button"
                onClick={clearAllFilters}
                className="rounded-full bg-primary text-primary-foreground px-4 py-2 text-xs font-medium hover:opacity-90 transition"
              >
                Clear all filters
              </button>
            )}
          </div>
        ) : (
          <div className="overflow-x-auto rounded-2xl border border-border shadow-sm">
            <table className="w-full text-left text-sm">
              <thead className="bg-muted/40 text-[11px] uppercase tracking-wider text-muted-foreground">
                <tr>
                  <th className="px-4 py-3">Order # &amp; Date</th>
                  <th className="px-4 py-3">Customer</th>
                  <th className="px-4 py-3">Items</th>
                  <th className="px-4 py-3">Amount &amp; Mode</th>
                  <th className="px-4 py-3">Fulfillment Status</th>
                  <th className="px-4 py-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border/60">
                {orders.map((o) => {
                  const isCod = isCodOrder(o);
                  const itemsCount = o.order_items?.length ?? 1;
                  const firstItem = o.order_items?.[0];
                  const hasAwb = Boolean(o.tracking_number);

                  return (
                    <tr key={o.id} className="hover:bg-muted/20 transition-colors">
                      <td className="px-4 py-3">
                        <Link
                          to="/operations/orders"
                          className="font-mono text-xs font-semibold text-primary hover:underline block"
                        >
                          {o.order_number}
                        </Link>
                        <span className="text-[11px] text-muted-foreground block mt-0.5">
                          {formatOrderDate(o.placed_at, {
                            day: "numeric",
                            month: "short",
                            year: "numeric",
                            hour: "2-digit",
                            minute: "2-digit",
                          })}
                        </span>
                      </td>

                      <td className="px-4 py-3">
                        <div className="font-medium text-xs text-foreground">
                          {o.shipping_address?.full_name || "Customer"}
                        </div>
                        <div className="text-[11px] text-muted-foreground mt-0.5">
                          {[o.shipping_address?.city, o.shipping_address?.pincode]
                            .filter(Boolean)
                            .join(", ") || o.shipping_address?.phone || "—"}
                        </div>
                      </td>

                      <td className="px-4 py-3">
                        <span className="inline-flex items-center gap-1 rounded-md bg-muted px-2 py-0.5 text-xs font-medium">
                          {itemsCount} {itemsCount === 1 ? "item" : "items"}
                        </span>
                        {firstItem && (
                          <span className="text-[11px] text-muted-foreground block mt-0.5 line-clamp-1 max-w-[12rem]">
                            {firstItem.product_name}
                          </span>
                        )}
                      </td>

                      <td className="px-4 py-3">
                        <div className="font-semibold text-xs text-foreground">
                          ₹{formatInr(o.total)}
                        </div>
                        <div className="flex items-center gap-1.5 mt-1">
                          <span
                            className={`rounded px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-wider ${
                              isCod
                                ? "bg-amber-100 text-amber-900 dark:bg-amber-950/80 dark:text-amber-300"
                                : "bg-sky-100 text-sky-900 dark:bg-sky-950/80 dark:text-sky-300"
                            }`}
                          >
                            {isCod ? "COD" : "Prepaid"}
                          </span>
                          <span className="text-[10px] text-muted-foreground">
                            {formatAdminPaymentStatusLabel(o.payment_status)}
                          </span>
                        </div>
                      </td>

                      <td className="px-4 py-3">
                        <div className="inline-flex items-center gap-1.5">
                          <span className="rounded-full bg-primary/10 text-primary px-2.5 py-1 text-[11px] font-semibold">
                            {formatOrderStatusLabel(o.status)}
                          </span>
                        </div>
                        <div className="text-[11px] text-muted-foreground mt-1 flex items-center gap-1">
                          {hasAwb ? (
                            <span className="text-emerald-700 dark:text-emerald-400 font-medium inline-flex items-center gap-1">
                              <Truck className="h-3 w-3" /> {o.tracking_carrier || "AWB"}: {o.tracking_number}
                            </span>
                          ) : (
                            <span className="text-amber-700 dark:text-amber-400 inline-flex items-center gap-1">
                              <Clock className="h-3 w-3" /> Awaiting booking
                            </span>
                          )}
                        </div>
                      </td>

                      <td className="px-4 py-3 text-right">
                        <div className="flex items-center justify-end gap-1.5">
                          <a
                            href={`/operations/orders/${o.order_number}/invoice`}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="inline-flex items-center gap-1 rounded-lg border border-border px-2.5 py-1 text-xs text-muted-foreground hover:text-foreground hover:bg-muted/80 transition"
                            title="Print invoice"
                          >
                            <FileText className="h-3.5 w-3.5" />
                          </a>
                          <Link
                            to="/operations/orders"
                            className="inline-flex items-center gap-1 rounded-lg bg-primary text-primary-foreground px-3 py-1 text-xs font-medium hover:opacity-90 transition"
                          >
                            Fulfill <ArrowRight className="h-3 w-3" />
                          </Link>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>

            {/* Pagination Controls */}
            {totalPages > 1 && (
              <div className="flex flex-wrap items-center justify-between gap-3 p-3 bg-muted/20 border-t border-border/70 text-xs">
                <span className="text-muted-foreground">
                  Page {page} of {totalPages} ({totalItems} orders)
                </span>
                <div className="flex items-center gap-1.5">
                  <button
                    type="button"
                    disabled={page <= 1}
                    onClick={() => setPage((p) => Math.max(1, p - 1))}
                    className="rounded-lg border border-border px-3 py-1 text-xs disabled:opacity-40 hover:bg-muted transition"
                  >
                    Previous
                  </button>
                  <button
                    type="button"
                    disabled={page >= totalPages}
                    onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
                    className="rounded-lg border border-border px-3 py-1 text-xs disabled:opacity-40 hover:bg-muted transition"
                  >
                    Next
                  </button>
                </div>
              </div>
            )}
          </div>
        )}
      </section>
    </AdminPage>
  );
}
