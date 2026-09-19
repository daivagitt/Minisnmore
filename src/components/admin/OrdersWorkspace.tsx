import { Link } from "@tanstack/react-router";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AdminPage } from "@/components/admin/AdminPage";
import { AlertCircle, CheckCircle2, CloudUpload, FileText, Loader2, Package, Printer, RefreshCw, Search, Truck, X } from "lucide-react";
import { toast } from "sonner";
import {
  bookAdminOrderShipment,
  cancelAdminOrderShipment,
  completeAdminOrderPackage,
  listAdminOrders,
  markCodCollected,
  previewAdminOrderPackageRates,
  type AdminOrderRow,
  type PackageRatesResult,
} from "@/lib/orders.functions";
import {
  formatZohoSyncFailures,
  formatZohoSyncSummary,
  retryZohoOrders,
  syncZohoOrderByNumber,
} from "@/lib/backend-data";
import { showApiError } from "@/lib/ui-errors";
import { ListPagination } from "@/components/ListPagination";
import { LIST_PAGE_SIZE } from "@/lib/pagination";
import { isInvoiceAvailable } from "@/lib/invoice-availability";
import {
  convertLengthInput,
  convertWeightInput,
  LENGTH_UNITS,
  parsePackageDimensionCm,
  parsePackageWeightGrams,
  WEIGHT_UNITS,
  type LengthUnit,
  type WeightUnit,
} from "@/lib/package-units";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";

export type OrdersWorkspaceMode = "admin" | "operations" | "support";

type Period = "all" | "day" | "week";

const ORDER_STATUS_FILTERS = [
  { value: "", label: "All" },
  { value: "pending", label: "Pending" },
  { value: "confirmed", label: "Confirmed" },
  { value: "hand_wrapped", label: "Hand-wrapped" },
  { value: "fulfilled", label: "Delivered" },
  { value: "cancelled", label: "Cancelled" },
] as const;

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

function displayPaymentStatus(value: string | null | undefined) {
  return value?.trim().toLowerCase() === "refunded" ? "settled" : value || "pending";
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

export function OrdersWorkspace({
  mode,
  invoiceHref,
}: {
  mode: OrdersWorkspaceMode;
  invoiceHref: (orderNumber: string) => string;
}) {
  const canFulfill = mode === "admin" || mode === "operations";
  const canSyncZoho = mode === "admin";
  const [rows, setRows] = useState<AdminOrderRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);
  const [bookTarget, setBookTarget] = useState<string | null>(null);
  const [bookMode, setBookMode] = useState<"xpressbees" | "manual">("xpressbees");
  const [bookCarrier, setBookCarrier] = useState("");
  const [bookAwb, setBookAwb] = useState("");
  const [bookTrackingUrl, setBookTrackingUrl] = useState("");
  const [bookLabelUrl, setBookLabelUrl] = useState("");
  const [bookRequiresOverride, setBookRequiresOverride] = useState(false);
  const [bookOverrideReason, setBookOverrideReason] = useState("");
  const [packageTarget, setPackageTarget] = useState<AdminOrderRow | null>(null);
  const [packageWeight, setPackageWeight] = useState("500");
  const [packageWeightUnit, setPackageWeightUnit] = useState<WeightUnit>("g");
  const [packageLength, setPackageLength] = useState("");
  const [packageBreadth, setPackageBreadth] = useState("");
  const [packageHeight, setPackageHeight] = useState("");
  const [packageLengthUnit, setPackageLengthUnit] = useState<LengthUnit>("cm");
  const [packageRates, setPackageRates] = useState<PackageRatesResult | null>(null);
  const [selectedCourierId, setSelectedCourierId] = useState("");
  const [cancelShipmentTarget, setCancelShipmentTarget] = useState<AdminOrderRow | null>(null);
  const [cancelShipmentMode, setCancelShipmentMode] = useState<"xpressbees" | "manual">("xpressbees");
  const [cancelShipmentReason, setCancelShipmentReason] = useState("");
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
  const range = useMemo(() => selectedRange(selectedDate, period), [selectedDate, period]);
  const loadGen = useRef(0);
  const filtersKey = `${period}:${selectedDate}:${q}:${status}:${paymentStatus}:${paymentMethod}:${zohoSyncStatus}`;
  const filtersActive = Boolean(
    q || status || paymentStatus || paymentMethod || zohoSyncStatus || period !== "all",
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
  }, [range.from, range.to, page, q, status, paymentStatus, paymentMethod, zohoSyncStatus]);

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

  const submitBookShipment = async () => {
    if (!bookTarget) return;
    if (bookMode !== "xpressbees" && bookAwb.trim().length < 3) {
      toast.error("AWB or tracking reference is required");
      return;
    }
    if (bookRequiresOverride && bookOverrideReason.trim().length < 5) {
      toast.error("Override reason must be at least 5 characters");
      return;
    }
    setBusy(`ship:${bookTarget}`);
    try {
      await bookAdminOrderShipment(bookTarget, {
        mode: bookMode,
        carrier_name: bookCarrier.trim() || undefined,
        awb: bookAwb.trim() || undefined,
        tracking_url: bookTrackingUrl.trim() || undefined,
        label_url: bookLabelUrl.trim() || undefined,
        override_fulfillment_hold: bookRequiresOverride || undefined,
        override_reason: bookRequiresOverride ? bookOverrideReason.trim() : undefined,
      });
      toast.success(bookMode === "xpressbees" ? `${bookTarget} booked with ExpressBees` : `${bookTarget} shipment recorded`);
      setBookTarget(null);
      setBookCarrier("");
      setBookAwb("");
      setBookTrackingUrl("");
      setBookLabelUrl("");
      setBookRequiresOverride(false);
      setBookOverrideReason("");
      await load();
    } catch (error) {
      showApiError(error, "Could not book shipment");
    } finally {
      setBusy(null);
    }
  };

  const openPackageDialog = (order: AdminOrderRow) => {
    setPackageTarget(order);
    setPackageWeight(String(order.package_weight_grams || order.default_package_weight_grams || 500));
    setPackageWeightUnit("g");
    setPackageLength(order.package_length_cm ? String(order.package_length_cm) : "");
    setPackageBreadth(order.package_breadth_cm ? String(order.package_breadth_cm) : "");
    setPackageHeight(order.package_height_cm ? String(order.package_height_cm) : "");
    setPackageLengthUnit("cm");
    setPackageRates(null);
    setSelectedCourierId(order.package_courier_id || "");
  };

  const parsedPackageDims = () => {
    const weight = parsePackageWeightGrams(packageWeight, packageWeightUnit);
    if (!weight.ok) {
      toast.error(weight.message);
      return null;
    }
    const length = parsePackageDimensionCm(packageLength, packageLengthUnit);
    const breadth = parsePackageDimensionCm(packageBreadth, packageLengthUnit);
    const height = parsePackageDimensionCm(packageHeight, packageLengthUnit);
    if (!length.ok || !breadth.ok || !height.ok) {
      toast.error(
        (!length.ok && length.message) ||
          (!breadth.ok && breadth.message) ||
          (!height.ok && height.message) ||
          "Enter length, breadth and height greater than 0",
      );
      return null;
    }
    return { weight_grams: weight.grams, length_cm: length.cm, breadth_cm: breadth.cm, height_cm: height.cm };
  };

  const packageExpressBeesPreview = useMemo(() => {
    if (packageWeightUnit === "g" && packageLengthUnit === "cm") return null;
    const weight = parsePackageWeightGrams(packageWeight, packageWeightUnit);
    const length = parsePackageDimensionCm(packageLength, packageLengthUnit);
    const breadth = parsePackageDimensionCm(packageBreadth, packageLengthUnit);
    const height = parsePackageDimensionCm(packageHeight, packageLengthUnit);
    if (!weight.ok || !length.ok || !breadth.ok || !height.ok) return null;
    return `${weight.grams} g · ${length.cm}×${breadth.cm}×${height.cm} cm`;
  }, [packageWeight, packageWeightUnit, packageLength, packageBreadth, packageHeight, packageLengthUnit]);

  const submitPackageRates = async () => {
    if (!packageTarget) return;
    const dims = parsedPackageDims();
    if (!dims) return;
    setBusy(`package-rates:${packageTarget.order_number}`);
    try {
      const result = await previewAdminOrderPackageRates(packageTarget.order_number, dims);
      setPackageRates(result);
      const preferred =
        result.couriers?.find((option) => option.preferred)?.id ||
        result.preferred_courier_id ||
        result.couriers?.[0]?.id ||
        "";
      setSelectedCourierId(preferred);
    } catch (error) {
      showApiError(error, "Could not calculate ExpressBees rates");
    } finally {
      setBusy(null);
    }
  };

  const submitCompletePackage = async () => {
    if (!packageTarget) return;
    const dims = parsedPackageDims();
    if (!dims) return;
    if (!packageRates) {
      toast.error("Calculate rates before completing wrap");
      return;
    }
    const selected = packageRates.couriers?.find((option) => option.id === selectedCourierId);
    setBusy(`package:${packageTarget.order_number}`);
    try {
      await completeAdminOrderPackage(packageTarget.order_number, {
        ...dims,
        courier_id: selected?.id || packageRates.preferred_courier_id || undefined,
        courier_name: selected?.name || packageRates.preferred_courier_name,
      });
      toast.success(`${packageTarget.order_number} marked hand-wrapped`);
      setPackageTarget(null);
      setPackageRates(null);
      await load();
    } catch (error) {
      showApiError(error, "Could not complete package");
    } finally {
      setBusy(null);
    }
  };

  const submitCancelShipment = async () => {
    if (!cancelShipmentTarget || cancelShipmentReason.trim().length < 5) return;
    setBusy(`cancel-ship:${cancelShipmentTarget.order_number}`);
    try {
      await cancelAdminOrderShipment(cancelShipmentTarget.order_number, {
        mode: cancelShipmentMode,
        reason: cancelShipmentReason.trim(),
      });
      toast.success(`${cancelShipmentTarget.order_number} shipment cancelled`);
      setCancelShipmentTarget(null);
      setCancelShipmentReason("");
      await load();
    } catch (error) {
      showApiError(error, "Could not cancel shipment");
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
      eyebrow={mode === "support" ? "Customer Support" : "Fulfilment"}
      title="Orders"
      description={
        mode === "support"
          ? "View and filter orders, inspect details, and print available invoices."
          : "Search orders, mark wrapping complete, book ExpressBees or a manual AWB, and sync with Zoho Books."
      }
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
          {canSyncZoho ? (
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
          ) : null}
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

      <div className="mb-5 rounded-2xl border border-border/70 bg-card/70 p-4">
        <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
          Order status
        </p>
        <div className="flex flex-wrap gap-2">
          {ORDER_STATUS_FILTERS.map((option) => (
            <button
              key={option.value || "all"}
              type="button"
              onClick={() => setStatus(option.value)}
              className={`rounded-full border px-3 py-1.5 text-xs transition ${
                status === option.value
                  ? "border-primary bg-primary text-primary-foreground"
                  : "border-border hover:bg-muted/60"
              }`}
            >
              {option.label}
            </button>
          ))}
        </div>

        <div className="mt-4 flex flex-wrap items-end gap-3">
        <FilterSelect
          label="Payment"
          value={paymentStatus}
          onChange={setPaymentStatus}
          options={[
            { value: "", label: "All payments" },
            { value: "pending", label: "Pending" },
            { value: "paid", label: "Paid" },
            { value: "failed", label: "Failed" },
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
              setSelectedDate(localDateValue(new Date()));
            }}
            className="inline-flex h-10 items-center gap-1.5 rounded-xl border border-border bg-background px-3 text-sm font-medium"
          >
            <X className="h-4 w-4" /> Clear filters
          </button>
        )}
        </div>
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
                        {(order.tracking_status || order.status || "pending").replaceAll("_", " ")}
                      </span>
                      <span className="rounded-full bg-muted px-2 py-0.5 text-[11px] font-medium uppercase">
                        {displayPaymentStatus(order.payment_status)}
                      </span>
                      <span className="text-[11px] font-medium uppercase text-muted-foreground">
                        {order.payment_method}
                      </span>
                      {order.coupon_code?.trim() && (
                        <span className="rounded-full bg-primary/15 px-2 py-0.5 text-[11px] font-semibold uppercase tracking-wide text-primary">
                          Coupon {order.coupon_code.trim()}
                        </span>
                      )}
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
                    {isPendingCod && canFulfill && (
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
                    {isInvoiceAvailable(order) && (
                      <a
                        href={invoiceHref(order.order_number)}
                        className="mt-2 ml-2 inline-flex items-center gap-1.5 rounded-full border border-border bg-background px-3 py-1.5 text-xs font-medium"
                      >
                        <FileText className="h-3.5 w-3.5" /> View invoice
                      </a>
                    )}
                  </div>
                </div>

                <div className="mt-3 rounded-xl border border-border/70 bg-background/60 p-3">
                  <div className="flex items-center justify-between gap-2">
                    <p className="text-sm font-semibold">Shipment</p>
                    {order.tracking_number ? (
                      <span className="rounded-full bg-emerald-100 px-2.5 py-1 text-[11px] font-semibold text-emerald-800">
                        {order.tracking_status === "delivered"
                          ? "Delivered"
                          : order.tracking_status === "shipped"
                            ? "In transit"
                            : "Pickup pending"}
                      </span>
                    ) : (
                      <span className="rounded-full bg-muted px-2.5 py-1 text-[11px] font-medium text-muted-foreground">
                        Not booked
                      </span>
                    )}
                  </div>
                  {order.tracking_number ? (
                    <div className="mt-2 space-y-1 text-xs text-muted-foreground">
                      <p>
                        {order.tracking_carrier || "Carrier"} · AWB {order.tracking_number}
                      </p>
                      {order.tracking_url ? (
                        <a
                          href={order.tracking_url}
                          target="_blank"
                          rel="noreferrer"
                          className="underline"
                        >
                          Open tracking
                        </a>
                      ) : null}
                    </div>
                  ) : (
                    <p className="mt-2 text-xs text-muted-foreground">
                      Create a package with dimensions to calculate ExpressBees rates, then book a shipment.
                    </p>
                  )}
                  {order.packed_at && !order.tracking_number ? (
                    <p className="mt-2 text-xs text-muted-foreground">
                      Packed {order.package_weight_grams || order.default_package_weight_grams} g
                      {order.package_length_cm && order.package_breadth_cm && order.package_height_cm
                        ? ` · ${order.package_length_cm}×${order.package_breadth_cm}×${order.package_height_cm} cm`
                        : ""}
                      {order.package_courier_name ? ` · ${order.package_courier_name}` : ""}
                    </p>
                  ) : null}
                  {order.pending_cancellation && !order.tracking_number && mode === "admin" && (
                    <div className="mt-2 rounded-xl border border-amber-200 bg-amber-50/80 p-3 text-xs text-amber-900">
                      <p className="font-semibold">Cancellation requested — shipment on hold</p>
                      <p className="mt-1">
                        A customer cancellation is pending review. Reject it on{" "}
                        <Link to="/admin/cancellations" className="underline">
                          Cancellations
                        </Link>{" "}
                        or book with an audited override if fulfilment must continue.
                      </p>
                    </div>
                  )}
                  {canFulfill ? (
                  <div className="mt-2 flex flex-wrap gap-2">
                    {order.can_complete_package && (
                      <button
                        type="button"
                        disabled={busy !== null}
                        onClick={() => openPackageDialog(order)}
                        className="inline-flex items-center gap-1.5 rounded-full border border-border bg-background px-3 py-1.5 text-xs font-medium disabled:opacity-60"
                      >
                        <Package className="h-3.5 w-3.5" />
                        {order.packed_at ? "Update package" : "Create package"}
                      </button>
                    )}
                    {(order.can_book_expressbees_shipment || order.can_book_shipment) && (
                      <button
                        type="button"
                        disabled={busy !== null}
                        onClick={() => {
                          setBookMode(order.can_book_expressbees_shipment ? "xpressbees" : "manual");
                          setBookRequiresOverride(false);
                          setBookOverrideReason("");
                          setBookTarget(order.order_number);
                        }}
                        className="inline-flex items-center gap-1.5 rounded-full border border-border bg-background px-3 py-1.5 text-xs font-medium disabled:opacity-60"
                      >
                        <Truck className="h-3.5 w-3.5" /> Book shipment
                      </button>
                    )}
                    {(order.can_book_expressbees_shipment_with_override || order.can_book_shipment_with_override) && (
                      <button
                        type="button"
                        disabled={busy !== null}
                        onClick={() => {
                          setBookMode("xpressbees");
                          setBookRequiresOverride(true);
                          setBookOverrideReason("");
                          setBookTarget(order.order_number);
                        }}
                        className="inline-flex items-center gap-1.5 rounded-full border border-amber-300 bg-amber-50 px-3 py-1.5 text-xs font-medium text-amber-900 disabled:opacity-60"
                      >
                        <Truck className="h-3.5 w-3.5" /> Override hold & book
                      </button>
                    )}
                    {order.shipment_label_url ? (
                      <a
                        href={order.shipment_label_url}
                        target="_blank"
                        rel="noreferrer"
                        className="inline-flex items-center gap-1.5 rounded-full border border-border bg-background px-3 py-1.5 text-xs font-medium"
                      >
                        <Printer className="h-3.5 w-3.5" /> Download label
                      </a>
                    ) : null}
                    {order.tracking_number && order.status !== "fulfilled" ? (
                      <button
                        type="button"
                        disabled={busy !== null}
                        onClick={() => {
                          setCancelShipmentTarget(order);
                          setCancelShipmentMode(
                            order.shipment_logistics_mode === "xpressbees" ? "xpressbees" : "manual",
                          );
                          setCancelShipmentReason("");
                        }}
                        className="inline-flex items-center gap-1.5 rounded-full border border-destructive/40 px-3 py-1.5 text-xs font-medium text-destructive"
                      >
                        <X className="h-3.5 w-3.5" /> Cancel shipment
                      </button>
                    ) : null}
                  </div>
                  ) : null}
                </div>

                {canSyncZoho ? (
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
                ) : null}
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
      <Dialog
        open={!!bookTarget}
        onOpenChange={(open) => {
          if (!open) {
            setBookTarget(null);
            setBookCarrier("");
            setBookAwb("");
            setBookTrackingUrl("");
            setBookLabelUrl("");
            setBookRequiresOverride(false);
            setBookOverrideReason("");
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {bookRequiresOverride ? "Override hold & book" : "Book shipment"}
              {bookTarget ? ` · ${bookTarget}` : ""}
            </DialogTitle>
          </DialogHeader>
          {bookRequiresOverride && (
            <div className="rounded-xl border border-amber-200 bg-amber-50/80 p-3 text-xs text-amber-900">
              This order has a pending cancellation request. Provide a reason — it is logged server-side.
            </div>
          )}
          <div className="space-y-3">
            <label className="block text-sm font-medium">
              Mode
              <select
                className="mt-1 h-10 w-full rounded-xl border border-border bg-background px-3 text-sm"
                value={bookMode}
                onChange={(event) => setBookMode(event.target.value as "xpressbees" | "manual")}
              >
                <option value="xpressbees">ExpressBees</option>
                <option value="manual">Manual carrier</option>
              </select>
            </label>
            {bookMode !== "xpressbees" ? (
              <>
                <label className="block text-sm font-medium">
                  Carrier name
                  <input
                    className="mt-1 h-10 w-full rounded-xl border border-border bg-background px-3 text-sm"
                    value={bookCarrier}
                    onChange={(event) => setBookCarrier(event.target.value)}
                    placeholder="e.g. Delhivery"
                  />
                </label>
                <label className="block text-sm font-medium">
                  AWB / reference *
                  <input
                    className="mt-1 h-10 w-full rounded-xl border border-border bg-background px-3 text-sm"
                    value={bookAwb}
                    onChange={(event) => setBookAwb(event.target.value)}
                  />
                </label>
                <label className="block text-sm font-medium">
                  Tracking URL
                  <input
                    className="mt-1 h-10 w-full rounded-xl border border-border bg-background px-3 text-sm"
                    value={bookTrackingUrl}
                    onChange={(event) => setBookTrackingUrl(event.target.value)}
                  />
                </label>
                <label className="block text-sm font-medium">
                  Label URL
                  <input
                    className="mt-1 h-10 w-full rounded-xl border border-border bg-background px-3 text-sm"
                    value={bookLabelUrl}
                    onChange={(event) => setBookLabelUrl(event.target.value)}
                    placeholder="Optional printable label link"
                  />
                </label>
              </>
            ) : (
              <label className="block text-sm font-medium">
                Pre-assigned AWB (optional)
                <input
                  className="mt-1 h-10 w-full rounded-xl border border-border bg-background px-3 text-sm"
                  value={bookAwb}
                  onChange={(event) => setBookAwb(event.target.value)}
                  placeholder="Leave blank if ExpressBees generates the AWB"
                />
              </label>
            )}
            {bookRequiresOverride && (
              <label className="block text-sm font-medium">
                Override reason *
                <textarea
                  className="mt-1 min-h-20 w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
                  value={bookOverrideReason}
                  onChange={(event) => setBookOverrideReason(event.target.value)}
                  placeholder="Why is this order shipping while cancellation is pending?"
                />
              </label>
            )}
          </div>
          <DialogFooter>
            <button
              type="button"
              onClick={() => void submitBookShipment()}
              disabled={
                busy === `ship:${bookTarget}` ||
                (bookMode !== "xpressbees" && bookAwb.trim().length < 3) ||
                (bookRequiresOverride && bookOverrideReason.trim().length < 5)
              }
              className="rounded-full bg-primary px-4 py-2 text-sm text-primary-foreground disabled:opacity-50"
            >
              {busy === `ship:${bookTarget}` ? "Booking…" : bookRequiresOverride ? "Override & book" : "Book shipment"}
            </button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog
        open={!!packageTarget}
        onOpenChange={(open) => {
          if (!open) {
            setPackageTarget(null);
            setPackageRates(null);
            setSelectedCourierId("");
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {packageTarget?.packed_at ? "Update package" : "Create package"}
              {packageTarget ? ` · ${packageTarget.order_number}` : ""}
            </DialogTitle>
          </DialogHeader>
          <p className="text-sm text-muted-foreground">
            Default ExpressBees weight is prefilled. Enter the box size in cm, inches or metres, calculate rates, then
            complete wrap. ExpressBees still receives grams and centimetres.
          </p>
          <div className="grid grid-cols-2 gap-3">
            <label className="col-span-2 block text-sm font-medium">
              Weight
              <span className="mt-1 flex gap-2">
                <input
                  type="number"
                  min={0}
                  step="any"
                  className="h-10 min-w-0 flex-1 rounded-xl border border-border bg-background px-3 text-sm"
                  value={packageWeight}
                  onChange={(event) => {
                    setPackageWeight(event.target.value);
                    setPackageRates(null);
                  }}
                />
                <select
                  aria-label="Weight unit"
                  className="h-10 w-[5.5rem] shrink-0 rounded-xl border border-border bg-background px-2 text-sm"
                  value={packageWeightUnit}
                  onChange={(event) => {
                    const next = event.target.value as WeightUnit;
                    setPackageWeight(convertWeightInput(packageWeight, packageWeightUnit, next));
                    setPackageWeightUnit(next);
                    setPackageRates(null);
                  }}
                >
                  {WEIGHT_UNITS.map((unit) => (
                    <option key={unit.value} value={unit.value}>
                      {unit.label}
                    </option>
                  ))}
                </select>
              </span>
            </label>
            <div className="col-span-2 flex items-center justify-between gap-3">
              <span className="text-sm font-medium">Box size</span>
              <label className="flex items-center gap-2 text-sm font-medium">
                Units
                <select
                  aria-label="Box size unit"
                  className="h-10 w-[5.5rem] rounded-xl border border-border bg-background px-2 text-sm"
                  value={packageLengthUnit}
                  onChange={(event) => {
                    const next = event.target.value as LengthUnit;
                    setPackageLength(convertLengthInput(packageLength, packageLengthUnit, next));
                    setPackageBreadth(convertLengthInput(packageBreadth, packageLengthUnit, next));
                    setPackageHeight(convertLengthInput(packageHeight, packageLengthUnit, next));
                    setPackageLengthUnit(next);
                    setPackageRates(null);
                  }}
                >
                  {LENGTH_UNITS.map((unit) => (
                    <option key={unit.value} value={unit.value}>
                      {unit.label}
                    </option>
                  ))}
                </select>
              </label>
            </div>
            <label className="block text-sm font-medium">
              Length ({packageLengthUnit})
              <input
                type="number"
                min={0}
                step="any"
                className="mt-1 h-10 w-full rounded-xl border border-border bg-background px-3 text-sm"
                value={packageLength}
                onChange={(event) => {
                  setPackageLength(event.target.value);
                  setPackageRates(null);
                }}
              />
            </label>
            <label className="block text-sm font-medium">
              Breadth ({packageLengthUnit})
              <input
                type="number"
                min={0}
                step="any"
                className="mt-1 h-10 w-full rounded-xl border border-border bg-background px-3 text-sm"
                value={packageBreadth}
                onChange={(event) => {
                  setPackageBreadth(event.target.value);
                  setPackageRates(null);
                }}
              />
            </label>
            <label className="block text-sm font-medium">
              Height ({packageLengthUnit})
              <input
                type="number"
                min={0}
                step="any"
                className="mt-1 h-10 w-full rounded-xl border border-border bg-background px-3 text-sm"
                value={packageHeight}
                onChange={(event) => {
                  setPackageHeight(event.target.value);
                  setPackageRates(null);
                }}
              />
            </label>
          </div>
          {packageExpressBeesPreview ? (
              <p className="text-xs text-muted-foreground">Sends to ExpressBees as {packageExpressBeesPreview}</p>
            ) : null}
          {packageRates ? (
            <div className="space-y-2 rounded-xl border border-border/70 bg-muted/40 p-3 text-sm">
              <p>
                Chargeable {packageRates.chargeable_weight_grams} g
                {packageRates.actual_weight_grams !== packageRates.chargeable_weight_grams
                  ? ` (actual ${packageRates.actual_weight_grams} g, volumetric ${packageRates.volumetric_weight_grams} g)`
                  : ""}
              </p>
              {packageRates.couriers?.length ? (
                <div className="space-y-1">
                  {packageRates.couriers.map((option) => (
                    <label
                      key={option.id}
                      className="flex cursor-pointer items-start gap-2 rounded-lg border border-transparent px-2 py-1.5 has-[:checked]:border-border has-[:checked]:bg-background"
                    >
                      <input
                        type="radio"
                        name="package-courier"
                        className="mt-1"
                        checked={selectedCourierId === option.id}
                        onChange={() => setSelectedCourierId(option.id)}
                      />
                      <span>
                        <span className="font-medium">{option.name}</span>
                        {option.preferred ? " · preferred" : ""}
                        {option.eta ? ` · ${option.eta}` : ""}
                        {option.rate != null && option.rate !== "" ? ` · ₹${option.rate}` : ""}
                      </span>
                    </label>
                  ))}
                </div>
              ) : (
                <p className="text-muted-foreground">
                  Preferred slab: {packageRates.preferred_courier_name}. Live courier options were not returned.
                </p>
              )}
            </div>
          ) : null}
          <DialogFooter>
            <button
              type="button"
              onClick={() => void submitPackageRates()}
              disabled={busy === `package-rates:${packageTarget?.order_number}`}
              className="rounded-full border border-border bg-background px-4 py-2 text-sm disabled:opacity-50"
            >
              {busy === `package-rates:${packageTarget?.order_number}` ? "Calculating…" : "Calculate rates"}
            </button>
            <button
              type="button"
              onClick={() => void submitCompletePackage()}
              disabled={busy === `package:${packageTarget?.order_number}` || !packageRates}
              className="rounded-full bg-primary px-4 py-2 text-sm text-primary-foreground disabled:opacity-50"
            >
              {busy === `package:${packageTarget?.order_number}` ? "Saving…" : "Complete wrap"}
            </button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={!!cancelShipmentTarget} onOpenChange={(open) => !open && setCancelShipmentTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Cancel shipment · {cancelShipmentTarget?.order_number}</DialogTitle>
          </DialogHeader>
          <p className="text-sm text-muted-foreground">
            Use ExpressBees to cancel the AWB, or choose manual after confirming cancellation directly with the carrier.
          </p>
          <label className="block text-sm font-medium">
            Mode
            <select
              value={cancelShipmentMode}
              onChange={(event) => setCancelShipmentMode(event.target.value as "xpressbees" | "manual")}
              className="mt-1 h-10 w-full rounded-xl border border-border bg-background px-3"
            >
              <option value="xpressbees">ExpressBees API</option>
              <option value="manual">Manual carrier confirmation</option>
            </select>
          </label>
          <label className="block text-sm font-medium">
            Reason
            <textarea
              value={cancelShipmentReason}
              onChange={(event) => setCancelShipmentReason(event.target.value)}
              className="mt-1 min-h-20 w-full rounded-xl border border-border bg-background px-3 py-2"
            />
          </label>
          <DialogFooter>
            <button
              type="button"
              onClick={() => void submitCancelShipment()}
              disabled={busy !== null || cancelShipmentReason.trim().length < 5}
              className="rounded-full bg-destructive px-4 py-2 text-sm text-destructive-foreground disabled:opacity-50"
            >
              Cancel shipment
            </button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </AdminPage>
  );
}
